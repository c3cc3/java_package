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

// threadIndex Manager
// Alternately, Java 8부터 지원하는 방식으로 AtomicInteger를 사용
import java.util.concurrent.atomic.AtomicInteger;


// 구성 값을 저장할 클래스를 정의합니다.
class Config {
    String logLevel;
    String logFilePath;
    String interThreadComQueuePath;
    String interThreadComQueueName;
    int userWorkingTimeForSimulate;
	int		ackPort;
	int		resultPort;

    // 생성자
    public Config(String logLevel, String logFilePath, String interThreadComQueuePath, String interThreadComQueueName,
                  int userWorkingTimeForSimulate, int ackPort, int resultPort) {
        this.logLevel = logLevel;
        this.logFilePath = logFilePath;
        this.interThreadComQueuePath = interThreadComQueuePath;
        this.interThreadComQueueName = interThreadComQueueName;
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

		// File Queue Open
		int rc;
		int queueIndex = 0;

		// 이 값은 소켓에서 exception이 발생되어 ackThread 가 종료되었을 때, resultThread 도 
		// 같이 종료하기 위함이다.
		AtomicInteger stopFlag = new AtomicInteger(0);

		FileQueueJNI interThreadComQueue = new FileQueueJNI( queueIndex, "/tmp/receiveRequestServer.log", 4, config.interThreadComQueuePath, config.interThreadComQueueName);

		if(  (rc = interThreadComQueue.open()) < 0 ) {
			System.out.println("open failed: " + "qPath="+ config.interThreadComQueuePath + ", qName=" + config.interThreadComQueueName + ", rc=" + rc);
			return;
		}

        Thread requestDeliveryServerThread = new Thread(() -> requestDeliveryListenServer(stopFlag, config, interThreadComQueue));
        Thread resultReturnServerThread = new Thread(() -> resultReturnListenServer(stopFlag, config, interThreadComQueue));

        requestDeliveryServerThread.start();
        resultReturnServerThread.start();
    }

    // 발송요청을 받아 큐에 넣고 ACK를 답하는 서버
    public static void requestDeliveryListenServer(AtomicInteger stopFlag, Config config, FileQueueJNI interThreadComQueue) {
		try (ServerSocket serverSocket = new ServerSocket(config.ackPort)) {
			System.out.println("Receive Request Server listening on port " + config.ackPort);
			System.out.println("interThreadComQueue info:" + config.interThreadComQueuePath + ", " +config.interThreadComQueueName);


			// 클라이언트 연결을 기다리며 무한 루프
			while (true) {
				Socket clientSocket = serverSocket.accept(); // 클라이언트 연결 수락
				new Thread(() -> handleAckClientThread(stopFlag, clientSocket, config, interThreadComQueue)).start();
			}
		} catch (IOException e) {
			System.err.println("Could not listen on port " + config.ackPort);
			e.printStackTrace();
		}
    }

    public static void handleAckClientThread(AtomicInteger stopFlag, Socket clientSocket, Config config, FileQueueJNI interThreadComQueue) {

        try (
            DataInputStream reader = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream writer = new DataOutputStream(clientSocket.getOutputStream());
        ) {
			while(true) { // 데이터 수신을 무한 반복.
				// 길이헤더 수신
				int responseLength = reader.readInt();
				if( responseLength <= 0 ) {
					System.err.println("reader.readInt() error.");
					return;
				}
				// 수신 byte[] 버퍼 생성
				byte[] receiveData = new byte[responseLength];
				reader.readFully( receiveData, 0, responseLength);
				String requestData = new String(receiveData);
				System.out.println("Received (request from client): " + requestData);

				// enQueue 
				try {
					while(true) {
						int write_rc = interThreadComQueue.write( requestData );

						if( write_rc < 0 ) {
							System.err.println("Write failed: " + interThreadComQueue.path + "," + interThreadComQueue.qname + "," + " rc: " + write_rc);
							// interThreadComQueue.close();
							return;
						}
						else if( write_rc == 0 ) { // queue is full
							System.out.println("full: " + interThreadComQueue.path + "," + interThreadComQueue.qname + "," + " rc: " + write_rc);
							try {
								Thread.sleep(1000); // Pause for 1 second (1000)
							}
							catch(InterruptedException ex) {
								Thread.currentThread().interrupt();
							}
							continue;
						}
						else {
							long out_seq = interThreadComQueue.get_out_seq();
							long out_run_time = interThreadComQueue.get_out_run_time();

							System.out.println("(requestClient)->receive thread:"+ "enQ success: " + "seq=" + out_seq + "," + "rc:" + write_rc);
/*
							try {
								Thread.sleep(100); // Pause for 1 second (1000)
							}
							catch(InterruptedException ex) {
									Thread.currentThread().interrupt();
							}
*/
							break;
						}
					}
				} catch (Exception e) {
					System.err.println("(echoClient)"+ "resultQueue.write() 오류: " + e.getMessage());
				}

				// parsing json message and get HISTORY_KEY

				StringBuilder returnHistoryKey = new StringBuilder();
				StringBuilder returnReceiver = new StringBuilder();
				StringBuilder returnChannel = new StringBuilder();
				StringBuilder returnMsgType = new StringBuilder();

				boolean tf =  JsonParserAndVerify (requestData, returnHistoryKey, returnReceiver, returnChannel, returnMsgType );

				System.out.println("JsonParserAndVerify() OK. tf=" + tf);

				// Make a new ACK json message
				JSONObject ackJson = new JSONObject();

				ackJson.put("HISTORY_KEY", returnHistoryKey.toString());
				ackJson.put("RESP_KIND", "ACK");
				ackJson.put("STATUS_CODE", "1");
				ackJson.put("RESULT_CODE", "0000");
				ackJson.put("RCS_MESSAGE", "success");

				String currentDateTime = getCurrentTime(); // 현재 시간을 가져오는 방법이 구현돼 있어야 함
				ackJson.put("RCS_SND_DTM", currentDateTime);
				ackJson.put("RCS_RCCP_DTM", currentDateTime);
				ackJson.put("AGENT_CODE", "MY");

				String jsonString = ackJson.toString();
				
				// 메시지를 바이트 배열로 변환 후 길이와 함께 전송
				try {
					byte[] data = jsonString.getBytes();
					writer.writeInt( data.length ); // 길이 Prefix header 전송
					writer.write( data ); // 서버에 메시지 전송
					System.out.println("(receiveRequestServer):" + "ACK OK.");
				} catch( IOException e) {
					System.err.println("(receiveRequestServer)" + "writer.write:" + e.getMessage());
					e.printStackTrace();

					// resultThread 를 종료시키기 위해 사용된다.
					stopFlag.getAndIncrement(); 
				}
			}
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("finally: receiveRequestServer connection closed.");
        }
    }

