import java.io.*;
import java.net.*;

public class ClangSimulatorDualFunctionTcpServer {

    public static void main(String[] args) {
        int echoPort = 5001;
        int messagePort = 5002;

        Thread echoServerThread = new Thread(() -> startEchoServer(echoPort));
        Thread messageServerThread = new Thread(() -> startMessageSenderServer(messagePort));

        echoServerThread.start();
        messageServerThread.start();
    }

    // 에코 기능을 수행하는 서버
    public static void startEchoServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Echo Server listening on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleEchoClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + port);
            e.printStackTrace();
        }
    }

    public static void handleEchoClient(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            String receivedMessage;
            while ((receivedMessage = reader.readLine()) != null) {
                System.out.println("Received (Echo): " + receivedMessage);
                writer.println(receivedMessage);  // Echo back the received message
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Echo connection closed.");
        }
    }

    // 메시지를 먼저 보내고 응답을 받는 서버
    public static void startMessageSenderServer(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Message Sender Server listening on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleMessageClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + port);
            e.printStackTrace();
        }
    }

    public static void handleMessageClient(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            // 먼저 메시지를 보내고 클라이언트의 응답을 기다립니다.
            String initialMessage = "Hello from server!";
            System.out.println("Sending: " + initialMessage);
            writer.println(initialMessage);
            
            String responseMessage;
            if ((responseMessage = reader.readLine()) != null) {
                System.out.println("Received in response: " + responseMessage);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Message sender connection closed.");
        }
    }
}
