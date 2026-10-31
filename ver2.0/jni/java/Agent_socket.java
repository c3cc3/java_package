import com.clang.socket.FileQueueSocket;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.*;

/** FileQueue Socket-based SMS Agent. Java 17. Telecom simulator is TEST ONLY. */
public final class Agent_socket {
    private static final Logger LOG = Logger.getLogger(Agent_socket.class.getName());
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final Config cfg;
    private final MapMessageCodec codec;
    private FileQueueSocket errorOutput;
    private final Object errorLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final BlockingQueue<JSONObject> reports = new LinkedBlockingQueue<>();
    private final TelecomAdapter telecom;
    private FileQueueSocket input, output;
    private final Object outputLock = new Object();
    private Thread reportThread;
    private final Object inputLock = new Object();

    private Agent_socket(Config cfg) throws Exception {
        this.cfg = cfg;
        this.codec = new MapMessageCodec(cfg.props);
        this.telecom = new SimulatedTelecom(cfg.simulatedDelayMs);
    }

    public static void main(String[] args) throws Exception {
        Config cfg = Config.load(args.length == 0 ? "Agent_socket.properties" : args[0]);
        configureLogging(cfg.logFile);
        Agent_socket agent = new Agent_socket(cfg);
        Runtime.getRuntime().addShutdownHook(new Thread(agent::stop, "agent-shutdown"));
        agent.run();
    }

    private void run() throws Exception {
        LOG.warning("SIMULATION MODE: telecom requests are NOT sent to a real carrier");
        try {
            // Connections are established lazily; temporary server outages do not exit the daemon.
            reportThread = new Thread(this::reportLoop, "result-publisher");
            reportThread.start();
            LOG.info("Agent_socket started, input=" + cfg.inputQueue + ", output=" + cfg.outputQueue);
            while (running.get()) {
                try {
                    ensureInputConnected();
                    FileQueueSocket.Result deq = input.read();
                    if (deq.status() == FileQueueSocket.Status.EMPTY) {
                        pause(cfg.idleMs);
                        continue;
                    }
                    if (deq.status() != FileQueueSocket.Status.OK) {
                        throw new IOException("DEQU rejected: " + deq.serverResult());
                    }
                    // A completed DEQU is handed to business processing once.
                    // Only transport failures trigger reconnection; business processing
                    // does not re-DEQU the same message explicitly.
                    try {
                        process(deq.message());
                    } catch (RuntimeException badMessage) {
                        LOG.log(Level.SEVERE, "Per-message processing failed; saving original to TST", badMessage);
                        publishError(deq.message());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException | IllegalStateException e) {
                    if (!running.get()) break;
                    LOG.log(Level.WARNING, "Socket/session error; re-LINK after "
                            + cfg.reconnectDelayMs + "ms", e);
                    closeInput();
                    closeOutput();
                    pause(cfg.reconnectDelayMs);
                }
            }
        } finally {
            stop();
            if (reportThread != null) reportThread.join(3000);
            telecom.close();
            closeError();
            closeInput();
            closeOutput();
            LOG.info("Agent_socket stopped");
        }
    }

    private void ensureInputConnected() throws IOException {
        synchronized (inputLock) {
            if (input == null) input = connect(cfg.inputQueue);
        }
    }

    private void closeInput() {
        synchronized (inputLock) {
            if (input != null) { input.close(); input = null; }
        }
    }

    private void closeOutput() {
        synchronized (outputLock) {
            if (output != null) { output.close(); output = null; }
        }
    }

    private void process(String raw) throws Exception {
        Map<String,String> values;
        try {
            values = codec.parse(raw);
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "INVALID_JSON: saving original to error queue", ex);
            publishError(raw);
            return;
        }
        if ("HEALTH_CHECK".equals(values.get("MSG_KIND"))) return;
        String kind = values.get("MSG_KIND");
        if (!Set.of("S", "L", "M").contains(kind == null ? "" : kind)
                || values.getOrDefault("HISTORY_KEY", "").isBlank()) {
            LOG.warning("Unreplyable request, saving original to error queue: MSG_KIND=" + kind);
            publishError(raw);
            return;
        }
        List<String> errors = codec.validate(values);
        if (!errors.isEmpty()) {
            LOG.warning("AUDIT_ERROR: " + errors + " HISTORY_KEY=" + values.get("HISTORY_KEY"));
            publish(codec.generate(values, "ACK", "9999", "03", "AUDIT_ERROR"));
            return;
        }
        JSONObject request = new JSONObject(raw);
        synchronized (outputLock) {
            TelecomAcceptance accepted;
            try {
                accepted = telecom.submit(request, this::onDeliveryReport);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Telecom submission error", ex);
                publish(codec.generate(values, "ACK", "9999", "03", "TELECOM_ERROR"));
                return;
            }
            if (accepted == null) {
                publish(codec.generate(values, "ACK", "9999", "03", "TELECOM_ERROR"));
                return;
            }
            boolean success = "0000".equals(accepted.code());
            values.put("SESSION_ID", accepted.sessionId() == null ? "" : accepted.sessionId());
            values.put("SESSION_NO", accepted.sessionNo() == null ? "" : accepted.sessionNo());
            publish(codec.generate(values, "ACK", success ? "0000" : "9999",
                    accepted.group() == null || accepted.group().isBlank() ? "03" : accepted.group(),
                    success ? "SUCCESS" : "TELECOM_REJECTED"));
        }
    }

