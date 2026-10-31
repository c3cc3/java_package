import java.nio.charset.StandardCharsets;

public class FQueueTest {
    public static void main(String[] args) {
        FQueue fq = new FQueue();

        String logFile = "/logs/fq/fqueue_jni_test.log";
        String queuePath = "/sendq1/enmq";
        String queueName = "TST";

        int rc = fq.openLogger(logFile, 2);
        System.out.println("openLogger rc=" + rc);

        if (rc <= 0) {
            System.err.println("Logger open failed.");
            return;
        }

        int queueId = fq.openQueue(queuePath, queueName);
        System.out.println("openQueue id=" + queueId);

        if (queueId < 0) {
            System.err.println("Queue open failed.");
            System.out.println("closeLogger rc=" + fq.closeLogger());
            return;
        }

        try {
            String text = "JNI_TEST_" + System.currentTimeMillis();
            byte[] data = text.getBytes(StandardCharsets.UTF_8);

            System.out.println("enqueue data=" + text);
            System.out.println("enqueue length=" + data.length);

            FQueue.EnqueueResult result =
                fq.enQueue(queueId, data, FQueue.EN_NORMAL_MODE);

            if (result == null) {
                System.err.println("enQueue returned null");
            } else { // enQ success
                System.out.println("enQueue rc=" + result.rc);
                System.out.println("enQueue seq=" + result.seq);
                System.out.println("enQueue runTime=" + result.runTime);

				FQueue.FQueueMessage msg = fq.deQueueXA(queueId);

				if (msg == null) {
					System.out.println("deQueueXA: no message or error");
				} else {
					// deQ Test
					String received = new String(
						msg.data, StandardCharsets.UTF_8
					);

					System.out.println("deQueueXA seq=" + msg.seq);
					System.out.println("deQueueXA runTime=" + msg.runTime);
					System.out.println("deQueueXA data=" + received);
					System.out.println("deQueueXA length=" + msg.data.length);

					int user_flag = 1;

					if( user_flag == 1 ) {
						rc = fq.commit(msg);
						System.out.println("commit rc=" + rc);
					} else {
						System.out.println("Unexpected message; cancelling.");
						rc = fq.cancel(msg);
						System.out.println("cancel rc=" + rc);
					}
				}
            }
        } finally {
            System.out.println("closeQueue rc=" + fq.closeQueue(queueId));
            System.out.println("closeLogger rc=" + fq.closeLogger());
        }
    }
}
