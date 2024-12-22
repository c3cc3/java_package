/* Line Reader */
/*
import java.io.*;
import java.net.*;

public class EchoClient {
    public static void main(String[] args) {
        // String hostname = "localhost"; // 서버의 호스트명
        String hostname = "127.0.0.1"; // 서버의 호스트명
        int port = 8080; // 서버의 포트 번호

        try (Socket socket = new Socket(hostname, port); // 서버에 연결
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            String userInputLine;

            System.out.println("Connected to the echo server. Type messages (type 'exit' to quit):");

            // 사용자 입력을 반복하여 서버에 전송
            while (true) {
                System.out.print("> "); // 프로프트 출력
                userInputLine = userInput.readLine(); // 사용자 입력 읽기

                if ("exit".equalsIgnoreCase(userInputLine)) {
                    System.out.println("Exiting...");
                    break; // 'exit' 입력 시 클라이언트 종료
                }

                out.println(userInputLine); // 서버에 메시지 전송

                // 서버로부터 응답 받기
                String serverResponse = in.readLine();
                System.out.println("Server response: " + serverResponse);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
*/

import java.io.*;
import java.net.*;

public class EchoClient {
    public static void main(String[] args) {
        String hostname = "localhost"; // 서버의 호스트명
        int port = 8080; // 서버의 포트 번호

        try (Socket socket = new Socket(hostname, port); // 서버에 연결
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream());
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            String userInputLine;

            System.out.println("Connected to the echo server. Type messages (type 'exit' to quit):");

            // 사용자 입력을 반복하여 서버에 전송
            while (true) {
                System.out.print("> "); // 프로프트 출력
                userInputLine = userInput.readLine(); // 사용자 입력 읽기

                if ("exit".equalsIgnoreCase(userInputLine)) {
                    System.out.println("Exiting...");
                    break; // 'exit' 입력 시 클라이언트 종료
                }

                // 메시지를 바이트 배열로 변환 후 길이와 함께 전송
                byte[] data = userInputLine.getBytes();
                out.writeInt(data.length);
                out.write(data);

                // 서버로부터 메시지 길이 및 메시지 읽기
                int length = in.readInt();
                if (length > 0) {
                    byte[] receivedData = new byte[length];
                    in.readFully(receivedData, 0, length);
                    String receivedMessage = new String(receivedData);
                    System.out.println("Server response: " + receivedMessage);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
