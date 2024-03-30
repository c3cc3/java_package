import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SharedBlockingQueueExample {
    static class ProducerThread extends Thread {
        private final BlockingQueue<String> sharedQueue;

        public ProducerThread(BlockingQueue<String> sharedQueue) {
            this.sharedQueue = sharedQueue;
        }

        public void run() {
            try {
                for (int i = 0; i < 10; i++) {
                    String message = "Message " + i;
                    sharedQueue.put(message);
                    System.out.println("Produced: " + message);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    static class ConsumerThread extends Thread {
        private final BlockingQueue<String> sharedQueue;

        public ConsumerThread(BlockingQueue<String> sharedQueue) {
            this.sharedQueue = sharedQueue;
        }

        public void run() {
            try {
                for (int i = 0; i < 10; i++) {
                    String message = sharedQueue.take();
                    System.out.println("Consumed: " + message);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        BlockingQueue<String> sharedQueue = new ArrayBlockingQueue<>(10);

        ProducerThread producerThread = new ProducerThread(sharedQueue);
        ConsumerThread consumerThread = new ConsumerThread(sharedQueue);

        producerThread.start();
        consumerThread.start();
    }
}

