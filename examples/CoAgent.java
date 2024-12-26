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

// JSON library: jackson
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.util.HashMap;
import java.util.Map;

// json Object
import org.json.JSONObject;

// socket
import java.io.*;
import java.net.*;

// log4j
import org.apache.log4j.Logger; // Log4j import

// Get current time // yyyymmddHHMMSS
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 구성 값을 저장할 클래스를 정의합니다.
class AgentConfig {
    int logLevel;
    String logFilePath;
    String resultQueuePath;
    String resultQueueName;
    String deQueuePath;
    String deQueueName;
    int userWorkingTimeForSimulate;
    int senderThreads;
	String	ackServerIp;
	int		ackServerPort;
	String	resultServerIp;
	int		resultServerPort;

    // 생성자
    public AgentConfig(int logLevel, String logFilePath, String resultQueuePath, String resultQueueName, String deQueuePath, String deQueueName, int userWorkingTimeForSimulate, int senderThreads, String ackServerIp, int ackServerPort, String resultServerIp, int resultServerPort) {
        this.logLevel = logLevel;
        this.logFilePath = logFilePath;
        this.resultQueuePath = resultQueuePath;
        this.resultQueueName = resultQueueName;
        this.deQueuePath = deQueuePath;
        this.deQueueName = deQueueName;
        this.userWorkingTimeForSimulate = userWorkingTimeForSimulate;
        this.senderThreads = senderThreads;
        this.ackServerIp = ackServerIp;
        this.ackServerPort = ackServerPort;
        this.resultServerIp = resultServerIp;
        this.resultServerPort = resultServerPort;
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
	private static final Logger logger = Logger.getLogger(CoAgent.class); // log4j Logger 인스턴스
 
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
        AgentConfig config = readConfigFromFile(args[0]);

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
		ExecutorService resultExecutor = Executors.newFixedThreadPool(1); // 1개 스레드

		// 스레드 ID는 sender 의 최대값+1 을 사용한다.
		final int receiveThreadId = config.senderThreads+1;

		// 스레드 수행
		resultExecutor.execute(() -> processResultReceiverThread(receiveThreadId, config));

		// ExecutorService를 사용하여 스레드 생성
		ExecutorService executor = Executors.newFixedThreadPool(config.senderThreads + 1);

		// config에 지정된 N 개의 발송 스레드 생성
		for (int i = 0; i < config.senderThreads; i++) {
			final int threadId = i;
			// executor.execute(() -> processMessageThread(config.ackServerIp, config.ackServerPort, threadId, config.deQueuePath, config.deQueueName, config.resultQueuePath, config.resultQueueName, config.userWorkingTimeForSimulate));
			executor.execute(() -> processMessageThread(threadId, config));
		}

		executor.shutdown(); // 스레드 종료
		resultExecutor.shutdown(); // 스레드 종료

	}

