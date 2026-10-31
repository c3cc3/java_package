import com.clang.socket.FileQueueSocket;
import org.json.JSONObject;

public class FileQueueSocketExample {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: java FileQueueSocketExample HOST PORT QUEUE_PATH QUEUE_NAME");
            System.exit(2);
        }
        try (FileQueueSocket queue = new FileQueueSocket(
                args[0], Integer.parseInt(args[1]), args[2], args[3])) {
            queue.open();

            JSONObject payload = new JSONObject();
            payload.put("MSG_KIND", "S");
            payload.put("SEQ", "100");
            payload.put("MESSAGE", "안녕하세요, FileQueue!");

            FileQueueSocket.Result sent = queue.writeRetryOnFull(payload.toString(), 1000);
            if (!sent.isOk()) {
                throw new IllegalStateException("ENQU failed: " + sent.serverResult());
            }
            System.out.println("ENQU OK");

            FileQueueSocket.Result received = queue.read();
            if (received.status() == FileQueueSocket.Status.OK) {
                JSONObject message = new JSONObject(received.message());
                System.out.println("DEQU OK: " + message.toString(2));
            } else if (received.status() == FileQueueSocket.Status.EMPTY) {
                System.out.println("Queue is EMPTY");
            } else {
                System.out.println("DEQU result: " + received.serverResult());
            }
        }
    }
}
