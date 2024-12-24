// Socket TCP
import java.io.*;
import java.net.*;

// FileQueue 
import com.clang.fq.*;

// XML configuration
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

// for your standard keyboard input
import java.util.Scanner;

// 구성 값을 저장할 클래스를 정의합니다.
class Config {
    String logLevel;
    String logFilePath;
    String queuePath;
    String queueName;
    int userWorkingTimeForSimulate;
	int		ackPort;
	int		resultPort;

    // 생성자
    public Config(String logLevel, String logFilePath, String queuePath, String queueName,
                  int userWorkingTimeForSimulate, int ackPort, int resultPort) {
        this.logLevel = logLevel;
        this.logFilePath = logFilePath;
        this.queuePath = queuePath;
        this.queueName = queueName;
        this.userWorkingTimeForSimulate = userWorkingTimeForSimulate;
        this.ackPort = ackPort;
        this.resultPort = resultPort;
    }
}

// 통신사 Simulator TCP Server
public class ClangSimulatorDualFunctionTcpServer {

    public static void main(String[] args) {
        int echoPort = 5001;
        int messagePort = 5002;

		System.out.println("args.length=" + args.length);
		for(int i = 0; i< args.length; i++) {
			System.out.println(String.format("Command Line Argument %d is %s", i, args[i]));
		}

		if( args.length != 1) {
			System.out.println("Usage: $ java ClangSimulatorDualFunctionTcpServer [xml_config_file] <enter>");
			System.out.println("Usage: $ java ClangSimulatorDualFunctionTcpServer CoAgentConf.xml <enter>");
			return;
		}

		// 입력받은 경로로 구성 파일을 읽습니다.
        Config config = readConfigFromFile(args[0]);

		// 모든 구성 값을 출력합니다.
        if (config != null) {
			printConfig( config );
			Scanner scanner = new Scanner(System.in);
        	System.out.print("Enter 'y' to continue: ");
        	String userInput = scanner.nextLine();

			// 사용자 입력이 'y'인지 확인
			if (userInput.equalsIgnoreCase("y")) {
				System.out.println("You entered 'y'. Continuing...");
			} else {
				System.out.println("You did not enter 'y'. Exiting...");
				return;
			}
			scanner.close();
        }


        Thread echoServerThread = new Thread(() -> startEchoServer(config));
        Thread messageServerThread = new Thread(() -> startMessageSenderServer(config));

        echoServerThread.start();
        messageServerThread.start();
    }

    // 에코 기능을 수행하는 서버
    public static void startEchoServer(Config config) {
		// File Queue Open
		int rc;
		int queueIndex = 0;
		FileQueueJNI ackQueue = new FileQueueJNI( queueIndex, "/tmp/ackServer.log", 4, config.queuePath, config.queueName);
		if(  (rc = ackQueue.open()) < 0 ) {
			System.out.println("open failed: " + "qPath="+ config.queuePath + ", qName=" + config.queueName + ", rc=" + rc);
			return;
		}

        try (ServerSocket serverSocket = new ServerSocket(config.ackPort)) {
            System.out.println("Echo Server listening on port " + config.ackPort);
            System.out.println("Queue info:" + config.queuePath + ", " +config.queueName);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleEchoClient(clientSocket, ackQueue)).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + config.ackPort);
            e.printStackTrace();
        }
    }

    public static void handleEchoClient(Socket clientSocket, FileQueueJNI ackQueue) {
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
    public static void startMessageSenderServer(Config config) {
		// File Queue Open
		int rc;
		int queueIndex = 1;
		FileQueueJNI resultQueue = new FileQueueJNI( queueIndex, "/tmp/resultServer.log", 4, config.queuePath, config.queueName);
		if(  (rc = resultQueue.open()) < 0 ) {
			System.out.println("open failed: " + "qPath="+ config.queuePath + ", qName=" + config.queueName + ", rc=" + rc);
			return;
		}

        try (ServerSocket serverSocket = new ServerSocket(config.resultPort)) {
            System.out.println("Message Sender Server listening on port " + config.resultPort);
            System.out.println("Queue info:" + config.queuePath + ", " +config.queueName);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleMessageClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + config.resultPort);
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

	// Loading configuration file
	public static Config readConfigFromFile(String filePath) {
        Config config = null;

        try {
            // XML 파일을 파싱하여 Document 객체를 생성합니다.
            File inputFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            // 각 설정 값을 읽어옵니다.
            String logLevel = doc.getElementsByTagName("logLevel").item(0).getTextContent();
            String logFilePath = doc.getElementsByTagName("logFilePath").item(0).getTextContent();
            String queuePath = doc.getElementsByTagName("queuePath").item(0).getTextContent();
            String queueName = doc.getElementsByTagName("queueName").item(0).getTextContent();

            String userWorkingTime_str = doc.getElementsByTagName("userWorkingTimeForSimulate").item(0).getTextContent();
			int userWorkingTimeForSimulate = Integer.parseInt(userWorkingTime_str); 

            String ackPort_str = doc.getElementsByTagName("ackPort").item(0).getTextContent();
			int ackPort = Integer.parseInt(ackPort_str); 
            String resultPort_str = doc.getElementsByTagName("resultPort").item(0).getTextContent();
			int resultPort = Integer.parseInt(resultPort_str); 


            // Config 객체를 생성합니다.
            config = new Config(logLevel, logFilePath, queuePath, queueName, userWorkingTimeForSimulate, ackPort, resultPort );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return config;  // Config 객체 반환
    }
	
	// print Config
	private static void printConfig( Config config) {
		System.out.println("---------- < configuration begin >--------------- ");
		System.out.println("\t- Log Level: " + config.logLevel);
		System.out.println("\t- Log File Path: " + config.logFilePath);
		System.out.println("\t- queue Path: " + config.queuePath);
		System.out.println("\t- queue Name: " + config.queueName);
		System.out.println("\t- User Working Time for Simulating : " + config.userWorkingTimeForSimulate);
		System.out.println("\t- ack PORT for Simulating : " + config.ackPort);
		System.out.println("\t- result PORT for Simulating : " + config.resultPort);
		System.out.println("---------- < configuration end >--------------- ");
	}
}
