import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class DualServerClient {
    private static final String SERVER1_ADDRESS = "127.0.0.1";
    private static final int SERVER1_PORT = 12345;

    private static final String SERVER2_ADDRESS = "127.0.0.1";
    private static final int SERVER2_PORT = 54321;

    private static final int RETRY_INTERVAL = 5000; // 3초 재시도 간격

    private static volatile boolean reconnect = false; // 동기화된 플래그 변수

    public static void main(String[] args) {
        Thread server1Thread = new Thread(() -> connectToServer(0, SERVER1_ADDRESS, SERVER1_PORT));
        Thread server2Thread = new Thread(() -> connectToServer(1, SERVER2_ADDRESS, SERVER2_PORT));

        server1Thread.start();
        server2Thread.start();
    }

    private static void connectToServer(int threadId, String serverAddress, int serverPort) {
        while (true) {
            System.out.println(threadId + ": while(true) 재진입:  재연결 준비 중... port=" + serverPort);

			try (Socket socket = new Socket(serverAddress, serverPort)) {
                System.out.println(threadId + " 서버 연결 성공: " + serverAddress + ":" + serverPort);
				reconnect = false;

                // 서버와 통신
                try (DataInputStream in = new DataInputStream(socket.getInputStream());
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                	while (!Thread.currentThread().isInterrupted()) {
						
                        // 예제 메시지 전송
                        String message = threadId + ": Hello from " + serverAddress +":"+ serverPort ;
                        out.writeInt(message.length());
                        out.write(message.getBytes());

                        // 서버 응답 수신
                        int length = in.readInt();
                        byte[] data = new byte[length];
                        in.readFully(data, 0, length);
                        String response = new String(data);
                        System.out.println(threadId + " 서버 응답: " + response);

                        Thread.sleep(1000); // 예제: 1초 간격으로 메시지 전송
                	}
                }
           	} catch (IOException | InterruptedException e) {
                System.out.println(threadId + " 서버 연결 실패: " + serverAddress + ":" + serverPort);
                e.printStackTrace();
           	}
			finally {
				System.out.println(threadId + "무한에서 빠져나옮.");

				try {
					System.out.println(threadId + " 재연결 대기(SLEEP)중.....");
					Thread.sleep(RETRY_INTERVAL);
					System.out.println(threadId + ":" + serverPort +" 로 재열결 시도하러 감...");
				} catch (InterruptedException e) {
					System.out.println(threadId + " 재연결 대기(SLEEP)중 인터럽트 발생.");
					break;
				}
			}
        } // while(true);
    }

    private static synchronized void reconnectAll(int issuer) {
        reconnect = true;
        System.out.println(issuer + " 가 모든 스레드 재연결 플래그 설정.");
    }
}

