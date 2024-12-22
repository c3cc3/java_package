// CoAgent.java
// 
import com.clang.fq.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.FileReader;
// XML configuration
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

// for your input
import java.util.Scanner;

// JSON
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// socket
import java.io.*;
import java.net.*;

// log4j
import org.apache.log4j.Logger; // Log4j import


// 구성 값을 저장할 클래스를 정의합니다.
class Config {
    String logLevel;
    String logFilePath;
    String resultQueuePath;
    String resultQueueName;
    String deQueuePath;
    String deQueueName;
    int userWorkingTimeForSimulate;
    int senderThreads;
	String	serverIp;
	int		serverPort;

    // 생성자
    public Config(String logLevel, String logFilePath, String resultQueuePath, String resultQueueName,
                  String deQueuePath, String deQueueName, int userWorkingTimeForSimulate, int senderThreads,
				  String serverIp, int serverPort) {
        this.logLevel = logLevel;
        this.logFilePath = logFilePath;
        this.resultQueuePath = resultQueuePath;
        this.resultQueueName = resultQueueName;
        this.deQueuePath = deQueuePath;
        this.deQueueName = deQueueName;
        this.userWorkingTimeForSimulate = userWorkingTimeForSimulate;
        this.senderThreads = senderThreads;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
    }
}

/*
** Warning: max buffer size is 65536
*/
public class CoAgent {
	static {
    	System.loadLibrary("jfq"); // Load native library at runtime
                                   // hello.dll (Windows) or libhello.so (Unixes)
		System.loadLibrary("fq");
	}
	CoAgent () {
	}

	private static final int THREAD_COUNT = 10;
	private static final Logger logger = Logger.getLogger(CoAgent.class); // Logger 인스턴스
 