    private void onDeliveryReport(JSONObject original, TelecomDelivery delivery) {
        try {
            Map<String,String> values = codec.parse(original.toString());
            values.put("SESSION_ID", delivery.sessionId == null ? "" : delivery.sessionId);
            values.put("SESSION_NO", delivery.sessionNo == null ? "" : delivery.sessionNo);
            reports.offer(new JSONObject(codec.generate(values, "RESULT", delivery.code,
                    delivery.group == null || delivery.group.isBlank() ? "03" : delivery.group,
                    delivery.reason == null || delivery.reason.isBlank() ? "DELIVERED" : delivery.reason)));
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Delivery report generation failed; needs manual recovery", ex);
        }
    }

    private void publishError(String raw) throws InterruptedException {
        synchronized (errorLock) {
            while (running.get()) {
                try {
                    if (errorOutput == null) errorOutput = connect(cfg.errorQueue);
                    FileQueueSocket.Result r = errorOutput.write(raw);
                    if (r.status() == FileQueueSocket.Status.OK) return;
                    if (r.status() != FileQueueSocket.Status.FULL)
                        throw new IOException("Error queue ENQU rejected: " + r.serverResult());
                    pause(cfg.fullRetryMs);
                } catch (IOException | IllegalStateException ex) {
                    LOG.log(Level.WARNING, "Error queue unavailable; retrying", ex);
                    closeError();
                    pause(cfg.reconnectDelayMs);
                }
            }
            throw new InterruptedException("Stopping before error queue publish");
        }
    }

    private void closeError() {
        synchronized (errorLock) {
            if (errorOutput != null) { errorOutput.close(); errorOutput = null; }
        }
    }