    // 메시지를 먼저 보내고 응답을 받는 서버
    public static void resultReturnListenServer(AtomicInteger stopFlag, Config config, FileQueueJNI interThreadComQueue) {

        try (ServerSocket serverSocket = new ServerSocket(config.resultPort)) {
            System.out.println("Message Sender Server listening on port " + config.resultPort);
            System.out.println("interThreadComQueue info:" + config.interThreadComQueuePath + ", " +config.interThreadComQueueName);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleMessageClient(stopFlag, clientSocket, interThreadComQueue)).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + config.resultPort);
            e.printStackTrace();
        }
    }

	// 1. deQ_XA
	// 2. json 에서 history_key를 꺼낸다.
	// 3. Make a new resultJson message.
	// 4. 고객 수신여부의 resultJson을 Agent 소켓에 전송
    public static void handleMessageClient(AtomicInteger stopFlag, Socket clientSocket, FileQueueJNI interThreadComQueue) {
		long threadId = Thread.currentThread().getId();
        try (
            DataInputStream reader = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream writer = new DataOutputStream(clientSocket.getOutputStream());
        ) {
			// deQueue(while)
			try {
				System.out.println("Message sender server : DeQ start.");
				// 무한반복
				while (true) {
					int read_rc = 0;

					// ackThread 가 종료되어 stop 요구를 하였는지 체크한다.
					int currentStopFlag = stopFlag.get();
					if( currentStopFlag > 0 ) {
						System.out.println("I will stop by stopFlag(" + currentStopFlag + ")");
						return;
					}
				
					read_rc = interThreadComQueue.readXA(); // XA read 
					if( read_rc < 0 ) {
						System.err.println("("+threadId+")"+ "readXA failed: " + interThreadComQueue.path + "," + interThreadComQueue.qname + "," + " rc: " + read_rc);
						break;
					}

					if( read_rc == 0 ) {
						System.out.println("("+threadId+")"+ "There is no data(empty) : " + interThreadComQueue.path + "," + interThreadComQueue.qname + "," + " rc: " + read_rc);
						Thread.sleep(1000); // Pause for 1 second
						continue;
					}

					String data = interThreadComQueue.get_out_msg();
					long out_seq = interThreadComQueue.get_out_seq();
					String out_unlink_filename = interThreadComQueue.get_out_unlink_filename();
					long out_run_time = interThreadComQueue.get_out_run_time();

					// writeMessageToFile(threadId, data); // 파일에 메시지 쓰기
					// System.out.println("("+threadId+")"+ "backup file writing success");
					System.out.println("("+threadId+")"+ "normal data: commitXA() sucesss seq : " + out_seq);

					// deQ 데이터에서 HISTORY_KEY(SEQ)를 꺼낸다.

					StringBuilder returnHistoryKey = new StringBuilder();
					StringBuilder returnReceiver = new StringBuilder();
					StringBuilder returnChannel = new StringBuilder();
					StringBuilder returnMsgType = new StringBuilder();
					boolean tf =  JsonParserAndVerify (data, returnHistoryKey, returnReceiver, returnChannel, returnMsgType );
					if( tf == true ) { // 성공메시지를 만든다.
						// Make a new json object with queue data.
						// 새로운 JSON 생성
						JSONObject resultJson = new JSONObject();

						resultJson.put("HISTORY_KEY", returnHistoryKey.toString());
						resultJson.put("RESP_KIND", "RSLT");
						resultJson.put("STATUS_CODE", "1");
						resultJson.put("RESULT_CODE", "0000");
						resultJson.put("RCS_MESSAGE", "success");

						String currentDateTime = getCurrentTime(); // 현재 시간을 가져오는 방법이 구현돼 있어야 함
						resultJson.put("RCS_SND_DTM", currentDateTime);
						resultJson.put("RCS_RCCP_DTM", currentDateTime);
						resultJson.put("AGENT_CODE", "MY");

						String resultMessage = resultJson.toString();
						System.out.println("Generated JSON: " + resultMessage);

						// 메시지를 바이트 배열로 변환 후 길이와 함께 전송
						try {
							byte[] resultData = resultMessage.getBytes();
							writer.writeInt( resultData.length ); // 길이 Prefix header 전송
							writer.write( resultData ); // 서버에 메시지 전송
						} catch( IOException e) {
							System.err.println("(messageSenderServer)" + "writer.write:" + e.getMessage());
							e.printStackTrace();
							return;
						}
	
						System.out.println("result sending OK.");
						
						String responseMessage;
						int responseLength = reader.readInt();
						if( responseLength <= 0 ) {
							System.err.println("Header integer receiving error.");
							return;
						}
						System.out.println("received agent response(ACK-header lengh): " + responseLength);

						byte[] receiveData = new byte[responseLength];
						reader.readFully( receiveData, 0, responseLength);
						String serverResponse = new String(receiveData);
						System.out.println("received agent response(ACK-body): " + serverResponse);

						// 응답이 yes 인지 확인한다.
						boolean your_job_result = true;
						
						// OK 가 들어오면 filequeue commit 을 수행한다.

						if( your_job_result == true) { // normal data
							interThreadComQueue.commitXA();
							// deleteFile(threadId); // 파일 삭제
							System.out.println("("+threadId+")"+ "deleteFile() sucesss seq : " + out_seq);
						}
						else { // abnormal data
							interThreadComQueue.cancelXA();
							System.out.println("("+threadId+")"+ "abnormal data: cancelXA() sucesss seq : " + out_seq);
							break;
						}
					}
					else { // json parsing error.
						interThreadComQueue.commitXA();
						System.err.println("("+threadId+")"+ "json parsing error. We throw it.");
					}
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				System.err.println("Thread " + threadId + " interrupted.");
				
			} finally {
				// interThreadComQueue.close();
			}


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("handleMessageClient() socket connection closed.");

			stopFlag.set(0); // atomicInteger  값을 초기화
			// System.exit(0);
        }
    }

	private static boolean JsonParserAndVerify (String jsonString, StringBuilder returnHistoryKey, StringBuilder returnReceiver, StringBuilder returnChannel, StringBuilder returnMsgType ) {

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
			) {
                // 필드들이 존재하므로 데이터를 읽습니다.
                String HistoryKey = jsonNode.get("SEQ").asText();
                String Channel = jsonNode.get("CHANNEL").asText();
                String MsgType = jsonNode.get("MSG_TYPE").asText();
                String Receiver = jsonNode.get("RECEIVER").asText();

				returnHistoryKey.append(HistoryKey);
				returnChannel.append(Channel);
				returnMsgType.append(MsgType);
				returnReceiver.append(Receiver);

            	System.out.println("-------------------------- JSON Check OK ------------------------");
            	System.out.println("\t- SEQ: " + HistoryKey);
            	System.out.println("\t- CHANNEL: " + Channel);
            	System.out.println("\t- MSG_TYPE: " + MsgType);
            	System.out.println("\t- RECEIVER: " + Receiver);

				return true;
            } else {
            	System.out.println("--------------------------JSON Check ERROR------------------------");
				return false;
            }
        } catch (Exception e) {
            // JSON 파싱 중 예외가 발생했을 경우
            System.err.println("Error verifying or parsing JSON: " + e.getMessage());
			return false;
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
            String interThreadComQueuePath = doc.getElementsByTagName("interThreadComQueuePath").item(0).getTextContent();
            String interThreadComQueueName = doc.getElementsByTagName("interThreadComQueueName").item(0).getTextContent();

            String userWorkingTime_str = doc.getElementsByTagName("userWorkingTimeForSimulate").item(0).getTextContent();
			int userWorkingTimeForSimulate = Integer.parseInt(userWorkingTime_str); 

            String ackPort_str = doc.getElementsByTagName("ackPort").item(0).getTextContent();
			int ackPort = Integer.parseInt(ackPort_str); 
            String resultPort_str = doc.getElementsByTagName("resultPort").item(0).getTextContent();
			int resultPort = Integer.parseInt(resultPort_str); 


            // Config 객체를 생성합니다.
            config = new Config(logLevel, logFilePath, interThreadComQueuePath, interThreadComQueueName, userWorkingTimeForSimulate, ackPort, resultPort );
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
		System.out.println("\t- interThreadComQueue Path: " + config.interThreadComQueuePath);
		System.out.println("\t- interThreadComQueue Name: " + config.interThreadComQueueName);
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
