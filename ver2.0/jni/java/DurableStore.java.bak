
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.file.StandardOpenOption.*;


public final class DurableStore implements AutoCloseable {

    // Group Commit 설정
    private static final int JOURNAL_BATCH_MAX = 64;
    private static final long JOURNAL_BATCH_WAIT_MS = 2;

    // 서로 다른 메시지의 처리는 병렬화하고,
    // 같은 메시지의 상태 변경은 직렬화한다.
    private static final int LOCK_STRIPE_COUNT = 256;

    private final ReentrantLock[] messageLocks =
        new ReentrantLock[LOCK_STRIPE_COUNT];

    private final BlockingQueue<JournalRequest> journalQueue =
        new LinkedBlockingQueue<>();

    private final Object lifecycleLock = new Object();

    private volatile boolean closing;
    private volatile boolean closed;
    private volatile IOException journalFailure;

    private Thread journalThread;

    // 성능 통계
    private final AtomicLong appendCount = new AtomicLong();
    private final AtomicLong jsonTotalNs = new AtomicLong();
    private final AtomicLong writeTotalNs = new AtomicLong();
    private final AtomicLong forceTotalNs = new AtomicLong();

    private final AtomicLong jsonMaxNs = new AtomicLong();
    private final AtomicLong writeMaxNs = new AtomicLong();
    private final AtomicLong forceMaxNs = new AtomicLong();


    public enum State {
        STAGED,
        COMMITTING,
        READY,
        PROCESSING,
        RETRY_WAIT,
        RESULT_PENDING,
        DONE
    }

    public static final class StoredMessage {
        public final String id;
        public final int queueId;
        public final byte[] data;
        public final long seq;
        public final long runTime;
        public final String unlinkFilename;

        public State state;
        public int retryCount;
        public byte[] result;

        StoredMessage(
            String id,
            int queueId,
            byte[] data,
            long seq,
            long runTime,
            String unlinkFilename
        ) {
            this.id = id;
            this.queueId = queueId;
            this.data = data;
            this.seq = seq;
            this.runTime = runTime;
            this.unlinkFilename = unlinkFilename;
            this.state = State.STAGED;
        }
    }

    private static final ObjectMapper JSON =
        new ObjectMapper();

    private final Path journal;
    private final FileChannel channel;

    private final Map<String, StoredMessage> messages =
        new LinkedHashMap<>();

    private static final class JournalRequest {
        final byte[] bytes;
        final CountDownLatch done = new CountDownLatch(1);

        volatile IOException error;

