//
// BundleAgent.java
//
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import java.io.FileInputStream;
import java.util.Properties;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BundleAgent {
	// inner class
    private static class Config {
        String logFile;
        int logLevel;

        String queuePath;
        String inputQueue;
        String outputQueue;

        int dequeueIdleMs;
        int retryDelayMs;
        int shutdownTimeoutSeconds;
		int bundleSize;
		int bundleFlushMs;
		int bundleQueueCapacity;
		int workerCount;
		int workerQueueCapacity;

        static Config load(String file) throws IOException {
            Properties p = new Properties();

            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            }

            Config c = new Config();

            c.logFile = required(p, "log.file");
            c.logLevel = integer(p, "log.level");

            c.queuePath = required(p, "queue.path");
            c.inputQueue = required(p, "queue.input");
            c.outputQueue = required(p, "queue.output");

            c.dequeueIdleMs = integer(p, "dequeue.idle.ms");
            c.retryDelayMs = integer(p, "retry.delay.ms");
            c.shutdownTimeoutSeconds =
                integer(p, "worker.shutdown.timeout.seconds");
			c.bundleSize = integer(p, "bundle.size");
			c.bundleFlushMs = integer(p, "bundle.flush.ms");
			c.bundleQueueCapacity = integer(p, "bundle.queue.capacity");
			c.workerCount = integer(p, "worker.count");
			c.workerQueueCapacity = integer(p, "worker.queue.capacity");

            // Configuration validation
            if (c.logLevel < 0) {
                throw new IllegalArgumentException(
                    "log.level must be >= 0");
            }

            if (c.dequeueIdleMs < 0) {
                throw new IllegalArgumentException(
                    "dequeue.idle.ms must be >= 0");
            }

            if (c.retryDelayMs < 0) {
                throw new IllegalArgumentException(
                    "retry.delay.ms must be >= 0");
            }

            if (c.shutdownTimeoutSeconds <= 0) {
                throw new IllegalArgumentException(
                    "worker.shutdown.timeout.seconds must be > 0");
            }

            return c;
        }

        private static String required(
                Properties p, String key) {

            String value = p.getProperty(key);

            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "Missing configuration: " + key);
            }

            return value.trim();
        }

        private static int integer(
                Properties p, String key) {

            String value = required(p, key);

            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Invalid integer configuration: "
                    + key + "=" + value, e);
            }
        }
    }

    private static Config config;


    private static final String LOG_FILE =
        "/logs/fq/fqueue_bundle_agent.log";

    private static final String QUEUE_PATH =
        "/sendq1/enmq";

    private static final String INPUT_QUEUE =
        "KT_SM_ONL";

    private static final String OUTPUT_QUEUE =
        "XMS_HIST_RSLT";

    private static final String JOURNAL_FILE =
        "/ums24/wiseu/fq/agent-data/agent.journal";

    private static final int LOG_LEVEL = 5;

    private static final int BUNDLE_SIZE = 400; 
    private static final long BUNDLE_FLUSH_MS = 20; // Bundle이 100개 미만이어도 최대한 20ms 간격으로 묶음을 전달

    private static final int WORKER_COUNT = 32; // 실제 업무 처리를 병렬로 수행할 Worker 스레드 수
    private static final int BUNDLE_QUEUE_CAPACITY = 400; // Worker에게 전달하기 전 Bundle을 대기시킬 수 있는 최대 개수
    private static final int WORKER_QUEUE_CAPACITY = 4000; // Worker 스레드가 모두 바쁠 때 실행 대기 작업을 저장할 최대 개수

    private static final int MAX_RETRIES = 3;

    private static final ObjectMapper JSON =
        new ObjectMapper();

    private static final AtomicBoolean running =
        new AtomicBoolean(true);

    private static FQueue fq;

    private static int inputQueueId = -1;
    private static int outputQueueId = -1;

    private static DurableStore store;

    private static BlockingQueue<
        List<DurableStore.StoredMessage>> bundleQueue;

    private static ExecutorService workers;

    private static Thread readerThread;
    private static Thread dispatcherThread;
    private static Thread publisherThread;

    public static void main(String[] args) throws Exception {
        String configFile =
            args.length > 0 ? args[0] : "Agent.properties";

        // Load and validate configuration before opening resources.
        config = Config.load(configFile);

        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> {
                running.set(false);

                if (readerThread != null) {
                    readerThread.interrupt();
                }

                if (dispatcherThread != null) {
                    dispatcherThread.interrupt();
                }

                if (publisherThread != null) {
                    publisherThread.interrupt();
                }
            }, "BundleAgent-Shutdown"));

        try {
            initialize();
            startComponents();
            waitForShutdown();
        } finally {
            stopComponents();
            closeResources();
        }
    }

    private static void initialize() throws Exception {

        fq = new FQueue();

        int rc = fq.openLogger(config.logFile, config.logLevel);

        if (rc <= 0) {
            throw new IllegalStateException(
                "Cannot open FileQueue logger: " + rc);
        }

        inputQueueId =
            fq.openQueue(config.queuePath, config.inputQueue);

        if (inputQueueId < 0) {
            throw new IllegalStateException(
                "Cannot open input queue: " + config.inputQueue);
        }

        outputQueueId =
            fq.openQueue(config.queuePath, config.outputQueue);

        if (outputQueueId < 0) {
            throw new IllegalStateException(
                "Cannot open output queue: " + config.outputQueue);
        }

        Path journalPath = Paths.get(JOURNAL_FILE);

        store = new DurableStore(journalPath);

        bundleQueue = new ArrayBlockingQueue<>(
            config.bundleQueueCapacity);

        /*
         * CallerRunsPolicy provides backpressure:
         * when the worker queue is full, the dispatcher
         * executes the task itself rather than dropping it.
         */
        workers = new ThreadPoolExecutor(
            config.workerCount,
            config.workerCount,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(
                config.workerQueueCapacity),
            new ThreadPoolExecutor.CallerRunsPolicy());

        System.out.println(
            "BundleAgent initialized. bundleSize="
            + config.bundleSize
            + ", workers=" + config.workerCount);
    }

    private static void startComponents() throws Exception {
		/*
		 * 해결되지 않은 XA commit 상태는 로그를 남기고
		 * 저널에서 제거한다.
		 */
        /*
         * A COMMITTING record means that the process may have
         * stopped while the source XA commit was in progress.
         *
         * Do not silently treat it as READY.
         * Resolve this state using the actual FileQueue
         * transaction/recovery semantics before proceeding.
         */
		for (DurableStore.StoredMessage m :
				store.recoverableMessages()) {

			if (m.state == DurableStore.State.COMMITTING) {

				System.err.println(
					"[ERROR] Unresolved XA commit discarded"
					+ ", id=" + m.id
					+ ", seq=" + m.seq
					+ ", queueId=" + m.queueId);

				store.discard(m.id);
			}
		}

        /*
         * Requeue previously committed messages that were
         * not fully processed before a previous shutdown.
         */
        List<DurableStore.StoredMessage> recovered =
            store.recoverableMessages();

        if (!recovered.isEmpty()) {
            List<DurableStore.StoredMessage> bundle =
                new ArrayList<>(config.bundleSize);

            for (DurableStore.StoredMessage m : recovered) {
                bundle.add(m);

                if (bundle.size() >= config.bundleSize) {
                    bundleQueue.put(List.copyOf(bundle));
                    bundle = new ArrayList<>(config.bundleSize);
                }
            }

            if (!bundle.isEmpty()) {
                bundleQueue.put(List.copyOf(bundle));
            }

            System.out.println(
                "Recovered messages=" + recovered.size());
        }

        /*
         * Start the result publisher before the reader.
         * It can resume messages already marked RESULT_PENDING.
         */
        publisherThread = new Thread(
            BundleAgent::publisherLoop,
            "Bundle-ResultPublisher");

        publisherThread.start();

        dispatcherThread = new Thread(
            BundleAgent::dispatcherLoop,
            "Bundle-Dispatcher");

        dispatcherThread.start();

        BundleReader reader = new BundleReader(
            fq,
            inputQueueId,
            store,
            bundleQueue,
            running,
            config.bundleSize,
            config.bundleFlushMs);

        readerThread = new Thread(
            reader,
            "Bundle-Reader");

        readerThread.start();

        System.out.println("BundleAgent started.");
    }

    /*
     * Dispatcher:
     * consume durable, committed bundles and submit each
     * message independently to the business worker pool.
     */
    private static void dispatcherLoop() {

        while (running.get() || !bundleQueue.isEmpty()) {
            try {
                List<DurableStore.StoredMessage> bundle =
                    bundleQueue.poll(200, TimeUnit.MILLISECONDS);

                if (bundle == null) {
                    continue;
                }

                for (DurableStore.StoredMessage message : bundle) {
                    workers.execute(
                        () -> processMessage(message));
                }

				/*
                System.out.println(
                    "Dispatched bundle size=" + bundle.size());
				*/

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RejectedExecutionException e) {
                /*
                 * Do not silently discard the bundle.
                 * An unexpected rejection requires controlled
                 * recovery; the journal remains the source
                 * of truth for unfinished messages.
                 */
                System.err.println(
                    "Worker submission rejected: " + e);
                running.set(false);
                break;
            }
        }
    }

    /*
     * Business processing:
     * read durable input -> audit/business -> persist result.
     *
     * The input queue has already been committed.
     * Never call input commit/cancel from this method.
     */
    private static void processMessage(
        DurableStore.StoredMessage message) {

        String id = message.id;

        try {
            DurableStore.StoredMessage current =
                store.get(id);

            if (current == null
                || current.state == DurableStore.State.DONE
                || current.state == DurableStore.State.RESULT_PENDING) {
                return;
            }

            for (int attempt = 0;
                 attempt <= MAX_RETRIES;
                 attempt++) {

                try {
                    store.setState(
                        id, DurableStore.State.PROCESSING);

                    String input = new String(
                        message.data,
                        StandardCharsets.UTF_8);

                    String output = auditAndDoWork(input);

                    byte[] outputBytes =
                        output.getBytes(StandardCharsets.UTF_8);

                    /*
                     * Persist the result before attempting output
                     * enqueue. A restart can then retry publishing
                     * without rerunning the business operation.
                     */
                    store.setResultPending(id, outputBytes);

					/*
                    System.out.println(
                        "Business complete seq=" + message.seq
                        + ", resultBytes=" + outputBytes.length);
					*/

                    return;

                } catch (Exception e) {
                    System.err.println(
                        "Business attempt failed. id=" + id
                        + ", attempt=" + (attempt + 1)
                        + ", error=" + e);

                    if (attempt == MAX_RETRIES) {
                        /*
                         * Preserve a terminal error as a result,
                         * rather than leaving the message invisible.
                         */
                        String error = makeErrorResult(
                            new String(
                                message.data,
                                StandardCharsets.UTF_8),
                            "PROCESS_ERROR",
                            e.getMessage());

                        store.setResultPending(
                            id,
                            error.getBytes(StandardCharsets.UTF_8));

                        return;
                    }

                    store.setRetry(id);

                    long delayMs =
                        Math.min(5000L, 250L << attempt);

                    Thread.sleep(delayMs);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(
                "Worker interrupted; message remains recoverable: "
                + id);

        } catch (Exception e) {
            System.err.println(
                "Durable processing error. id=" + id
                + ", error=" + e);

            /*
             * Do not mark DONE when durable state changes fail.
             * Recovery must inspect the journal on restart.
             */
            running.set(false);
        }
    }

    /*
     * Result Publisher:
     * one thread owns output enqueue and result-state changes.
     */
    private static void publisherLoop() {

        while (running.get()) {
            try {
                List<DurableStore.StoredMessage> pending =
                    store.pendingResults();

                if (pending.isEmpty()) {
                    Thread.sleep(100);
                    continue;
                }

                for (DurableStore.StoredMessage message : pending) {
                    if (!running.get()) {
                        break;
                    }

                    publishOne(message);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println(
                    "Publisher error: " + e);

                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private static void publishOne(
        DurableStore.StoredMessage message) throws Exception {

        if (message.result == null) {
            throw new IllegalStateException(
                "Missing result payload: " + message.id);
        }

        // Retry only the output enqueue; do not reprocess the input.
        // The result remains RESULT_PENDING until enqueue succeeds.
        FQueue.EnqueueResult result;
        while (running.get()) {
            result = fq.enQueue(
                outputQueueId,
                message.result,
                FQueue.EN_NORMAL_MODE);

            if (result != null && result.rc > 0) {
                break;
            }

            System.err.println(
                "Result enqueue failed; retry after 1 second. id="
                + message.id + ", rc="
                + (result == null ? "null" : result.rc));

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // Keep RESULT_PENDING for restart recovery.
            }
        }

        if (!running.get()) {
            return; // Do not mark DONE on shutdown.
        }

        /*
         * A crash after enqueue but before DONE may cause
         * duplicate output on restart. This is at-least-once
         * delivery unless the destination supports deduplication.
         */
        store.setState(
            message.id, DurableStore.State.DONE);

		/*
        System.out.println(
            "Result enqueued. id=" + message.id
            + ", seq=" + message.seq
            + ", resultSeq=" + result.seq);
		*/
    }


    /*
     * Reuse the existing Agent audit/business example.
     */
/*
    private static String auditAndDoWork(String input)
        throws Exception {

        JsonNode root = JSON.readTree(input);

        if (root == null || !root.isObject()) {
            return makeErrorResult(
                input, "INVALID_JSON",
                "Root must be an object");
        }

        String[] required = {
            "CHANNEL", "SEQ", "RECEIVER", "SENDER"
        };

        for (String field : required) {
            JsonNode value = root.get(field);

            if (value == null
                || !value.isTextual()
                || value.asText().trim().isEmpty()) {

                return makeErrorResult(
                    input,
                    "AUDIT_ERROR",
                    "Missing, empty or non-string field: " + field);
            }
        }

        String businessResult = doBusinessWork(root);

        ObjectNode result = JSON.createObjectNode();

        result.put("CHANNEL", root.get("CHANNEL").asText());
        result.put("SEQ", root.get("SEQ").asText());
        result.put("RESULT_CODE", "0000");
        result.put("RESULT_MESSAGE", "SUCCESS");
        result.put("BUSINESS_RESULT", businessResult);
        result.put("PROCESSED_AT", Instant.now().toString());

        return JSON.writeValueAsString(result);
    }
*/
    // --------------------------------------------------
    // JSON audit and business processing
    // --------------------------------------------------

    /*
     * Example audit:
     * Required fields must exist, be strings, and not be blank.
     */
    private static String auditAndDoWork(String input)
            throws Exception {

        JsonNode root = JSON.readTree(input);

        if (root == null || !root.isObject()) {
            return makeErrorResult(
                input,
                "INVALID_JSON",
                "Root must be an object");
        }

        String[] required = {
            "TITLE",
            "HISTOTY_KEY", // 20 bytes
            "MSG_KIND", // SMS:S, LMS:L, RCS:R, AlimTalk:K, FriendTalk: A, FAX:F, E-mail:E, PUSH:P
            "MSG_TYPE", // 국내:1, 국재:2
			"REQUEST_CO_ID", // 삼성증권지정코드 - KISA최초발신사업자 식별코드
			"REQUEST_DTTM", // EAI 부터 최초 발송요청 수신시간(게이트웨이수신시간)
			"DIST_Q_DTTM", // 분배시간: 분배기가 중계사큐에 메시지를 넣은 시간
            "RCV_PHN_NO", // 착신가입자번호
            "SND_PHN_NO", // 발신번호
            "CALLBACK_NO", // 회신번호
            "SMS_MESSAGE" // 118 bytes 단문메시지
        };

        for (String field : required) {

            JsonNode value = root.get(field);

            if (value == null
                    || !value.isTextual()
                    || value.asText().trim().isEmpty()) {

                return makeErrorResult(
                    input,
                    "AUDIT_ERROR",
                    "Missing, empty or non-string field: "
                    + field);
            }
        }

        /*
         * TODO:
         * 1. Validate exact/max lengths and enumerations.
         * 2. Validate SEND_TIME format.
         * 3. Call the real business operation.
         */
        String businessResult = doBusinessWork(root);

        ObjectNode result = JSON.createObjectNode();

        result.put("RESP_KIND", "RESULT");
        result.put("RESULT_CODE", "0000");
        result.put("RESULT_GROUP_CODE", "1");
        result.put("REASON", "");

		// 분배기가 내려준값을 돌려준다.
        result.put(
            "MSG_KIND",
            root.get("MSG_KIND").asText());

        result.put( "END_TIME",
            root.get("DIST_Q_DTTM").asText());

        result.put(
            "HISTORY_KEY",
            root.get("HISTORY_KEY").asText());

		String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        result.put( "DONE_DATE", currentTime);

        result.put("AGENT_CODE", "K"); // KT:K, SK:S, LG:L, infobank:I
        result.put("TELECOM_CODE", "KT");

        result.put(
            "SESSION_ID",
            Instant.now().toString());
        result.put(
            "SESSION_NO",
            Instant.now().toString());

        // result.put("BUSINESS_RESULT", businessResult);
        result.put("EXTRA", "");
		
        return JSON.writeValueAsString(result);
    }

    private static String doBusinessWork(JsonNode input) {
        /*
         * Replace this sample with the real business logic.
         */
        return "BUSINESS_OK";
    }

    private static String makeErrorResult(
        String input, String code, String reason) {

        ObjectNode result = JSON.createObjectNode();

        result.put("RESULT_CODE", code);
        result.put(
            "RESULT_MESSAGE",
            reason == null ? "Unknown error" : reason);
        result.put("INPUT", input);
        result.put("PROCESSED_AT", Instant.now().toString());

        try {
            return JSON.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"RESULT_CODE\":\"RESULT_BUILD_ERROR\"}";
        }
    }

    private static void waitForShutdown()
        throws InterruptedException {

        while (running.get()) {
            Thread.sleep(500);
        }
    }

    private static void stopComponents() {

        running.set(false);

        if (readerThread != null) {
            readerThread.interrupt();
        }

        joinThread(readerThread, 5000);
        joinThread(dispatcherThread, 5000);

        if (workers != null) {
            workers.shutdown();

            try {
                if (!workers.awaitTermination(
                        30, TimeUnit.SECONDS)) {
                    workers.shutdownNow();
                }
            } catch (InterruptedException e) {
                workers.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (publisherThread != null) {
            publisherThread.interrupt();
        }

        joinThread(publisherThread, 5000);
    }

    private static void joinThread(
        Thread thread, long timeoutMs) {

        if (thread == null) {
            return;
        }

        try {
            thread.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeResources() {

        try {
            if (store != null) {
                store.close();
            }
        } catch (IOException e) {
            System.err.println(
                "Cannot close DurableStore: " + e);
        }

        if (fq != null) {
            if (outputQueueId >= 0) {
                fq.closeQueue(outputQueueId);
            }

            if (inputQueueId >= 0) {
                fq.closeQueue(inputQueueId);
            }

            fq.closeLogger();
        }

        System.out.println("BundleAgent stopped.");
    }
}