	// Test Driver
	public static void main(String[] args) {
		int rc;

		System.out.println("args.length=" + args.length);
		for(int i = 0; i< args.length; i++) {
			System.out.println(String.format("Command Line Argument %d is %s", i, args[i]));
		}

		if( args.length != 1) {
			System.out.println("Usage: $ java CoAgent [xml_config_file] <enter>");
			System.out.println("Usage: $ java CoAgent CoAgentConf.xml <enter>");
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


		// ExecutorService를 사용하여 스레드 생성
		ExecutorService resultExecutor = Executors.newFixedThreadPool(1);

		// 1 개의 결과 수신 스레드 생성
		final int receiveThreadId = config.senderThreads+1;

		String resultQueueName = "GW_ONL_HIS";
		resultExecutor.execute(() -> processResultReceiver(receiveThreadId, config.resultQueuePath, config.resultQueueName));


		// ExecutorService를 사용하여 스레드 생성
		ExecutorService executor = Executors.newFixedThreadPool(config.senderThreads + 1);

		// 10개의 발송 스레드 생성
		for (int i = 0; i < config.senderThreads; i++) {
			final int threadId = i;
			executor.execute(() -> processMessages(config.serverIp, config.serverPort, threadId, config.deQueuePath, config.deQueueName, config.resultQueuePath, config.resultQueueName, config.userWorkingTimeForSimulate));
		}

		executor.shutdown();
		resultExecutor.shutdown();

	}

	// 결과 수집 스래드
	private static void processResultReceiver(int threadId, String qPath, String qName)  {
		int rc;

		FileQueueJNI resultQueue = new FileQueueJNI( threadId, "/tmp/result_jni.log", 4, qPath, qName);
		if(  (rc = resultQueue.open()) < 0 ) {
			System.out.println("open failed: " + "qPath="+qPath + ", qName=" + qName + ", rc=" + rc);
			return;
		}

		while(true) {
			// 실제로는 이곳에 통신사로 부터 받는 소켓 수신 코드가 들어가야 함.
			// enQueueData = receiveResult(socket);
			String enQueueData = "This is a result(JSON).";
			// 이곳에도 받은 데이터 분실에 대비한 save 루틴이 필요할 수도 있지만
			// enQ 가 워낙 빠르기 때문에 실제로 불필요 함.


			/*
			// 서버로부터 메시지 길이 및 메시지 읽기
			int messageLength = in.readInt();
			if (messageLength > 0) {
				byte[] receivedData = new byte[messageLength];
				in.readFully(receivedData, 0, messageLength);
				String receivedMessage = new String(receivedData);
				System.out.println("Server response: " + receivedMessage);
			}
			*/

			int write_rc = resultQueue.write( enQueueData );

			if( write_rc < 0 ) {
				System.out.println("Write failed: " + resultQueue.path + "," + resultQueue.qname + "," + " rc: " + write_rc);
				resultQueue.close();
				return;
			}
			else if( write_rc == 0 ) { // queue is full
				System.out.println("full: " + resultQueue.path + "," + resultQueue.qname + "," + " rc: " + write_rc);
				try {
					Thread.sleep(10); // Pause for 1 second (1000)
				}
				catch(InterruptedException ex) {
				        Thread.currentThread().interrupt();
			    }
				continue;
			}
			else {
				long out_seq = resultQueue.get_out_seq();
				long out_run_time = resultQueue.get_out_run_time();

				System.out.println("("+threadId+")"+ "enQ success: " + "seq=" + out_seq + "," + "rc:" + write_rc);
				try {
					Thread.sleep(100); // Pause for 1 second (1000)
				}
				catch(InterruptedException ex) {
				        Thread.currentThread().interrupt();
			    }
				continue;
			}
		}
	}

	/////////////////////////////////////////////////////////////////////////
	// 발송 스래드
	// 1. Open FileQueue( for readXA_FQ )
	// 2. Open FileQueue( for write_FQ )
	// 2. Connect to server.
	//	while(true) {
	//		readXA(&jsonMessage); // read message from FQ
	//		sendToServer(jsonMessage); // send message to server
	//		receiveAckFromServer(&jsonAckMessage); // receive ack
	//		writeFQ(jsonAckMessage); // enQ ack
	//	}
	/////////////////////////////////////////////////////////////////////////
	private static void processMessages( String serverIp, int serverPort, int threadId, String qPath, String qName, String ackQueuePath, String ackQueueName, int userWorkingTimeForSimulate) {
		int rc;


		// make a FileQueueJNI instance with naming test.
		// 3-th argument is loglevel. (0: trace, 1: debug, 2: info, 3: Warning, 4: error, 5: emerg, 6: request)
		// Use 1 in dev and 4 prod.

		FileQueueJNI queue = new FileQueueJNI( threadId, "/tmp/sender_jni.log", 4, qPath, qName);
		if(  (rc = queue.open()) < 0 ) {
			System.out.println("open failed: " + "qPath="+qPath + ", qName=" + qName + ", rc=" + rc);
			return;
		}

		FileQueueJNI ackQueue = new FileQueueJNI( threadId+20, "/tmp/sender_ack_jni.log", 4, ackQueuePath , ackQueueName);
		if(  (rc = ackQueue.open()) < 0 ) {
			System.out.println("open failed: " + " ackQueuePath="+ ackQueuePath + ", ackQueueName=" + ackQueueName + ", rc=" + rc);
			return;
		}

		try (
			Socket socket = new Socket(serverIp, serverPort);
			DataOutputStream out_socket = new DataOutputStream(socket.getOutputStream());
			DataInputStream in_socket = new DataInputStream(socket.getInputStream()) )  
		{
			logger.info("("+threadId+")"+ "Connected to the echo server." + ", IP=" + serverIp + ", PORT=" + serverPort );

			// 비정상 종료시 미처리로 남아있던 파일을 처리한다.( 미리 커밋을 했을 경우, 메시지 누락 방지 )
			// recovery 
			String fileName = "thread_" + threadId + ".dat";
			try {
				String backupMsg = readFileIfExists( fileName );

				if( backupMsg != null ) {
					int  recovery_result = RecoveryMessage(threadId, backupMsg, out_socket ); // 화면에 메시지 출력
				}
			} catch (IOException e) {
				System.err.println("("+threadId+")"+ "backup recovery 오류: " + e.getMessage());
				logger.error("("+threadId+")"+ "Connected to the echo server." + ", IP=" + serverIp + ", PORT=" + serverPort );
			}

			try {
				// 무한반복 ( daemon )
				while (true) {
					int read_rc = 0;

					read_rc = queue.readXA(); // XA read 
					if( read_rc < 0 ) {
						logger.error("("+threadId+")"+ "readXA failed: " + queue.path + "," + queue.qname + "," + " rc: " + read_rc);
						break;
					}

					if( read_rc == 0 ) {
						System.out.println("("+threadId+")"+ "There is no data(empty) : " + queue.path + "," + queue.qname + "," + " rc: " + read_rc);
						logger.debug("("+threadId+")"+ "There is no data(empty) : " + queue.path + "," + queue.qname + "," + " rc: " + read_rc);
						Thread.sleep(1000); // Pause for 1 second
						continue;
					}


					String data = queue.get_out_msg();
					long out_seq = queue.get_out_seq();
					String out_unlink_filename = queue.get_out_unlink_filename();
					long out_run_time = queue.get_out_run_time();


					writeMessageToFile(threadId, data); // 파일에 메시지 쓰기

					int your_job_result = DoMessage(threadId, read_rc, out_seq, out_run_time,  data, out_socket, in_socket, ackQueue ); // 화면에 메시지 출력

					// input your jobs in here ///////////////////////////////////
					// 
					// 
					///////////////////////////////////////////////////////////// 

					if( userWorkingTimeForSimulate > 0 ) {
						Thread.sleep(userWorkingTimeForSimulate); // Pause for 1 second
					}
					if( your_job_result == 1) { // normal data
						deleteFile(threadId); // 파일 삭제
						queue.commitXA();
						logger.debug("("+threadId+")"+ "normal data: commitXA() sucesss seq : " + out_seq);
					}
					else { // abnormal data
						queue.cancelXA();
						logger.debug("("+threadId+")"+ "abnormal data: cancelXA() sucesss seq : " + out_seq);
						break;
					}
				}
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				System.out.println("Thread " + threadId + " interrupted.");
			} finally {
				queue.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	} 

	private static String readFileIfExists(String filePath) throws IOException {
	// private static String readFileIfExists(String filePath) {
        // 파일 객체 생성
        File file = new File(filePath);

        // 파일 존재 여부 확인
        if (!file.exists() || !file.isFile() || file.length() == 0) {
            // throw new IOException("파일이 존재하지 않거나 파일이 아닙니다: " + filePath);
			return null;
        }

        // 파일 읽기
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
        }
        return content.toString();
    }
	
    // 스레드 ID에 따른 파일에 메시지 쓰기
    private static void writeMessageToFile(int threadId, String message) {
        String fileName = "thread_" + threadId + ".txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write(message);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("(" + threadId + ")" + "Error writing to file: " + e.getMessage());
        }
    }

    // DeQ and send message to server.
    private static int  DoMessage(int threadId, int rc, long out_seq, long out_run_time, String jsonMessage, DataOutputStream out_socket, DataInputStream in_socket, FileQueueJNI ackQueue ) {
		boolean tf=JsonParserAndVerify ( threadId, jsonMessage );
		if( tf == false ) {
			return 0;
		}

	    System.out.println("(" + threadId + ")" + "data read success:" + " rc: " + rc + " msg: " + jsonMessage + " seq: " + out_seq + " run_time(micro seconds): " + out_run_time);


		// 메시지를 바이트 배열로 변환 후 길이와 함께 전송
		try {
			byte[] data = jsonMessage.getBytes();
			out_socket.writeInt( data.length ); // 길이 Prefix header 전송
			out_socket.write( data ); // 서버에 메시지 전송
		} catch( IOException e) {
			e.printStackTrace();
		}

		System.out.println("(" + threadId + ")" + "data send success");

		// We receive ACK from server.
		try {
			// 길이헤더 수신
			int responseLength = in_socket.readInt();
			if( responseLength > 0 ) {
				// 수신 byte[] 버퍼 생성
				byte[] receiveData = new byte[responseLength];
			
				in_socket.readFully( receiveData, 0, responseLength);

				String serverResponse = new String(receiveData);
				System.out.println("("+threadId+")" + "Server response(ACK): " + serverResponse);

				while(true) {
					int write_rc = ackQueue.write( serverResponse );

					if( write_rc < 0 ) {
						System.out.println("("+threadId+")"+ "Write failed: " + ackQueue.path + "," + ackQueue.qname + "," + " rc: " + write_rc);
						ackQueue.close();
						return write_rc;
					}
					else if( write_rc == 0 ) { // queue is full
						System.out.println("("+threadId+")" + "full: " + ackQueue.path + "," + ackQueue.qname + "," + " rc: " + write_rc);
						try {
							Thread.sleep(10); // Pause for 1 second (1000)
						}
						catch(InterruptedException ex) {
								Thread.currentThread().interrupt();
						}
						continue;
					}
					else {
						long writeOutSeq = ackQueue.get_out_seq();
						long writeRunTime = ackQueue.get_out_run_time();

						System.out.println("("+threadId+")"+ "enQ(ACK) success: " + "seq=" + writeOutSeq + "," + "rc:" + write_rc);
						try {
							Thread.sleep(100); // Pause for 1 second (1000)
						}
						catch(InterruptedException ex) {
								Thread.currentThread().interrupt();
						}
						break;
					}
				}
			}
		} catch( IOException e ) {
			e.printStackTrace();
		}

		return 1;
    }

    // my job
    private static int  RecoveryMessage(int threadId, String message, DataOutputStream out_socket) {
	    System.out.println("(" + threadId + ")" + "recovery :"  + message);

		try {
			byte[] data = message.getBytes();
			out_socket.writeInt(data.length); // 길이헤더 전송
			out_socket.write(data); // 서버에 메시지 전송
			System.out.println("("+threadId+")"+ "Recovery data sending success: " + data);
		} catch( IOException e) {
			e.printStackTrace();
			return 0;
		}
		
		return 1;
    }

    // 스레드 ID에 따른 파일 삭제
    private static void deleteFile(int threadId) {
        String fileName = "thread_" + threadId + ".txt";
        File file = new File(fileName);
        if (file.delete()) {
            System.out.println("(" + threadId + ")" + "Deleted file: " + fileName);
        } else {
            System.err.println("(" + threadId + ")" + "Failed to delete file: " + fileName);
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
            String resultQueuePath = doc.getElementsByTagName("resultQueuePath").item(0).getTextContent();
            String resultQueueName = doc.getElementsByTagName("resultQueueName").item(0).getTextContent();
            String deQueuePath = doc.getElementsByTagName("deQueuePath").item(0).getTextContent();
            String deQueueName = doc.getElementsByTagName("deQueueName").item(0).getTextContent();

            String userWorkingTime_str = doc.getElementsByTagName("userWorkingTimeForSimulate").item(0).getTextContent();
			int userWorkingTimeForSimulate = Integer.parseInt(userWorkingTime_str); 

            String senderThreads_str = doc.getElementsByTagName("senderThreads").item(0).getTextContent();
			int senderThreads = Integer.parseInt(senderThreads_str); 

            String serverIp = doc.getElementsByTagName("serverIp").item(0).getTextContent();

            String serverPort_str = doc.getElementsByTagName("serverPort").item(0).getTextContent();
			int serverPort = Integer.parseInt(serverPort_str); 


            // Config 객체를 생성합니다.
            config = new Config(logLevel, logFilePath, resultQueuePath, resultQueueName, 
                                deQueuePath, deQueueName, userWorkingTimeForSimulate, senderThreads,
								serverIp, serverPort );

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
		System.out.println("\t- Result Queue Path: " + config.resultQueuePath);
		System.out.println("\t- Result Queue Name: " + config.resultQueueName);
		System.out.println("\t- DeQueue Path: " + config.deQueuePath);
		System.out.println("\t- DeQueue Name: " + config.deQueueName);
		System.out.println("\t- User Working Time for Simulating : " + config.userWorkingTimeForSimulate);
		System.out.println("\t- Sender Threads: " + config.senderThreads);
		System.out.println("\t- Server IP for Simulating : " + config.serverIp);
		System.out.println("\t- Server PORT for Simulating : " + config.serverPort);
		System.out.println("---------- < configuration end >--------------- ");
	}
	private static boolean JsonParserAndVerify ( int threadId, String jsonString ) {
        // JSON 문자열 입력
        // String jsonString = "{\"name\":\"John\", \"age\":30, \"city\":\"New York\"}";

        // ObjectMapper 인스턴스를 생성합니다.
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // JSON 문자열을 JsonNode로 파싱합니다.
            JsonNode jsonNode = objectMapper.readTree(jsonString);

            // JSON 검증: 필수 필드가 있는지 확인
            if (jsonNode.hasNonNull("SEQ") 
				// && jsonNode.hasNonNull("CHANNEL")
				// && jsonNode.hasNonNull("MSG_TYPE")
				// && jsonNode.hasNonNull("RECEIVER")
				// && jsonNode.hasNonNull("SENDER")
				// && jsonNode.hasNonNull("BRAND_ID")
				// && jsonNode.hasNonNull("BRAND_KEY")
				// && jsonNode.hasNonNull("MESSAGEBASE_ID")
				// && jsonNode.hasNonNull("HEADER")
				// && jsonNode.hasNonNull("FOOTER")
				// && jsonNode.hasNonNull("COPYALLOWED")
				// && jsonNode.hasNonNull("MESSAGE")
				// && jsonNode.hasNonNull("BOTTONS")
			) {
                // 필드들이 존재하므로 데이터를 읽습니다.
                String seq = jsonNode.get("SEQ").asText();
				/*
                String channel = jsonNode.get("CHANNEL").asText();
                String msgType = jsonNode.get("MSG_TYPE").asText();
                String receiver = jsonNode.get("RECEIVER").asText();
                String sender = jsonNode.get("SENDEER").asText();
                String brandId = jsonNode.get("BRAND_ID").asText();
                String brandKey = jsonNode.get("BRAND_KEY").asText();
                String messageId = jsonNode.get("MESSAGEBASE_ID").asText();
                String header = jsonNode.get("HEADER").asText();
                String footer = jsonNode.get("FOOTER").asText();
                String copyAllowed = jsonNode.get("COPYALLOWED").asText();
                String message = jsonNode.get("MESSAGE").asText();
				*/
                // String bottons = jsonNode.get("BOTTONS").asText();
                // int channel = jsonNode.get("CHANNEL").asInt();
                // String city = jsonNode.get("city").asText();
				// boolean isActive = jsonNode.get("isActive").asBoolean();

            	System.out.println("-------------------------- OK ------------------------");
            	System.out.println("(" + threadId + ")" + "SEQ: " + seq);
				return true;
            } else {
            	System.out.println("--------------------------ERROR------------------------");
            	System.out.println("(" + threadId + ")" + "Invalid JSON: missing required fields");
				return false;
            }
        } catch (Exception e) {
            // JSON 파싱 중 예외가 발생했을 경우
            System.err.println("Error verifying or parsing JSON: " + e.getMessage());
			return false;
        }
    }
} // class block end.
