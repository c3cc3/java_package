/*
import java.io.*;
import java.net.*;

public class SocketServer {
    
    public static void main(String[] args) {
        int port = 8080; // 사용할 포트 번호
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Waiting for clients...(8080 port)");

            // 클라이언트의 연결을 기다리는 무한 루프
            while (true) {
                Socket clientSocket = serverSocket.accept(); // 클라이언트 연결 수락
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // 새로운 스레드를 생성하여 클라이언트 처리
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// 클라이언트 요청을 처리하는 스레드 클래스
class ClientHandler extends Thread {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // 클라이언트의 메시지를 읽고 응답을 보냄
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received from client: " + inputLine);
                out.println("Echo: " + inputLine); // 클라이언트에게 에코 응답
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close(); // 소켓 닫기
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
*/

import java.io.*;
import java.net.*;

public class SocketServer {

    public static void main(String[] args) {
        int port = 8080; // 사용할 포트 번호
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Waiting for clients...");

            // 클라이언트의 연결을 기다리는 무한 루프
            while (true) {
                Socket clientSocket = serverSocket.accept(); // 클라이언트 연결 수락
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // 새로운 스레드를 생성하여 클라이언트 처리
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// 클라이언트 요청을 처리하는 스레드 클래스
class ClientHandler extends Thread {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
		// 현재 스레드 얻기
        Thread currentThread = Thread.currentThread();
        // 스레드 ID 출력
        System.out.println("Current Thread ID: " + currentThread.getId());

        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            while (true) {
                // 메시지 길이 읽기
                int length = in.readInt();
                if (length < 0) {
                    break; // Negative length indicates the end of the stream
                }

                // 메시지 읽기
                byte[] data = new byte[length];
                in.readFully(data, 0, length);
                String message = new String(data);

                System.out.println("Client Message: " + message);
				

                // 클라이언트로 에코 응답 전송
				byte[] sendData = message.getBytes();
				out.writeInt(sendData.length);
				out.write(sendData);

/*
                int dataLength = sendData.length;

                // 길이(int)를 바이트 배열로 변환
                byte[] lengthBytes = new byte[4];
                lengthBytes[0] = (byte) (dataLength >>> 24); // 최상위 바이트
                lengthBytes[1] = (byte) (dataLength >>> 16);
                lengthBytes[2] = (byte) (dataLength >>> 8);
                lengthBytes[3] = (byte) dataLength; // 최하위 바이트

                // 길이와 본문을 결합한 새로운 배열 생성
                byte[] send_message = new byte[4 + sendData.length]; // 4바이트 길이 + 메시지 본문 길이
                System.arraycopy(lengthBytes, 0, send_message, 0, 4); // 길이 복사
                System.arraycopy(sendData, 0, send_message, 4, data.length); // 본문 복사

                out.write(send_message);
*/
            }
        } catch (IOException e) {
            System.out.println("IOException");
            e.printStackTrace();
        } finally {
            System.out.println("finally");
            try {
                socket.close(); // 소켓 닫기
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