        JournalRequest(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    public DurableStore(Path journalPath) throws IOException {
        journal = journalPath.toAbsolutePath();

        Path parent = journal.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        channel = FileChannel.open(
            journal, CREATE, READ, WRITE
        );

        try {
            recoverJournal();
            channel.position(channel.size());

            for (int i = 0; i < LOCK_STRIPE_COUNT; i++) {
                messageLocks[i] = new ReentrantLock();
            }

            journalThread = new Thread(
                this::journalWriterLoop,
                "DurableStore-Journal"
            );

            journalThread.setDaemon(false);
            journalThread.start();

        } catch (IOException | RuntimeException e) {
            try {
                channel.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    private ReentrantLock lockFor(String id) {
        int index = (id.hashCode() & 0x7fffffff)
            % LOCK_STRIPE_COUNT;

        return messageLocks[index];
    }

    /*
     * ID must remain stable across redelivery.
     */
    public static String messageId(
        FQueue.FQueueMessage m
    ) {
        return m.queueId + ":" + m.seq;
    }

    public synchronized boolean contains(String id) {
        return messages.containsKey(id);
    }

    public StoredMessage stage(
        FQueue.FQueueMessage m
    ) throws IOException {

        String id = messageId(m);
        ReentrantLock lock = lockFor(id);
        lock.lock();

        try {
            synchronized (this) {
                StoredMessage existing = messages.get(id);
                if (existing != null) {
                    return existing;
                }
            }

            StoredMessage stored = new StoredMessage(
                id,
                m.queueId,
                m.data.clone(),
                m.seq,
                m.runTime,
                m.unlinkFilename
            );

            // Journal state and memory state must agree.
            stored.state = State.COMMITTING;

            ObjectNode event = JSON.createObjectNode();

            event.put("event", "STAGE");
            event.put("state", State.COMMITTING.name());
            event.put("id", id);
            event.put("queueId", m.queueId);
            event.put("seq", m.seq);
            event.put("runTime", m.runTime);
            event.put(
                "data",
                Base64.getEncoder().encodeToString(m.data)
            );

            if (m.unlinkFilename != null) {
                event.put("unlinkFilename", m.unlinkFilename);
            }

            // Returns only after the batch force succeeds.
            append(event);

            synchronized (this) {
                messages.put(id, stored);
            }

            return stored;

        } finally {
            lock.unlock();
        }
    }

    public void setState(
        String id,
        State state
    ) throws IOException {

        ReentrantLock lock = lockFor(id);
        lock.lock();

        try {
            synchronized (this) {
                require(id);
            }

            ObjectNode event = JSON.createObjectNode();
            event.put("event", "STATE");
            event.put("id", id);
            event.put("state", state.name());

            append(event);

            synchronized (this) {
                require(id).state = state;
            }

        } finally {
            lock.unlock();
        }
    }

    public void discard(String id) throws IOException {
        ReentrantLock lock = lockFor(id);
        lock.lock();

        try {
            synchronized (this) {
                if (!messages.containsKey(id)) {
                    return;
                }
            }

            ObjectNode event = JSON.createObjectNode();
            event.put("event", "DISCARD");
            event.put("id", id);

            append(event);

            synchronized (this) {
                messages.remove(id);
            }

        } finally {
            lock.unlock();
        }
    }

    public void setRetry(String id) throws IOException {
        ReentrantLock lock = lockFor(id);
        lock.lock();

        try {
            int nextRetryCount;

            synchronized (this) {
                nextRetryCount = require(id).retryCount + 1;
            }

            ObjectNode event = JSON.createObjectNode();
            event.put("event", "RETRY");
            event.put("id", id);
            event.put("retryCount", nextRetryCount);

            append(event);

            synchronized (this) {
                StoredMessage m = require(id);
                m.retryCount = nextRetryCount;
                m.state = State.RETRY_WAIT;
            }

        } finally {
            lock.unlock();
        }
    }

    public void setResultPending(
        String id,
        byte[] result
    ) throws IOException {

        ReentrantLock lock = lockFor(id);
        lock.lock();

        try {
            synchronized (this) {
                require(id);
            }

            ObjectNode event = JSON.createObjectNode();
            event.put("event", "RESULT");
            event.put(
                "id",
                id
            );
            event.put(
                "result",
                Base64.getEncoder().encodeToString(result)
            );

            // Result must be durable before it is published.
            append(event);

            synchronized (this) {
                StoredMessage m = require(id);
                m.result = result.clone();
                m.state = State.RESULT_PENDING;
            }

        } finally {
            lock.unlock();
        }
    }

    public synchronized List<StoredMessage>
    recoverableMessages() {
        List<StoredMessage> result = new ArrayList<>();

        for (StoredMessage m : messages.values()) {
            if (m.state == State.COMMITTING
                || m.state == State.READY
                || m.state == State.PROCESSING
                || m.state == State.RETRY_WAIT) {
                result.add(m);
            }
        }

        return result;
    }

    public synchronized List<StoredMessage>
    pendingResults() {
        List<StoredMessage> result = new ArrayList<>();

        for (StoredMessage m : messages.values()) {
            if (m.state == State.RESULT_PENDING) {
                result.add(m);
            }
        }

        return result;
    }

    public synchronized StoredMessage get(String id) {
        return messages.get(id);
    }

    private StoredMessage require(String id) {
        StoredMessage m = messages.get(id);

        if (m == null) {
            throw new IllegalArgumentException(
                "Unknown message: " + id
            );
        }

        return m;
    }

    /*
     * Serialize the event in the caller thread, enqueue it,
     * and wait until the journal thread has written and forced
     * the batch containing this event.
     */
    private void append(ObjectNode event) throws IOException {
        long startNs = System.nanoTime();

        byte[] bytes = (
            JSON.writeValueAsString(event) + "\n"
        ).getBytes(StandardCharsets.UTF_8);

        long jsonNs = System.nanoTime() - startNs;
        jsonTotalNs.addAndGet(jsonNs);
        updateMax(jsonMaxNs, jsonNs);

        JournalRequest request = new JournalRequest(bytes);

        synchronized (lifecycleLock) {
            if (closing || closed) {
                throw new IOException(
                    "DurableStore is closing or closed"
                );
            }

            if (journalFailure != null) {
                throw new IOException(
                    "Journal writer has failed",
                    journalFailure
                );
            }

            journalQueue.add(request);
        }

        try {
            request.done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            // The request may still be written by the journal
            // thread. The caller must treat this outcome as
            // uncertain and stop normal processing.
            throw new IOException(
                "Interrupted waiting for journal completion",
                e
            );
        }

        if (request.error != null) {
            throw request.error;
        }
    }

	private final AtomicLong forceBatchCount = new AtomicLong();
	private final AtomicLong forceEventCount = new AtomicLong();
	private final AtomicLong forceLargestBatch = new AtomicLong();
    /*
     * Collect multiple events, write them in order, and issue
     * one force(true) for the entire batch.
     */
    private void journalWriterLoop() {

        while (true) {
            List<JournalRequest> batch = new ArrayList<>();

            try {
                JournalRequest first = journalQueue.poll(
                    10, TimeUnit.MILLISECONDS
                );

                if (first == null) {
                    if (closing && journalQueue.isEmpty()) {
                        return;
                    }
                    continue;
                }

                batch.add(first);

                long deadline = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(
                        JOURNAL_BATCH_WAIT_MS
                    );

                while (batch.size() < JOURNAL_BATCH_MAX) {
                    long remaining = deadline - System.nanoTime();

                    if (remaining <= 0) {
                        break;
                    }

                    JournalRequest next = journalQueue.poll(
                        remaining, TimeUnit.NANOSECONDS
                    );

                    if (next == null) {
                        break;
                    }

                    batch.add(next);
                }

                ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

                for (JournalRequest request : batch) {
                    output.write(request.bytes);
                }

                byte[] batchBytes = output.toByteArray();
                ByteBuffer buffer = ByteBuffer.wrap(batchBytes);

                long writeStartNs = System.nanoTime();

                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }

                long writeNs = System.nanoTime() - writeStartNs;

                long forceStartNs = System.nanoTime();

                // One durable sync for all events in this batch.
                channel.force(true);

				forceBatchCount.incrementAndGet();
				forceEventCount.addAndGet(batch.size());
				updateMax(forceLargestBatch, batch.size());

                long forceNs = System.nanoTime() - forceStartNs;

                writeTotalNs.addAndGet(writeNs);
                forceTotalNs.addAndGet(forceNs);

                updateMax(writeMaxNs, writeNs);
                updateMax(forceMaxNs, forceNs);

                appendCount.addAndGet(batch.size());

                // No request reports success before force succeeds.
                for (JournalRequest request : batch) {
                    request.done.countDown();
                }

            } catch (InterruptedException e) {
                if (!closing) {
                    failJournal(
                        new IOException(
                            "Journal thread interrupted", e
                        ),
                        batch
                    );
                    return;
                }

            } catch (IOException | RuntimeException e) {
                IOException error = (e instanceof IOException)
                    ? (IOException) e
                    : new IOException("Journal writer failed", e);

                failJournal(error, batch);
                return;
            }
        }
    }

    private void failJournal(
        IOException error,
        List<JournalRequest> currentBatch
    ) {
        synchronized (lifecycleLock) {
            if (journalFailure == null) {
                journalFailure = error;
            }

            for (JournalRequest request : currentBatch) {
                request.error = journalFailure;
                request.done.countDown();
            }

            JournalRequest queued;

            while ((queued = journalQueue.poll()) != null) {
                queued.error = journalFailure;
                queued.done.countDown();
            }
        }
    }

    private static void updateMax(
        AtomicLong max,
        long value
    ) {
        long current;

        do {
            current = max.get();

            if (value <= current) {
                return;
            }

        } while (!max.compareAndSet(current, value));
    }

    private void recoverJournal() throws IOException {
        long size = channel.size();

        if (size == 0) {
            return;
        }

        if (size > Integer.MAX_VALUE) {
            throw new IOException(
                "Journal too large for initial recovery implementation"
            );
        }

        ByteBuffer buffer = ByteBuffer.allocate((int) size);
        channel.position(0);

        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                break;
            }
        }

        byte[] bytes = buffer.array();
        int validLength = bytes.length;

        // Discard a trailing incomplete record after a crash.
        while (validLength > 0
            && bytes[validLength - 1] != '\n') {
            validLength--;
        }

        if (validLength != bytes.length) {
            channel.truncate(validLength);
            channel.force(true);
        }

        String content = new String(
            bytes,
            0,
            validLength,
            StandardCharsets.UTF_8
        );

        for (String line : content.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }

            JsonNode e = JSON.readTree(line);
            String event = e.path("event").asText();
            String id = e.path("id").asText();

            switch (event) {
                case "STAGE": {
                    StoredMessage m = new StoredMessage(
                        id,
                        e.path("queueId").asInt(),
                        Base64.getDecoder().decode(
                            e.path("data").asText()
                        ),
                        e.path("seq").asLong(),
                        e.path("runTime").asLong(),
                        e.has("unlinkFilename")
                            ? e.get("unlinkFilename").asText()
                            : null
                    );

                    m.state = State.valueOf(
                        e.path("state").asText("COMMITTING")
                    );

                    messages.put(id, m);
                    break;
                }

                case "STATE": {
                    StoredMessage m = require(id);
                    m.state = State.valueOf(
                        e.path("state").asText()
                    );
                    break;
                }

                case "RETRY": {
                    StoredMessage m = require(id);
                    m.retryCount =
                        e.path("retryCount").asInt();
                    m.state = State.RETRY_WAIT;
                    break;
                }

                case "RESULT": {
                    StoredMessage m = require(id);
                    m.result = Base64.getDecoder().decode(
                        e.path("result").asText()
                    );
                    m.state = State.RESULT_PENDING;
                    break;
                }

                case "DISCARD": {
                    messages.remove(id);
                    break;
                }

                default:
                    throw new IOException(
                        "Unknown journal event: " + event
                    );
            }
        }
    }

	
	public void printAppendStats() {
		long count = appendCount.getAndSet(0);

		long jsonTotal = jsonTotalNs.getAndSet(0);
		long writeTotal = writeTotalNs.getAndSet(0);
		long forceTotal = forceTotalNs.getAndSet(0);

		long jsonMax = jsonMaxNs.getAndSet(0);
		long writeMax = writeMaxNs.getAndSet(0);
		long forceMax = forceMaxNs.getAndSet(0);

		// Group Commit 통계
		long forceBatches = forceBatchCount.getAndSet(0);
		long forceEvents = forceEventCount.getAndSet(0);
		long maxBatchSize = forceLargestBatch.getAndSet(0);

		if (count == 0) {
			return;
		}

		double avgBatchSize =
			forceBatches > 0
				? forceEvents / (double) forceBatches
				: 0.0;

		System.out.printf(
			"[Store Stats] events=%d"
			+ " JSON_avg=%.3fms JSON_max=%.3fms"
			+ " WRITE_avg_per_event=%.3fms WRITE_batch_max=%.3fms"
			+ " FORCE_avg_per_event=%.3fms FORCE_batch_max=%.3fms"
			+ " forceBatches=%d forceEvents=%d"
			+ " avgBatchSize=%.2f maxBatchSize=%d%n",
			count,
			jsonTotal / (double) count / 1_000_000.0,
			jsonMax / 1_000_000.0,
			writeTotal / (double) count / 1_000_000.0,
			writeMax / 1_000_000.0,
			forceTotal / (double) count / 1_000_000.0,
			forceMax / 1_000_000.0,
			forceBatches,
			forceEvents,
			avgBatchSize,
			maxBatchSize
		);
	}

    @Override
    public void close() throws IOException {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }

            closing = true;
        }

        boolean interrupted = false;

        while (journalThread.isAlive()) {
            try {
                journalThread.join();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        IOException closeError = null;

        try {
            channel.close();
        } catch (IOException e) {
            closeError = e;
        }

        synchronized (lifecycleLock) {
            closed = true;
        }

        if (interrupted) {
            Thread.currentThread().interrupt();

            IOException e = new IOException(
                "Interrupted while closing DurableStore"
            );

            if (closeError != null) {
                e.addSuppressed(closeError);
            }

            throw e;
        }

        if (closeError != null) {
            throw closeError;
        }

        if (journalFailure != null) {
            throw new IOException(
                "DurableStore closed after journal failure",
                journalFailure
            );
        }
    }
}