    private void reportLoop() {
        while (running.get() || !reports.isEmpty()) {
            try {
                JSONObject report = reports.poll(200, TimeUnit.MILLISECONDS);
                if (report != null) publish(report.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Unexpected RESULT publisher failure", e);
                running.set(false);
                return;
            }
        }
    }

    private void publish(String bytes) throws InterruptedException {
        synchronized (outputLock) {
            while (running.get()) {
                try {
                    if (output == null) output = connect(cfg.outputQueue);
                    FileQueueSocket.Result r = output.write(bytes);
                    if (r.status() == FileQueueSocket.Status.OK) return;
                    if (r.status() == FileQueueSocket.Status.FULL) {
                        LOG.warning("Output FULL; retry same JSON in " + cfg.fullRetryMs + "ms");
                        pause(cfg.fullRetryMs);
                    } else {
                        throw new IOException("ENQU rejected: " + r.serverResult());
                    }
                } catch (IOException | IllegalStateException e) {
                    if (!running.get()) break;
                    // ENQU response can be lost after the server accepted it.
                    // Replaying the identical payload may cause duplicates.
                    LOG.log(Level.WARNING,
                            "Output socket/session lost; re-LINK and retry same payload "
                            + "(duplicate possible). Delay=" + cfg.reconnectDelayMs + "ms", e);
                    if (output != null) { output.close(); output = null; }
                    pause(cfg.reconnectDelayMs);
                }
            }
        }
        throw new InterruptedException("Stopping before output publish confirmed");
    }

    private FileQueueSocket connect(String queue) throws IOException {
        FileQueueSocket s = new FileQueueSocket(cfg.host, cfg.port, cfg.queuePath, queue,
                cfg.connectTimeoutMs, cfg.readTimeoutMs, cfg.maxFrameBytes);
        s.open();
        LOG.info("LINK OK queue=" + queue);
        return s;
    }

    private void stop() {
        running.set(false);
        if (reportThread != null) reportThread.interrupt();
        closeInput();
        // Publisher owns output reconnect/close while running.
    }
    private static void pause(long ms) throws InterruptedException { Thread.sleep(ms); }
    private static String now() { return LocalDateTime.now().format(TS); }
    private static void configureLogging(String path) throws IOException {
        Path p = Path.of(path).toAbsolutePath();
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        FileHandler f = new FileHandler(p.toString(), true);
        f.setFormatter(new SimpleFormatter());
        LOG.addHandler(f);
        LOG.setLevel(Level.INFO);
    }

    interface DeliveryCallback { void completed(JSONObject original, TelecomDelivery result); }
    interface TelecomAdapter extends AutoCloseable {
        TelecomAcceptance submit(JSONObject request, DeliveryCallback callback) throws Exception;
        @Override void close();
    }
    record TelecomAcceptance(String code, String group, String reason, String sessionId, String sessionNo) {}
    record TelecomDelivery(String code, String group, String reason, String sessionId, String sessionNo, String doneDate) {}

    /** TEST ONLY: creates an acceptance immediately and a simulated final report later. */
    private static final class SimulatedTelecom implements TelecomAdapter {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        private final long delay;
        private long sequence;
        SimulatedTelecom(long delay) { this.delay = delay; }
        public synchronized TelecomAcceptance submit(JSONObject r, DeliveryCallback callback) {
            String id = "SIM" + (++sequence);
            JSONObject copy = new JSONObject(r.toString());
            executor.schedule(() -> callback.completed(copy,
                    new TelecomDelivery("0000", "01", "", id, id, now())), delay, TimeUnit.MILLISECONDS);
            return new TelecomAcceptance("0000", "01", "", id, id);
        }
        public void close() { executor.shutdown(); }
    }

    static final class Config {
        String host, queuePath, inputQueue, outputQueue, errorQueue, telecomCode, logFile;
        Properties props;
        int port, connectTimeoutMs, readTimeoutMs, maxFrameBytes;
        long idleMs, fullRetryMs, simulatedDelayMs, reconnectDelayMs;
        static Config load(String file) throws IOException {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(Path.of(file))) { p.load(in); }
            Config c = new Config();
            c.props = p;
            c.errorQueue = required(p, "queue.error");
            c.host = required(p,"server.host");
            c.port = Integer.parseInt(required(p,"server.port"));
            c.queuePath = required(p,"queue.path");
            c.inputQueue = required(p,"queue.input");
            c.outputQueue = required(p,"queue.output");
            c.telecomCode = required(p,"telecom.code");
            c.logFile = required(p,"log.file");
            c.connectTimeoutMs = Integer.parseInt(required(p,"socket.connect.timeout.ms"));
            c.readTimeoutMs = Integer.parseInt(required(p,"socket.read.timeout.ms"));
            c.maxFrameBytes = Integer.parseInt(required(p,"socket.max.frame.bytes"));
            c.idleMs = Long.parseLong(required(p,"dequeue.idle.ms"));
            c.fullRetryMs = Long.parseLong(required(p,"enqueue.full.retry.ms"));
            c.simulatedDelayMs = Long.parseLong(required(p,"telecom.simulated.result.delay.ms"));
            c.reconnectDelayMs = Long.parseLong(p.getProperty("socket.reconnect.delay.ms", "1000").trim());
            if (c.port < 1 || c.port > 65535 || c.connectTimeoutMs <= 0 || c.readTimeoutMs <= 0
                    || c.maxFrameBytes < 4 || c.idleMs < 0 || c.fullRetryMs < 1 || c.simulatedDelayMs < 0 || c.reconnectDelayMs < 1)
                throw new IllegalArgumentException("Invalid configuration range");
            return c;
        }
        static String required(Properties p, String k) {
            String v = p.getProperty(k);
            if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing " + k);
            return v.trim();
        }
    }
}
