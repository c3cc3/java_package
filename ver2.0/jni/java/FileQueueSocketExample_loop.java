import com.clang.socket.FileQueueSocket;
import org.json.JSONObject;

/**
 * Usage: java FileQueueSocketExample_loop HOST PORT QUEUE_PATH QUEUE_NAME ENQU|DEQU COUNT
 * ENQU: send JSON with SEQ=1..COUNT; retry FULL after 1 second.
 * DEQU: receive COUNT messages; retry EMPTY after 1 second.
 */
public final class FileQueueSocketExample_loop {
    private static final long RETRY_DELAY_MS = 1000L;

    public static void main(String[] args) {
        if (args.length != 6) {
            usage();
            System.exit(2);
        }

        final int port;
        final long count;
        final String mode = args[4].toUpperCase(java.util.Locale.ROOT);
        try {
            port = Integer.parseInt(args[1]);
            count = Long.parseLong(args[5]);
            if (port < 1 || port > 65535 || count <= 0 ||
                    !("ENQU".equals(mode) || "DEQU".equals(mode))) {
                throw new IllegalArgumentException("Invalid PORT, MODE, or COUNT");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            usage();
            System.exit(2);
            return;
        }

        long completed = 0;
        long startNanos = System.nanoTime();
        try (FileQueueSocket queue = new FileQueueSocket(args[0], port, args[2], args[3])) {
            queue.open();
            System.out.printf("LINK OK: %s:%d, queue=%s/%s, mode=%s, count=%d%n",
                    args[0], port, args[2], args[3], mode, count);

            while (completed < count) {
                if ("ENQU".equals(mode)) {
                    long seq = completed + 1;
                    JSONObject payload = new JSONObject();
                    payload.put("MSG_KIND", "S");
                    payload.put("SEQ", Long.toString(seq));
                    payload.put("MESSAGE", "Hello FileQueue! SEQ=" + seq);
                    FileQueueSocket.Result result =
                            queue.writeRetryOnFull(payload.toString(), RETRY_DELAY_MS);
                    if (!result.isOk()) {
                        throw new IllegalStateException("ENQU failed at SEQ=" + seq
                                + ": " + result.serverResult());
                    }
                    completed++;
                    if (shouldLog(completed, count)) {
                        System.out.printf("ENQU OK: SEQ=%d (%d/%d)%n", seq, completed, count);
                    }
                } else {
                    FileQueueSocket.Result result = queue.read();
                    if (result.status() == FileQueueSocket.Status.EMPTY) {
                        Thread.sleep(RETRY_DELAY_MS);
                        continue;
                    }
                    if (!result.isOk()) {
                        throw new IllegalStateException("DEQU failed: " + result.serverResult());
                    }
                    completed++;
                    if (shouldLog(completed, count)) {
                        System.out.printf("DEQU OK: (%d/%d) MESSAGE=%s%n",
                                completed, count, result.message());
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted; completed=" + completed);
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Stopped; confirmed completed=" + completed + ", error=" + e);
            System.err.println("On socket I/O failure, the last operation's outcome may be uncertain.");
            System.exit(1);
        }

        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        System.out.printf("DONE: mode=%s, completed=%d, elapsed=%.3fs, avg TPS=%.2f%n",
                mode, completed, seconds, seconds > 0 ? completed / seconds : 0.0);
    }

    private static boolean shouldLog(long completed, long count) {
        return completed <= 10 || completed % 1000 == 0 || completed == count;
    }

    private static void usage() {
        System.err.println("Usage: java -cp 'out:lib/*' FileQueueSocketExample_loop HOST PORT QUEUE_PATH QUEUE_NAME MODE COUNT");
        System.err.println();
        System.err.println("MODE:");
        System.err.println("  ENQU    Enqueue COUNT JSON messages with SEQ=1..COUNT");
        System.err.println("  DEQU    Dequeue COUNT messages (wait 1 second when EMPTY)");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  java -cp 'out:lib/*' FileQueueSocketExample_loop localhost 9000 /sendq1/enmq KT_SM_ONL ENQU 10000");
        System.err.println("  java -cp 'out:lib/*' FileQueueSocketExample_loop localhost 9000 /sendq1/enmq KT_SM_ONL DEQU 10000");
    }
}
