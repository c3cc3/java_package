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

// json Object
import org.json.JSONObject;


// JSON library: jackson
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.util.HashMap;
import java.util.Map; // mapper

// Get current time // yyyymmddHHMMSS
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


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

				// enQueue 
				try {
					int write_rc = ackQueue.write( receivedMessage );

					if( write_rc < 0 ) {
						System.err.println("Write failed: " + ackQueue.path + "," + ackQueue.qname + "," + " rc: " + write_rc);
						ackQueue.close();
						return;
					}
					else if( write_rc == 0 ) { // queue is full
						System.out.println("full: " + ackQueue.path + "," + ackQueue.qname + "," + " rc: " + write_rc);
						try {
							Thread.sleep(10); // Pause for 1 second (1000)
						}
						catch(InterruptedException ex) {
							Thread.currentThread().interrupt();
						}
						continue;
					}
					else {
						long out_seq = ackQueue.get_out_seq();
						long out_run_time = ackQueue.get_out_run_time();

						System.out.println("(echoClient)->receive thread:"+ "enQ success: " + "seq=" + out_seq + "," + "rc:" + write_rc);
						try {
							Thread.sleep(100); // Pause for 1 second (1000)
						}
						catch(InterruptedException ex) {
								Thread.currentThread().interrupt();
						}
						continue;
					}
				} catch (Exception e) {
					System.err.println("(echoClient)"+ "resultQueue.write() 오류: " + e.getMessage());
				}

				// parsing json message and get HISTORY_KEY
				boolean healthCheckFlag = false;
				String returnSequence = null;

				boolean tf=JsonParserAndVerify (receivedMessage, healthCheckFlag, returnSequence );
				if( tf == false && returnSequence != null ) {
					System.err.println("(echoClient)" + "JdonParerAndVerify() interrupted.");
					return;
				}
				if( healthCheckFlag == true) {
					System.err.println("(echoClient)" + "Health checking message.");
					return;
				}

				// Make a new ACK json message
				JSONObject ackJson = new JSONObject();

				ackJson.put("HISTORY_KEY", returnSequence);
				ackJson.put("RESP_KIND", 30);
				ackJson.put("STATUS_CODE", "1");
				ackJson.put("RESULT_CODE", "0000");
				ackJson.put("RCS_MESSAGE", "success");

				String currentDateTime = getCurrentTime(); // 현재 시간을 가져오는 방법이 구현돼 있어야 함
				ackJson.put("RCS_SND_DTM", currentDateTime);
				ackJson.put("RCS_RCCP_DTM", currentDateTime);
				ackJson.put("AGENT_CODE", "MY");

				String jsonString = ackJson.toString();
				// logger.debug("Generated JSON: " + jsonString);
				
                writer.println(jsonString);  
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
                new Thread(() -> handleMessageClient(clientSocket, resultQueue)).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + config.resultPort);
            e.printStackTrace();
        }
    }

    public static void handleMessageClient(Socket clientSocket, FileQueueJNI resultQueue) {
		long threadId = Thread.currentThread().getId();
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {

			// deQueue(while)
			try {
				// 무한반복
				while (true) {
					int read_rc = 0;

					read_rc = resultQueue.readXA(); // XA read 
					if( read_rc < 0 ) {
						System.err.println("("+threadId+")"+ "readXA failed: " + resultQueue.path + "," + resultQueue.qname + "," + " rc: " + read_rc);
						break;
					}

					if( read_rc == 0 ) {
						System.out.println("("+threadId+")"+ "There is no data(empty) : " + resultQueue.path + "," + resultQueue.qname + "," + " rc: " + read_rc);
						Thread.sleep(1000); // Pause for 1 second
						continue;
					}

					String data = resultQueue.get_out_msg();
					long out_seq = resultQueue.get_out_seq();
					String out_unlink_filename = resultQueue.get_out_unlink_filename();
					long out_run_time = resultQueue.get_out_run_time();

					writeMessageToFile(threadId, data); // 파일에 메시지 쓰기
					System.out.println("("+threadId+")"+ "backup file writing success");
					resultQueue.commitXA();
					System.out.println("("+threadId+")"+ "normal data: commitXA() sucesss seq : " + out_seq);

					// Make a json object with queue data.
					// takeout SEQ from json.
					// Make a json result message.
					// send resultMessage to client.

					// 먼저 메시지를 보내고 클라이언트의 응답을 기다립니다.
					String initialMessage = "Hello from server!";
					System.out.println("Sending: " + initialMessage);
					writer.println(initialMessage);
					
					String responseMessage;
					if ((responseMessage = reader.readLine()) != null) {
						System.out.println("Received in response: " + responseMessage);
					}
					// 응답이 yes 인지 확인한다.
					boolean your_job_result = true;
					
					// OK 가 들어오면 filequeue commit 을 수행한다.

					if( your_job_result == true) { // normal data
						deleteFile(threadId); // 파일 삭제
						System.out.println("("+threadId+")"+ "deleteFile() sucesss seq : " + out_seq);
					}
					else { // abnormal data
						resultQueue.cancelXA();
						System.out.println("("+threadId+")"+ "abnormal data: cancelXA() sucesss seq : " + out_seq);
						break;
					}
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				System.err.println("Thread " + threadId + " interrupted.");
				
			} finally {
				resultQueue.close();
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
	
	// 현재 시간을 "yyyyMMddHHmmss" 형식으로 문자열로 반환
    public static String getCurrentTime() {
        // 현재 날짜 및 시간 얻기
        LocalDateTime now = LocalDateTime.now();
        
        // 원하는 형식으로 포맷터 생성
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        
        // 포맷팅된 문자열 반환
        return now.format(formatter);
    }

	private static boolean JsonParserAndVerify ( String jsonString, boolean hcFlag, String returnSequence ) {
        // JSON 문자열 입력
        // String jsonString = "{\"name\":\"John\", \"age\":30, \"city\":\"New York\"}";

        // ObjectMapper 인스턴스를 생성합니다.
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // JSON 문자열을 JsonNode로 파싱합니다.
            JsonNode jsonNode = objectMapper.readTree(jsonString);

            // JSON 검증: 필수 필드가 있는지 확인
            if (jsonNode.hasNonNull("SEQ") 
				&& jsonNode.hasNonNull("CHANNEL")
				&& jsonNode.hasNonNull("MSG_TYPE")
				&& jsonNode.hasNonNull("RECEIVER")
				&& jsonNode.hasNonNull("SENDER")
				&& jsonNode.hasNonNull("BRAND_ID")
				&& jsonNode.hasNonNull("BRAND_KEY")
				&& jsonNode.hasNonNull("MESSAGEBASE_ID")
				// && jsonNode.hasNonNull("HEADER")
				// && jsonNode.hasNonNull("FOOTER")
				// && jsonNode.hasNonNull("COPYALLOWED")
				&& jsonNode.hasNonNull("MESSAGE")
				// && jsonNode.hasNonNull("BOTTONS")
			) {
                // 필드들이 존재하므로 데이터를 읽습니다.
                String seq = jsonNode.get("SEQ").asText();
				returnSequence  = seq; // sequence 값을 인자로 돌려준다.

                String channel = jsonNode.get("CHANNEL").asText();
                String msgType = jsonNode.get("MSG_TYPE").asText();
                String receiver = jsonNode.get("RECEIVER").asText();
                String sender = jsonNode.get("SENDER").asText();
                String brandId = jsonNode.get("BRAND_ID").asText();
                String brandKey = jsonNode.get("BRAND_KEY").asText();
                String messageBaseId = jsonNode.get("MESSAGEBASE_ID").asText();
                String message = jsonNode.get("MESSAGE").asText();

                // String header = jsonNode.get("HEADER").asText();
                // String footer = jsonNode.get("FOOTER").asText();
                // String copyAllowed = jsonNode.get("COPYALLOWED").asText();

                // String bottons = jsonNode.get("BOTTONS").asText();
                // int channel = jsonNode.get("CHANNEL").asInt();
                // String city = jsonNode.get("city").asText();
				// boolean isActive = jsonNode.get("isActive").asBoolean();

            	System.out.println("-------------------------- JSON Check OK ------------------------");
            	System.out.println("\t-(echoClient)" + "SEQ: " + seq);
            	System.out.println("\t-(echoClient)" + "CHANNEL: " + channel);
            	System.out.println("\t-(echoClient)" + "MSG_TYPE: " + msgType);
            	System.out.println("\t-(echoClient)" + "RECEIVER: " + receiver);
            	System.out.println("\t-(echoClient)" + "SENDER: " + sender);
            	System.out.println("\t-(echoClient)" + "BRAND_ID: " + brandId);
            	System.out.println("\t-(echoClient)" + "BRAND_KEY: " + brandKey);
            	System.out.println("\t-(echoClient)" + "MESSAGEBASE_ID: " + messageBaseId);
            	System.out.println("\t-(echoClient)" + "MESSAGE: " + message);

				if (channel.equalsIgnoreCase("HC")) { // is health checking.
					hcFlag = true;
				}
				return true;
            } else {
            	System.out.println("--------------------------JSON Check ERROR------------------------");
            	System.err.println("(echoClient)" + "Invalid JSON: missing required fields");
				return false;
            }
        } catch (Exception e) {
            // JSON 파싱 중 예외가 발생했을 경우
            System.err.println("Error verifying or parsing JSON: " + e.getMessage());
			return false;
        }
    }
	
    // 스레드 ID에 따른 파일에 메시지 쓰기
    private static void writeMessageToFile(long threadId, String message) {
        String fileName = "thread_" + threadId + ".txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write(message);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("(" + threadId + ")" + "Error writing to file: " + e.getMessage());
        }
    }
    // 스레드 ID에 따른 파일 삭제
    private static void deleteFile(long threadId) {
        String fileName = "thread_" + threadId + ".txt";
        File file = new File(fileName);
        if (file.delete()) {
            System.out.println("(" + threadId + ")" + "Deleted file: " + fileName);
        } else {
            System.err.println("(" + threadId + ")" + "Failed to delete file: " + fileName);
        }
		return;
    }
}
