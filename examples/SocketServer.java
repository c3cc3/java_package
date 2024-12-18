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
