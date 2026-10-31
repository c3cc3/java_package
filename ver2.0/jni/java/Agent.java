import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/** FileQueue JNI agent: map-based request audit and ACK/RESULT generation. */
public final class Agent {
    private static final DateTimeFormatter DATE14 =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);

    private final Properties config;
    private final Map<String, List<FieldMap.Field>> inputMaps = new HashMap<>();
    private final List<FieldMap.Field> ackMap;
    private final List<FieldMap.Field> resultMap;
    private final JsonGenerator generator = new JsonGenerator();
    private final FQueue fq = new FQueue();

    private int inputQueueId = -1;
    private int outputQueueId = -1;
    private int errorQueueId = -1;
    private boolean loggerOpened;
    private long responseSequence;

    private Agent(String configFile) throws Exception {
        config = new Properties();
        try (FileInputStream stream = new FileInputStream(configFile)) {
            config.load(stream);
        }
        inputMaps.put("S", loadMap("map.sms"));
        inputMaps.put("L", loadMap("map.lms"));
        inputMaps.put("M", loadMap("map.mms"));
        ackMap = loadMap("map.ack");
        resultMap = loadMap("map.result");
    }

    private String setting(String key) {
        String value = config.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return value.trim();
    }

    private int number(String key) {
        return Integer.parseInt(setting(key));
    }

    private List<FieldMap.Field> loadMap(String key) throws Exception {
        return FieldMap.load(Path.of(setting(key)));
    }

    private void open() {
        int loggerRc = fq.openLogger(setting("log.file"), number("log.level"));
        if (loggerRc <= 0) {
            throw new IllegalStateException("openLogger failed: " + loggerRc);
        }
        loggerOpened = true;
        inputQueueId = fq.openQueue(setting("queue.path"), setting("queue.input"));
        if (inputQueueId < 0) {
            throw new IllegalStateException("Cannot open input queue");
        }
        String errorQueue = setting("queue.error");
        if (errorQueue.equals(setting("queue.input")) ||
            errorQueue.equals(setting("queue.output"))) {
            throw new IllegalArgumentException("queue.error must be a separate queue");
        }
        outputQueueId = fq.openQueue(setting("queue.path"), setting("queue.output"));
        if (outputQueueId < 0) {
            throw new IllegalStateException("Cannot open output queue");
        }
        errorQueueId = fq.openQueue(setting("queue.path"), errorQueue);
        if (errorQueueId < 0) {
            throw new IllegalStateException("Cannot open error queue: " + errorQueue);
        }
    }

    private void close() {
        if (errorQueueId >= 0) {
            fq.closeQueue(errorQueueId);
        }
        if (outputQueueId >= 0) {
            fq.closeQueue(outputQueueId);
        }
        if (inputQueueId >= 0) {
            fq.closeQueue(inputQueueId);
        }
        if (loggerOpened) {
            fq.closeLogger();
        }
    }

    private static String now14() {
        return LocalDateTime.now().format(DATE14);
    }

    private static String limitedReason(String text) {
        if (text == null || text.isBlank()) {
            return "UNKNOWN";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c <= 126 && result.length() < 32) {
                result.append(c);
            }
        }
        return result.length() == 0 ? "AUDIT_ERROR" : result.toString();
    }

    /** Override correlation and status fields, never randomize them. */
    private String response(List<FieldMap.Field> fields,
                            Map<String, String> request,
                            String kind,
                            String code,
                            String group,
                            String reason) {
        Map<String, String> values = new HashMap<>();
        values.put("MSG_KIND", kind);
        values.put("RESULT_CODE", code);
        values.put("RESULT_GROUP_CODE", group);
        values.put("REASON", limitedReason(reason));
        values.put("SEND_TIME", now14());
        values.put("DONE_DATE", now14());

        String history = request.get("HISTORY_KEY");
        if (history != null && !history.isEmpty()) {
            values.put("HISTORY_KEY", history);
        } else {
            throw new IllegalArgumentException(
                "HISTORY_KEY missing: cannot safely correlate response");
        }

        // Override only fields that are defined in this response map.
        Map<String, String> selected = new HashMap<>();
        for (FieldMap.Field field : fields) {
            if (values.containsKey(field.name)) {
                selected.put(field.name, values.get(field.name));
            }
        }
        return generator.generate(fields, ++responseSequence, selected);
    }

    /** Placeholder business operation; replace with actual telecom submission. */
    private void doBusinessWork(Map<String, String> request) {
        // Existing Agent had only a sample return value: BUSINESS_OK.
    }

    /** Retry exactly the same bytes; never regenerate or resubmit business work. */
    private boolean enqueueWithRetry(String payload, long inputSeq) {
        return enqueueBytesWithRetry(outputQueueId,
            payload.getBytes(StandardCharsets.UTF_8), inputSeq);
    }

    private boolean enqueueBytesWithRetry(int queueId, byte[] bytes, long inputSeq) {
        while (RUNNING.get()) {
            FQueue.EnqueueResult result;
            try {
                result = fq.enQueue(queueId, bytes, FQueue.EN_NORMAL_MODE);
            } catch (Exception error) {
                throw new IllegalStateException("Output enqueue exception; XA pending", error);
            }
            if (result != null && result.rc > 0) {
                return true;
            }
            System.err.println("enqueue failed rc=" +
                (result == null ? "null" : result.rc) +
                ", input seq=" + inputSeq + "; retrying same payload");
            sleep(number("retry.delay.ms"));
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            RUNNING.set(false);
        }
    }

    /**
     * Unreplyable messages go to a separate error queue. Preserve the exact
     * input bytes, then commit XA only after the error enqueue succeeds.
     */
    private boolean quarantine(FQueue.FQueueMessage message, String reason) {
        System.err.println("Rejected input seq=" + message.seq + " reason=" + reason);
        if (!enqueueBytesWithRetry(errorQueueId, message.data, message.seq)) {
            return false;
        }
        return commit(message);
    }

    /** Process a single pending XA; never commit after a failed output enqueue. */
    private boolean process(FQueue.FQueueMessage message) {
        Map<String, String> request;
        try {
            request = JsonAudit.parseObject(
                new String(message.data, StandardCharsets.UTF_8));
        } catch (RuntimeException error) {
            return quarantine(message, "INVALID_JSON: " + error.getMessage());
        }

        String kind = request.get("MSG_KIND");
        if ("HEALTH_CHECK".equals(kind)) {
            return commit(message);
        }
        if (kind == null || !kind.matches("[SLM]")) {
            return quarantine(message, "Missing/unsupported MSG_KIND: " + kind);
        }
        if (request.get("HISTORY_KEY") == null ||
            request.get("HISTORY_KEY").isEmpty()) {
            return quarantine(message, "Missing HISTORY_KEY");
        }
        List<FieldMap.Field> map = inputMaps.get(kind);
        List<String> errors = new ArrayList<>();
        if (map == null) {
            errors.add("Unsupported MSG_KIND: " + kind);
        } else {
            errors.addAll(JsonAudit.validateObject(map, request));
        }

        String ack;
        String result = null;
        try {
            if (!errors.isEmpty()) {
                System.err.println("Audit failed seq=" + message.seq + " " + errors);
                ack = response(ackMap, request, kind,
                    "9999", "03", "AUDIT_ERROR");
            } else {
                // Do not submit business twice when an output enqueue is retried.
                doBusinessWork(request);
                ack = response(ackMap, request, kind, "0000", "01", "SUCCESS");
                result = response(resultMap, request, kind, "0000", "01", "DELIVERED");
            }
        } catch (Exception error) {
            // If response construction fails, no output was published yet.
            // Preserve input in the error queue instead of blocking all traffic.
            return quarantine(message, "Response/business failure: " + error.getMessage());
        }

        // Two independent enqueues are NOT atomic. ACK may be duplicated after crash.
        if (!enqueueWithRetry(ack, message.seq)) {
            return false;
        }
        if (result != null && !enqueueWithRetry(result, message.seq)) {
            return false;
        }
        return commit(message);
    }

    private boolean commit(FQueue.FQueueMessage message) {
        int rc = fq.commit(message);
        if (rc != 0) {
            System.err.println("XA commit failed seq=" + message.seq + " rc=" + rc);
            return false;
        }
        System.out.println("Committed seq=" + message.seq);
        return true;
    }

    private void run() {
        open();
        System.out.println("Agent started with 5 map files; error queue=" +
            setting("queue.error"));
        while (RUNNING.get()) {
            FQueue.FQueueMessage message = fq.deQueueXA(inputQueueId);
            if (message == null) {
                sleep(number("dequeue.idle.ms"));
                continue;
            }
            if (!process(message)) {
                RUNNING.set(false);
                throw new IllegalStateException(
                    "Processing stopped with unresolved XA seq=" + message.seq);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String file = args.length > 0 ? args[0] : "conf/Agent.properties";
        Agent agent = new Agent(file);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> RUNNING.set(false)));
        try {
            agent.run();
        } finally {
            agent.close();
        }
    }
}
