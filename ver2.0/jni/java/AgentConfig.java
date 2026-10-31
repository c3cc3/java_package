
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class AgentConfig {
    public String logFile;
    public int logLevel;

    public String queuePath;
    public String inputQueue;
    public String outputQueue;

    public int dequeueIdleMs;
    public int retryDelayMs;
    public int shutdownTimeoutSeconds;

	public int bundleSize;

    public static AgentConfig load(String file)
            throws IOException {

        Properties p = new Properties();

        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
        }

        AgentConfig c = new AgentConfig();

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

        if (c.logLevel < 0
                || c.dequeueIdleMs < 0
                || c.retryDelayMs < 0
                || c.shutdownTimeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                "Invalid numeric configuration value");
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

        try {
            return Integer.parseInt(required(p, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid integer configuration: " + key, e);
        }
    }
}