	// 결과 수집 스래드: 중계사로 부터 계속해서 결과를 받아 resultQueue 에 넣는 서버
	// 1. 결과를 넣을 파일큐 오픈
	// 2. 중계사 연결
	// 이하 무한 반복(3~4)
	// 3. 결과 수신
	// 4. 결과 수집 큐에 enQueue
	private static void processResultReceiverThread(int threadId, AgentConfig config)  {
		int rc;

		FileQueueJNI resultQueue = new FileQueueJNI( threadId, config.logFilePath, config.logLevel, config.resultQueuePath, config.resultQueueName);
		if(  (rc = resultQueue.open()) < 0 ) {
			logger.error("filequeue open failed: " + "qPath="+ config.resultQueuePath + ", qName=" +  config.resultQueueName + ", rc=" + rc);
			return;
		}

		while(true) { // 소켓 통신 무한반복
			try (
				Socket socket = new Socket(config.resultServerIp, config.resultServerPort);
				DataOutputStream out_socket = new DataOutputStream(socket.getOutputStream());
				DataInputStream in_socket = new DataInputStream(socket.getInputStream()) )  
			{
				logger.info("("+threadId+")"+ "Connected to the echo server." + ", IP=" + config.resultServerIp + ", PORT=" + config.resultServerPort );

				while(true) { // 큐가 full 될 경우를 대비해서 메시지 전송이 성공할 때까지 무한반복

					// 서버로부터 메시지 길이 및 메시지 읽기
					int messageLength = in_socket.readInt();
					if (messageLength <= 0) {
						logger.error("fatal: socket header receiving failed: messageLength=" + messageLength);
						System.exit(0);
					}

					byte[] receivedData = new byte[messageLength];
					in_socket.readFully(receivedData, 0, messageLength);
					String receivedMessage = new String(receivedData);
					logger.info("Server response: " + receivedMessage);

					// 통신사로 부터 받은 메시지를 그데로 결과 큐에 넣는다.
					try {
						while(true) {
							int write_rc = resultQueue.write( receivedMessage );

							if( write_rc < 0 ) {
								logger.error("Write failed: " + resultQueue.path + "," + resultQueue.qname + "," + " rc: " + write_rc);
								resultQueue.close();
								return;
							}
							else if( write_rc == 0 ) { // queue is full
								logger.info("full: " + resultQueue.path + "," + resultQueue.qname + "," + " rc: " + write_rc);
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

								System.out.println("("+threadId+")->receive thread:"+ "enQ success: " + "seq=" + out_seq + "," + "rc:" + write_rc);
								try {
									Thread.sleep(100); // Pause for 1 second (1000)
								}
								catch(InterruptedException ex) {
										Thread.currentThread().interrupt();
								}
								break;
							}
						}
					} catch (Exception e) {
						logger.error("("+threadId+")"+ "resultQueue.write() 오류: " + e.getMessage());
					}

					// 잘받았다는 메시지를 보내기 위한 RECEIVE_RESULT json String 을 만든다.
					JSONObject resultJson = new JSONObject();
					resultJson.put("RECEIVE_RESULT", "OK");
					String resultMessage = resultJson.toString();
					System.out.println("receive result Generated JSON: " + resultMessage);
					
					// 메시지를 바이트 배열로 변환 후 길이와 함께 전송
					try {
						byte[] resultData = resultMessage.getBytes();
						out_socket.writeInt( resultData.length ); // 길이 Prefix header 전송
						out_socket.write( resultData ); // 서버에 메시지 전송
					} catch( IOException e) {
						System.err.println("(messageSenderServer)" + "writer.write:" + e.getMessage());
						e.printStackTrace();
					}
					System.out.println("receive result sending OK.");
				} // while(true)
			} catch (IOException e) {
				logger.error("(" + threadId + ")" + "socket exception:" + e.getMessage());
				e.printStackTrace();
				try {
					Thread.sleep(5000); // Pause for 1 second (1000): 5초 후에 재연결을 시도한다.
				}
				catch(InterruptedException ex) {
						Thread.currentThread().interrupt();
				}
			}
		} // while(true) socket
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
	private static void processMessageThread( int threadId, AgentConfig config) {
		int rc;

/*
		// make a FileQueueJNI instance with naming test.
		// 3-th argument is loglevel. (0: trace, 1: debug, 2: info, 3: Warning, 4: error, 5: emerg, 6: request)
		// Use 1 in dev and 4 prod.
		FileQueueJNI requestQueue = new FileQueueJNI( threadId, config.logFilePath, config.logLevel, config.deQueuePath, config.deQueueName);
		if(  (rc = requestQueue.open()) < 0 ) {
			logger.error("("+threadId+")"+ "open failed: " + "qPath="+ config.deQueuePath + ", qName=" + config.deQueueName + ", rc=" + rc);
			return;
		}

		// We use threadID + 20 : Max threads is 20
		FileQueueJNI ackQueue = new FileQueueJNI( threadId+20, config.logFilePath, config.logLevel, config.resultQueuePath , config.resultQueueName);
		if(  (rc = ackQueue.open()) < 0 ) {
			logger.error("("+threadId+")"+ "open failed: " + " ackQueuePath="+ config.resultQueuePath + ", ackQueueName=" + config.resultQueueName + ", rc=" + rc);
			return;
		}
*/

		while(true) {
			// make a FileQueueJNI instance with naming test.
			// 3-th argument is loglevel. (0: trace, 1: debug, 2: info, 3: Warning, 4: error, 5: emerg, 6: request)
			// Use 1 in dev and 4 prod.
			FileQueueJNI requestQueue = new FileQueueJNI( threadId, config.logFilePath, config.logLevel, config.deQueuePath, config.deQueueName);
			if(  (rc = requestQueue.open()) < 0 ) {
				logger.error("("+threadId+")"+ "open failed: " + "qPath="+ config.deQueuePath + ", qName=" + config.deQueueName + ", rc=" + rc);
				return;
			}

			// We use threadID + 20 : Max threads is 20
			FileQueueJNI ackQueue = new FileQueueJNI( threadId+20, config.logFilePath, config.logLevel, config.resultQueuePath , config.resultQueueName);
			if(  (rc = ackQueue.open()) < 0 ) {
				logger.error("("+threadId+")"+ "open failed: " + " ackQueuePath="+ config.resultQueuePath + ", ackQueueName=" + config.resultQueueName + ", rc=" + rc);
				return;
			}

			try (
				Socket socket = new Socket(config.ackServerIp, config.ackServerPort);
				DataOutputStream out_socket = new DataOutputStream(socket.getOutputStream());
				DataInputStream in_socket = new DataInputStream(socket.getInputStream()) )  
			{
				logger.info("("+threadId+")"+ "Connected to the echo server." + ", IP=" + config.ackServerIp + ", PORT=" + config.ackServerPort );

				// 비정상 종료시 미처리로 남아있던 파일을 처리한다.( 미리 커밋을 했을 경우, 메시지 누락 방지 )
				// recovery 
				logger.info("("+threadId+")"+ "backup 파일 처리 시작." );
				String fileName = "thread_" + threadId + ".dat";
				try {
					String backupMsg = readFileIfExists( fileName );

					if( backupMsg != null ) {
						boolean  recoveryResult = RecoveryMessage(threadId, backupMsg, out_socket ); // 화면에 메시지 출력
						if( recoveryResult == false ) {
							logger.error("("+threadId+")"+ "backup recovery -> false ");
							return;
						}
					}
				} catch (IOException e) {
					logger.error("("+threadId+")"+ "backup recovery 오류: " + e.getMessage());
					return;
				}
				logger.info("("+threadId+")"+ "backup 파일 처리 완료." );

				try {
					// 무한반복 ( daemon )
					while (true) {
						int read_rc = 0;

						read_rc = requestQueue.readXA(); // XA read 
						if( read_rc < 0 ) {
							logger.error("("+threadId+")"+ "readXA failed: " + requestQueue.path + "," + requestQueue.qname + "," + " rc: " + read_rc);
							break;
						}

						if( read_rc == 0 ) {
							logger.debug("("+threadId+")"+ "There is no data(empty) : " + requestQueue.path + "," + requestQueue.qname + "," + " rc: " + read_rc);
							Thread.sleep(1000); // Pause for 1 second
							continue;
						}

						String data = requestQueue.get_out_msg();
						long out_seq = requestQueue.get_out_seq();
						String out_unlink_filename = requestQueue.get_out_unlink_filename();
						long out_run_time = requestQueue.get_out_run_time();


						writeMessageToFile(threadId, data); // 파일에 메시지 쓰기
						logger.debug("("+threadId+")"+ "backup file writing success");
						requestQueue.commitXA();
						logger.debug("("+threadId+")"+ "normal data: commitXA() sucesss seq : " + out_seq);

						// 큐에서 꺼낸 데이터를 통신사(Simulator)에 보내고 ACK를 수신한다.
						// ACK 메시지를 resultQueue 에 넣는다.
						boolean your_job_result = DoMessage(threadId, read_rc, out_seq, out_run_time,  data, out_socket, in_socket, ackQueue ); // 화면에 메시지 출력

						// input your jobs in here ///////////////////////////////////
						// 
						// 
						///////////////////////////////////////////////////////////// 

						if( your_job_result == true) { // normal data
							deleteFile(threadId); // 파일 삭제
							logger.debug("("+threadId+")"+ "deleteFile() sucesss seq : " + out_seq);
						}
						else { // abnormal data
							requestQueue.cancelXA();
							logger.debug("("+threadId+")"+ "abnormal data: cancelXA() sucesss seq : " + out_seq);

							break;
						}
					}
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					logger.error("Thread " + threadId + " interrupted.");
					
				} finally {
					requestQueue.close();
					ackQueue.close();
				}
			} catch (IOException e) {
				logger.error("(" + threadId + ")" + "socket:" + e.getMessage());
				e.printStackTrace();


				try {
					Thread.sleep(5000); // Pause for 1 second (1000): 5초 후에 재연결을 시도한다.
				}
				catch(InterruptedException ex) {
						Thread.currentThread().interrupt();
				}
			} finally {
				/*
				if (socket != null && !socket.isClosed()) {
					socket.close(); // 기존 소켓 닫기
					logger.info("기존 소켓을 닫았습니다.");
				}
				*/
				logger.error("(" + threadId + ")" + "socket finallly.");
			}
		} // while(true) : 소켓통신 무한반복
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
            logger.error("(" + threadId + ")" + "Error writing to file: " + e.getMessage());
        }
    }

