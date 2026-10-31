
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;

public final class BundleReader implements Runnable {
	private long statStartNs = System.nanoTime();
	private long statDeq = 0;
	private long statCommit = 0;
	private long statDeqNs = 0;
	private long statCommitNs = 0;
	private long statStoreNs = 0;

	private long statStageNs = 0;
	private long statReadyNs = 0;
	private long statStoreTotalNs = 0;

	private long lastStoreReportNs = System.nanoTime();

	private static final ObjectMapper JSON = new ObjectMapper();

	private void printStats() {
		long now = System.nanoTime();

		if (now - statStartNs < 1_000_000_000L) {
			return;
		}

		double sec = (now - statStartNs) / 1_000_000_000.0;

		System.out.printf(
			"[Reader TPS] DEQ=%.1f COMMIT=%.1f "
		  + "DEQ_ms=%.3f STAGE_ms=%.3f READY_ms=%.3f "
		  + "STORE_TOTAL_ms=%.3f COMMIT_ms=%.3f%n",
			statDeq / sec,
			statCommit / sec,
			statDeqNs / 1_000_000.0 / Math.max(statDeq, 1),
			statStageNs / 1_000_000.0 / Math.max(statDeq, 1),
			statReadyNs / 1_000_000.0 / Math.max(statDeq, 1),
			statStoreTotalNs / 1_000_000.0 / Math.max(statDeq, 1),
			statCommitNs / 1_000_000.0 / Math.max(statCommit, 1)
		);

		// 10초 단위 DurableStore 통계 출력
        long nowNs = System.nanoTime();

        if (nowNs - lastStoreReportNs >= 10_000_000_000L) {
            store.printAppendStats();
            lastStoreReportNs = nowNs;
        }

		statStartNs = now;
		statDeq = 0;
		statCommit = 0;
		statDeqNs = 0;
		statCommitNs = 0;
		statStoreNs = 0;

		statStageNs = 0;
		statReadyNs = 0;
		statStoreTotalNs = 0;
	}

    private final FQueue fq;
    private final int inputQueueId;
    private final DurableStore store;
    private final BlockingQueue<List<DurableStore.StoredMessage>>
        bundleQueue;

    private final AtomicBoolean running;
    private final int bundleSize;
    private final long flushIntervalMs;

    public BundleReader(
        FQueue fq,
        int inputQueueId,
        DurableStore store,
        BlockingQueue<List<DurableStore.StoredMessage>> bundleQueue,
        AtomicBoolean running,
        int bundleSize,
        long flushIntervalMs
    ) {
        this.fq = fq;
        this.inputQueueId = inputQueueId;
        this.store = store;
        this.bundleQueue = bundleQueue;
        this.running = running;
        this.bundleSize = bundleSize;
        this.flushIntervalMs = flushIntervalMs;
    }

	private static boolean isHealthCheck(
			FQueue.FQueueMessage message) {

		if (message == null || message.data == null) {
			return false;
		}

		try {
			String json = new String(
				message.data,
				StandardCharsets.UTF_8);

			JsonNode root = JSON.readTree(json);
			JsonNode kind = root.get("MSG_KIND");

			return kind != null
				&& kind.isTextual()
				&& "HEALTH_CHECK".equals(kind.asText());

		} catch (Exception e) {
			return false;
		}
	}

    @Override
    public void run() {
        List<DurableStore.StoredMessage> bundle =
            new ArrayList<>(bundleSize);

        long bundleStart = System.nanoTime();

        while (running.get()) {
            try {
				long t0 = System.nanoTime();

				FQueue.FQueueMessage message =
					fq.deQueueXA(inputQueueId);

				if (isHealthCheck(message)) {
					int rc = fq.commit(message);

					if (rc != 0) {
						System.err.println(
							"HEALTH_CHECK commit failed: seq="
							+ message.seq + ", rc=" + rc
						);

						running.set(false);
						break;
					}

					// 결과 큐 enQueue 및 Worker 전달 없이 다음 메시지 처리
					continue;
				}

				long t1 = System.nanoTime();

				if (message != null) {
					statDeq++;
					statDeqNs += t1 - t0;
				}

                if (message == null) {
                    if (!bundle.isEmpty()
                        && elapsedMs(bundleStart)
                            >= flushIntervalMs) {
                        publishBundle(bundle);
                        bundle = new ArrayList<>(bundleSize);
                        bundleStart = System.nanoTime();
                    }

                    Thread.sleep(20);
                    continue;
                }

                String id = DurableStore.messageId(message);
                boolean wasKnown = store.contains(id);

                DurableStore.StoredMessage stored;
				long storeTotalStartNs = System.nanoTime();

                try {

                    /*
                     * Persist intent before attempting Commit.
                     * A crash after this point requires recovery.
                     */

					long stageStartNs = System.nanoTime();

					stored = store.stage(message);

					long stageElapsedNs = System.nanoTime() - stageStartNs;

					statStageNs += stageElapsedNs;
					statStoreNs += stageElapsedNs;

                } catch (IOException e) {
                    /*
                     * Persistence failed. The message must not
                     * be committed. Cancel is best effort.
                     */
                    int cancelRc = fq.cancel(message);

                    System.err.println(
                        "Journal write failed; cancelRc="
                        + cancelRc + ", error=" + e);

                    running.set(false);
                    break;
                }

				long commitStartNs = System.nanoTime();

				int commitRc = fq.commit(message);

				statCommitNs += System.nanoTime() - commitStartNs;


				if (commitRc == 0) {
					statCommit++;
					printStats();
				}

                if (commitRc != 0) {
                    /*
                     * Commit outcome may be ambiguous.
                     * Keep the durable record and stop.
                     * Do not blindly cancel or process it.
                     */
                    System.err.println(
                        "XA commit failed/uncertain: seq="
                        + message.seq + ", rc=" + commitRc);

                    running.set(false);
                    break;
                }

                /*
                 * If this is a redelivery of an existing message,
                 * commit it but do not dispatch duplicate work.
                 */
                if (wasKnown) {
                    continue;
                }

				long readyStartNs = System.nanoTime();

				store.setState(
					id, DurableStore.State.READY);

				statReadyNs += System.nanoTime() - readyStartNs;

				statStoreTotalNs += System.nanoTime() - storeTotalStartNs;


                bundle.add(stored);

                if (bundle.size() >= bundleSize) {
                    publishBundle(bundle);
                    bundle = new ArrayList<>(bundleSize);
                    bundleStart = System.nanoTime();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (Exception e) {
                System.err.println(
                    "BundleReader error: " + e);
                running.set(false);
            }
        }

        if (!bundle.isEmpty()) {
            try {
                publishBundle(bundle);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void publishBundle(
        List<DurableStore.StoredMessage> bundle
    ) throws InterruptedException {
        if (bundle.isEmpty()) {
            return;
        }

        bundleQueue.put(List.copyOf(bundle));
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(
            System.nanoTime() - startNanos);
    }
}

