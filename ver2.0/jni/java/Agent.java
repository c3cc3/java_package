//
// Agent.java ( 삼성증권 SMS(단문) 기준 Audit 처리함
//
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Agent {

    // --------------------------------------------------
    // Configuration
    // --------------------------------------------------

    private static class Config {
        String logFile;
        int logLevel;

        String queuePath;
        String inputQueue;
        String outputQueue;

        int dequeueIdleMs;
        int retryDelayMs;
        int shutdownTimeoutSeconds;

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

    // --------------------------------------------------
    // Constants and shared objects
    // --------------------------------------------------

    private static Config config;

    private static final ObjectMapper JSON =
        new ObjectMapper();

    private static final AtomicBoolean running =
        new AtomicBoolean(true);

    private static FQueue fq;

    private static int inputQueueId = -1;
    private static int outputQueueId = -1;

    private enum ProcessResult {
        COMMITTED,
        RETRY,
        FATAL
    }

    // --------------------------------------------------
    // Main
    // --------------------------------------------------

    public static void main(String[] args) throws Exception {

        String configFile =
            args.length > 0 ? args[0] : "Agent.properties";

        // Load and validate configuration before opening resources.
        config = Config.load(configFile);

        fq = new FQueue();

        int rc = fq.openLogger(
            config.logFile,
            config.logLevel);

        if (rc <= 0) {
            throw new IllegalStateException(
                "Cannot open FileQueue logger: " + rc);
        }

        inputQueueId = fq.openQueue(
            config.queuePath,
            config.inputQueue);

        if (inputQueueId < 0) {
            fq.closeLogger();

            throw new IllegalStateException(
                "Cannot open input queue: "
                + config.inputQueue);
        }

        outputQueueId = fq.openQueue(
            config.queuePath,
            config.outputQueue);

        if (outputQueueId < 0) {
            fq.closeQueue(inputQueueId);
            fq.closeLogger();

            throw new IllegalStateException(
                "Cannot open output queue: "
                + config.outputQueue);
        }

        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> running.set(false)));

        ExecutorService worker =
            Executors.newSingleThreadExecutor();

        System.out.println("Agent started.");
        System.out.println("Config file: " + configFile);
        System.out.println("Input queue: " + config.inputQueue);
        System.out.println("Output queue: " + config.outputQueue);

        try {
            dequeueLoop(worker);
        } finally {
            running.set(false);
            worker.shutdown();

            if (!worker.awaitTermination(
                    config.shutdownTimeoutSeconds,
                    TimeUnit.SECONDS)) {

                worker.shutdownNow();
            }

            fq.closeQueue(outputQueueId);
            fq.closeQueue(inputQueueId);
            fq.closeLogger();

            System.out.println("Agent stopped.");
        }
    }

    // --------------------------------------------------
    // Input dequeue loop
    // --------------------------------------------------

    /*
     * Dequeue one XA message, then dispatch processing
     * to a worker thread.
     *
     * The current JNI wrapper permits only one pending XA
     * message per queue, so wait for processing before
     * dequeuing the next message.
     */
    private static void dequeueLoop(ExecutorService worker)
            throws Exception {

        while (running.get()) {

            FQueue.FQueueMessage message =
                fq.deQueueXA(inputQueueId);

            if (message == null) {
                Thread.sleep(config.dequeueIdleMs);
                continue;
            }

            Future<ProcessResult> future =
                worker.submit(() -> processMessage(message));

            ProcessResult result;

            try {
                result = future.get();
            } catch (ExecutionException e) {
                System.err.println(
                    "Worker exception: " + e.getCause());

                /*
                 * Do not blindly commit or cancel.
                 * The worker may already have enqueued a result.
                 */
                running.set(false);
                break;
            }

            if (result == ProcessResult.FATAL) {
                System.err.println(
                    "Fatal processing state; stopping agent.");

                running.set(false);
                break;
            }

            if (result == ProcessResult.RETRY) {
                Thread.sleep(config.retryDelayMs);
            }
        }
    }

    // --------------------------------------------------
    // Message processing
    // --------------------------------------------------

    /*
     * Audit JSON -> do business work -> enqueue result
     * -> commit input XA message.
     */
    private static ProcessResult processMessage(
            FQueue.FQueueMessage message) {

        String input = new String(
            message.data,
            StandardCharsets.UTF_8);

        String output;

        try {
            output = auditAndDoWork(input);
			if( output == null ) {
				int commitRc = fq.commit(message);

				if (commitRc != 0) {
					/*
					 * The result may already be in the output queue.
					 * Stop to avoid blindly retrying and duplicating it.
					 */
					System.err.println(
						"Input commit failed: " + commitRc);

					return ProcessResult.FATAL;
				}

				System.out.println(
					"(Health Checking) Processed input seq=" + message.seq);

				return ProcessResult.COMMITTED;
			}
        } catch (Exception e) {
            System.err.println(
                "Processing error: " + e.getMessage());

            output = makeErrorResult(
                input,
                "PROCESS_ERROR",
                e.getMessage());
        }

        byte[] outputBytes =
            output.getBytes(StandardCharsets.UTF_8);

        /*
         * Output queue may become temporarily full.
         * Keep the input XA pending and retry ONLY the same output
         * payload every second. Never re-run the business operation
         * or cancel the input XA because of an enqueue failure.
         *
         * NOTE: The current JNI API does not expose a distinct FULL
         * status here. Therefore rc <= 0 is retried as well. Verify
         * native error codes before using this policy in production.
         */
        while (running.get()) {
            FQueue.EnqueueResult enqueueResult;

            try {
                enqueueResult = fq.enQueue(
                    outputQueueId,
                    outputBytes,
                    FQueue.EN_NORMAL_MODE);
            } catch (Exception e) {
                System.err.println(
                    "Output enqueue threw an exception; XA remains pending: " + e);
                return ProcessResult.FATAL;
            }

            if (enqueueResult != null && enqueueResult.rc > 0) {
                break;
            }

            System.err.println(
                "Output enqueue failed (possible FULL), rc="
                + (enqueueResult == null ? "null" : enqueueResult.rc)
                + "; retrying the same message in 1 second. seq="
                + message.seq);

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println(
                    "Output enqueue retry interrupted; input XA not committed/cancelled.");
                return ProcessResult.FATAL;
            }
        }

        if (!running.get()) {
            System.err.println(
                "Agent stopping before output enqueue succeeded; input XA remains pending.");
            return ProcessResult.FATAL;
        }

        int commitRc = fq.commit(message);

        if (commitRc != 0) {
            /*
             * The result may already be in the output queue.
             * Stop to avoid blindly retrying and duplicating it.
             */
            System.err.println(
                "Input commit failed: " + commitRc);

            return ProcessResult.FATAL;
        }

        System.out.println(
            "Processed input seq=" + message.seq
            + ", resultBytes=" + outputBytes.length);

        return ProcessResult.COMMITTED;
    }

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
		** health_check
		*/
		if( root.get("MSG_KIND").asText() == "HEALTH_CHECK") {
			return null;
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
        result.put(
            "END_TIME",
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
         * Replace this sample with the actual business logic.
         */
        return "BUSINESS_OK";
    }

    // --------------------------------------------------
    // Error result
    // --------------------------------------------------

    private static String makeErrorResult(
            String input,
            String code,
            String reason) {

        ObjectNode result = JSON.createObjectNode();

        result.put("RESULT_CODE", code);

        result.put(
            "RESULT_MESSAGE",
            reason == null ? "Unknown error" : reason);

        result.put("INPUT", input);

        result.put(
            "PROCESSED_AT",
            Instant.now().toString());

        try {
            return JSON.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"RESULT_CODE\":\"RESULT_BUILD_ERROR\"}";
        }
    }
}