    // DeQ and send message to server.
    private static boolean  DoMessage(int threadId, int rc, long out_seq, long out_run_time, String jsonMessage, DataOutputStream out_socket, DataInputStream in_socket, FileQueueJNI ackQueue ) {


		boolean healthCheckFlag = false;
		boolean tf=JsonParserAndVerify ( threadId, jsonMessage, healthCheckFlag );
		if( tf == false ) {
            logger.error("(" + threadId + ")" + "JdonParerAndVerify() interrupted.");
			return false;
		}
		if( healthCheckFlag == true) {
            logger.info("(" + threadId + ")" + "Health checking message.");
			return true;
		}

	    logger.debug("(" + threadId + ")" + "data read success:" + " rc: " + rc + " msg: " + jsonMessage + " seq: " + out_seq + " run_time(micro seconds): " + out_run_time);


		// 메시지를 바이트 배열로 변환 후 길이와 함께 전송
		try {
			byte[] data = jsonMessage.getBytes();
			out_socket.writeInt( data.length ); // 길이 Prefix header 전송
			out_socket.write( data ); // 서버에 메시지 전송
		} catch( IOException e) {
            logger.error("(" + threadId + ")" + "socket.write:" + e.getMessage());
			e.printStackTrace();
		}

		logger.info("(" + threadId + ")" + "data send success.");

		// We receive ACK from server.
		try {
			// 길이헤더 수신
			int responseLength = in_socket.readInt();

			if( responseLength > 0 ) {
				// 수신 byte[] 버퍼 생성
				byte[] receiveData = new byte[responseLength];
			
				in_socket.readFully( receiveData, 0, responseLength);

				String serverResponse = new String(receiveData);
				logger.info("("+threadId+")" + "received server response(ACK): " + serverResponse);

				// ACK 메시지를 ackQueue 에 넣는다.
				while(true) {
					int write_rc = ackQueue.write( serverResponse );

					if( write_rc < 0 ) {
						logger.error("("+threadId+")"+ "Write failed: " + ackQueue.path + "," + ackQueue.qname + "," + " rc: " + write_rc);
						ackQueue.close();
						return false;
					}
					else if( write_rc == 0 ) { // queue is full
						logger.debug("("+threadId+")" + "full: " + ackQueue.path + "," + ackQueue.qname + "," + " rc: " + write_rc);
						try {
							// We retry to enQ after 1/10 secondes
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

						logger.info("("+threadId+")"+ "enQ(ACK) success: " + "seq=" + writeOutSeq + "," + "rc:" + write_rc);
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
			else {
				System.err.println("길이 헤더 수신 실패. responseLength=" + responseLength);
				return false;
			}
		} catch( IOException e ) {
            logger.error("(" + threadId + ")" + "socket.receive and enQ failed.:" + e.getMessage());
			e.printStackTrace();
			return false;
		}

		return true;
    }

    // my job
    private static boolean  RecoveryMessage(int threadId, String message, DataOutputStream out_socket) {
	    System.out.println("(" + threadId + ")" + "recovery :"  + message);

		try {
			byte[] data = message.getBytes();
			out_socket.writeInt(data.length); // 길이헤더 전송
			out_socket.write(data); // 서버에 메시지 전송
			logger.info("("+threadId+")"+ "Recovery data sending success: " + data);
			return true;
		} catch( IOException e) {
            logger.error("(" + threadId + ")" + "socket.write:" + e.getMessage());
			return false;
		}
    }

    // 스레드 ID에 따른 파일 삭제
    private static void deleteFile(int threadId) {
        String fileName = "thread_" + threadId + ".txt";
        File file = new File(fileName);
        if (file.delete()) {
            logger.info("(" + threadId + ")" + "Deleted file: " + fileName);
        } else {
            logger.error("(" + threadId + ")" + "Failed to delete file: " + fileName);
        }
		return;
    }

	// Loading configuration file
	public static AgentConfig readConfigFromFile(String filePath) {
        AgentConfig config = null;

        try {
            // XML 파일을 파싱하여 Document 객체를 생성합니다.
            File inputFile = new File(filePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            // 각 설정 값을 읽어옵니다.
            String logLevel_str = doc.getElementsByTagName("logLevel").item(0).getTextContent();
			int logLevel = Integer.parseInt(logLevel_str); 


            String logFilePath = doc.getElementsByTagName("logFilePath").item(0).getTextContent();
            String resultQueuePath = doc.getElementsByTagName("resultQueuePath").item(0).getTextContent();
            String resultQueueName = doc.getElementsByTagName("resultQueueName").item(0).getTextContent();
            String deQueuePath = doc.getElementsByTagName("deQueuePath").item(0).getTextContent();
            String deQueueName = doc.getElementsByTagName("deQueueName").item(0).getTextContent();

            String userWorkingTime_str = doc.getElementsByTagName("userWorkingTimeForSimulate").item(0).getTextContent();
			int userWorkingTimeForSimulate = Integer.parseInt(userWorkingTime_str); 

            String senderThreads_str = doc.getElementsByTagName("senderThreads").item(0).getTextContent();
			int senderThreads = Integer.parseInt(senderThreads_str); 

            String ackServerIp = doc.getElementsByTagName("ackServerIp").item(0).getTextContent();
            String ackServerPort_str = doc.getElementsByTagName("ackServerPort").item(0).getTextContent();
			int ackServerPort = Integer.parseInt(ackServerPort_str); 

            String resultServerIp = doc.getElementsByTagName("resultServerIp").item(0).getTextContent();
            String resultServerPort_str = doc.getElementsByTagName("resultServerPort").item(0).getTextContent();
			int resultServerPort = Integer.parseInt(resultServerPort_str); 

            // Config 객체를 생성합니다.
            config = new AgentConfig(logLevel, logFilePath, resultQueuePath, resultQueueName, deQueuePath, deQueueName, userWorkingTimeForSimulate, senderThreads, ackServerIp, ackServerPort, resultServerIp, resultServerPort);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return config;  // Config 객체 반환
    }
	// print Config
	private static void printConfig( AgentConfig config) {
		System.out.println("---------- < configuration begin >--------------- ");
		System.out.println("\t- Log Level: " + config.logLevel);
		System.out.println("\t- Log File Path: " + config.logFilePath);
		System.out.println("\t- Result Queue Path: " + config.resultQueuePath);
		System.out.println("\t- Result Queue Name: " + config.resultQueueName);
		System.out.println("\t- DeQueue Path: " + config.deQueuePath);
		System.out.println("\t- DeQueue Name: " + config.deQueueName);
		System.out.println("\t- User Working Time for Simulating : " + config.userWorkingTimeForSimulate);
		System.out.println("\t- Sender Threads: " + config.senderThreads);
		System.out.println("\t- ackServer IP for Simulating : " + config.ackServerIp);
		System.out.println("\t- ackServer PORT for Simulating : " + config.ackServerPort);
		System.out.println("\t- resultServer IP for Simulating : " + config.resultServerIp);
		System.out.println("\t- resultServer PORT for Simulating : " + config.resultServerPort);
		System.out.println("---------- < configuration end >--------------- ");
	}
	private static boolean JsonParserAndVerify ( int threadId, String jsonString, boolean hcFlag ) {
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
            	System.out.println("\t-(" + threadId + ")" + "SEQ: " + seq);
            	System.out.println("\t-(" + threadId + ")" + "CHANNEL: " + channel);
            	System.out.println("\t-(" + threadId + ")" + "MSG_TYPE: " + msgType);
            	System.out.println("\t-(" + threadId + ")" + "RECEIVER: " + receiver);
            	System.out.println("\t-(" + threadId + ")" + "SENDER: " + sender);
            	System.out.println("\t-(" + threadId + ")" + "BRAND_ID: " + brandId);
            	System.out.println("\t-(" + threadId + ")" + "BRAND_KEY: " + brandKey);
            	System.out.println("\t-(" + threadId + ")" + "MESSAGEBASE_ID: " + messageBaseId);
            	System.out.println("\t-(" + threadId + ")" + "MESSAGE: " + message);

				if (channel.equalsIgnoreCase("HC")) { // is health checking.
					hcFlag = true;
				}
				return true;
            } else {
            	System.out.println("--------------------------JSON Check ERROR------------------------");
            	logger.error("(" + threadId + ")" + "Invalid JSON: missing required fields");
				return false;
            }
        } catch (Exception e) {
            // JSON 파싱 중 예외가 발생했을 경우
            System.err.println("Error verifying or parsing JSON: " + e.getMessage());
			return false;
        }
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
} // class block end.
