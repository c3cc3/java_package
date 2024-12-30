//////////////////////////////////////////////////////////////////////////////////// 
// "This program is copyrighted by CLang Co., Ltd.
// When using any part or the entirety of the code, 
// please contact us via the email address below to obtain permission before use."
//
// MyAgent.java
// 2024/12/29
// kill -15 pid
//////////////////////////////////////////////////////////////////////////////////// 
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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

// log4j
import org.apache.log4j.Logger; // Log4j import

// Get current time // yyyymmddHHMMSS
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// threadIndex Manager
// Alternately, Java 8부터 지원하는 방식으로 AtomicInteger를 사용
import java.util.concurrent.atomic.AtomicInteger;

// 스레드 관리를 위한 목록
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// 스트링 비교
import java.util.Objects;
// AutomicBoolean
import java.util.concurrent.atomic.AtomicBoolean;

// 구성 값을 저장할 클래스를 정의합니다.
class MyAgentConfig {
    int logLevel;
    String logFilePath;
    String resultQueuePath;
    String resultQueueName;
    String deQueuePath;
    String deQueueName;
	String backupFilePath;
    int userWorkingTimeForSimulate;
    int senderThreads;
	String	ackServerIp;
	int		ackServerPort;
	String	resultServerIp;
	int		resultServerPort;

    // 생성자
    public MyAgentConfig(int logLevel, String logFilePath, String resultQueuePath, String resultQueueName, String deQueuePath, String deQueueName, String backupFilePath, int userWorkingTimeForSimulate, int senderThreads, String ackServerIp, int ackServerPort, String resultServerIp, int resultServerPort) {
        this.logLevel = logLevel;
        this.logFilePath = logFilePath;
        this.resultQueuePath = resultQueuePath;
        this.resultQueueName = resultQueueName;
        this.deQueuePath = deQueuePath;
        this.deQueueName = deQueueName;
        this.backupFilePath = backupFilePath;
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
public class MyAgent {
	static {
    	System.loadLibrary("jfq"); // Load native library at runtime
		System.loadLibrary("fq");
	}
	MyAgent () {
	}

	private static final int RETRY_INTERVAL = 5000; // 3초 재시도 간격
	private static final int THREAD_COUNT = 10;
	private static final Logger logger = Logger.getLogger(MyAgent.class); // log4j Logger 인스턴스

	private static volatile boolean running = true; // 종료 플래그
 
	public static void main(String[] args) {

		System.out.println("args.length=" + args.length);
		for(int i = 0; i< args.length; i++) {
			System.out.println(String.format("Command Line Argument %d is %s", i, args[i]));
		}

		if( args.length != 1) {
			System.out.println("Usage: $ java MyAgent [xml_config_file] <enter>");
			System.out.println("Usage: $ java MyAgent MyAgentConf.xml <enter>");
			return;
		}

		// 입력받은 경로로 구성 파일을 읽습니다.
        MyAgentConfig config = readConfigFromFile(args[0]);

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

		// Shutdown Hook 등록
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown Hook 실행: 서버를 안전하게 종료 중...");
            // running = false; // 서버 실행 종료 신호
        }));

        // AtomicInteger receiverStopFlag = new AtomicInteger(0);

		int rc;

		///////////////////////////////////////////////////////////////////////////
		// All threads use this queue.
		FileQueueJNI resultQueue = new FileQueueJNI( config.senderThreads+1, config.logFilePath, config.logLevel, config.resultQueuePath, config.resultQueueName);
		if(  (rc = resultQueue.open()) < 0 ) {
			logger.error("filequeue open failed: " + "qPath="+ config.resultQueuePath + ", qName=" +  config.resultQueueName + ", rc=" + rc);
			return;
		}

		///////////////////////////////////////////////////////////////////////////
		// ExecutorService를 사용하여 스레드 생성
		ExecutorService resultExecutor = Executors.newFixedThreadPool(1); // 1개 스레드

		// 스레드 ID는 sender 의 최대값+1 을 사용한다.
		final int receiveThreadId = config.senderThreads + 1;
		// 스레드 수행
		resultExecutor.execute(() -> processResultReceiverThread(resultQueue, receiveThreadId, config));
		///////////////////////////////////////////////////////////////////////////



		///////////////////////////////////////////////////////////////////////////
		// ExecutorService를 사용하여 스레드 생성
		ExecutorService executor = Executors.newFixedThreadPool(config.senderThreads + 1);

		// config에 지정된 N 개의 발송 스레드 생성
		for (int i = 0; i < config.senderThreads; i++) {
			final int threadId = i;
			// make a FileQueueJNI instance with naming test.
			// 3-th argument is loglevel. (0: trace, 1: debug, 2: info, 3: Warning, 4: error, 5: emerg, 6: request)
			// Use 1 in dev and 4 prod.
			FileQueueJNI requestQueue = new FileQueueJNI( threadId, config.logFilePath, config.logLevel, config.deQueuePath, config.deQueueName);
			if(  (rc = requestQueue.open()) < 0 ) {
				logger.error("("+threadId+")"+ "open failed: " + "qPath="+ config.deQueuePath + ", qName=" + config.deQueueName + ", rc=" + rc);
				return;
			}
			executor.execute(() -> processMessageThread( requestQueue, resultQueue, threadId, config));
		}
		///////////////////////////////////////////////////////////////////////////


		//System.out.println("모든 작업이 종료되었습니다. 프로그램을 안전하게 종료합니다.");

		executor.shutdown(); // 스레드 종료
		resultExecutor.shutdown(); // 스레드 종료

	}

	// 결과 수집 스래드: 중계사로 부터 계속해서 결과를 받아 resultQueue 에 넣는 서버
	// 1. 중계사 연결
	// 이하 무한 반복(2~3)
	// 2. 결과 수신
	// 3. 결과 수집 큐에 enQueue
	private static void processResultReceiverThread(FileQueueJNI resultQueue, int threadId, MyAgentConfig config)  {
		// while(true) { // 소켓 통신 무한반복
		while(running) { // 소켓 통신 무한반복

			try ( Socket socket = new Socket(config.resultServerIp, config.resultServerPort) ) {
				logger.info("("+threadId+")"+ "Connected to server." + ", IP=" + config.resultServerIp + ", PORT=" + config.resultServerPort );
				try ( DataOutputStream out_socket = new DataOutputStream(socket.getOutputStream());
					DataInputStream in_socket = new DataInputStream(socket.getInputStream()) ) {

					while ( !Thread.currentThread().isInterrupted() ) {
						
						// 서버로부터 메시지 길이 및 메시지 읽기
						int messageLength = in_socket.readInt();
						byte[] receivedData = new byte[messageLength];
						in_socket.readFully(receivedData, 0, messageLength); // 메지시 길이만클 받는다.
						String receivedMessage = new String(receivedData);
						logger.info("("+threadId+")" + "Server sends the result: " + receivedMessage);

						// 통신사로 부터 받은 메시지를 그데로 결과 큐에 넣는다.
						while(true) {
							int write_rc = resultQueue.write( receivedMessage );

							if( write_rc < 0 ) {
								logger.error("("+threadId+")"+ "Write failed: " + resultQueue.path + "," + resultQueue.qname + "," + " rc: " + write_rc);
								logger.error("("+threadId+")"+ "Fatal error: I will stop processingg. Good bye!");
								System.exit(0);
							}
							else if( write_rc == 0 ) { // queue is full
								logger.info("("+threadId+")"+ "full: " + resultQueue.path + "," + resultQueue.qname + "," + " rc: " + write_rc);
								Thread.sleep(10); // Pause for 1 second (1000)
								continue;
							}
							else {
								long out_seq = resultQueue.get_out_seq();
								long out_run_time = resultQueue.get_out_run_time();

								logger.info("("+threadId+")"+ "enQ success(" + resultQueue.path + "," + resultQueue.qname + "), " + "seq=" + out_seq + "," + "rc:" + write_rc);
								break;
							}
						}

						// 잘받았다는 메시지를 보내기 위한 RECEIVE_RESULT json String 을 만든다.
						JSONObject resultJson = new JSONObject();
						resultJson.put("RECEIVE_RESULT", "OK");
						String resultMessage = resultJson.toString();
						logger.info("("+threadId+")" + "Generate a JSON message for confirming." + resultMessage);
						
						// 메시지를 바이트 배열로 변환 후 길이와 함께 전송
						byte[] resultData = resultMessage.getBytes();
						out_socket.writeInt( resultData.length ); // 길이 Prefix header 전송
						out_socket.write( resultData ); // 서버에 메시지 전송
						logger.info("("+threadId+")"+ "The client sends a response confirming that it has successfully received the result message from the server.");
					} // while(true)
				} // try
			} catch ( IOException | InterruptedException e) {
				logger.error(threadId + " 서버 연결 실패: " + config.resultServerIp + ":" + config.resultServerPort);
                e.printStackTrace();
			}
			finally {	
				logger.info(threadId + "무한에서 빠져나옮.");

				try {
                    logger.info(threadId + " 재연결 대기(SLEEP)중.....");
                    Thread.sleep(RETRY_INTERVAL);
                    logger.info(threadId + ":" + config.resultServerPort +" 로 재열결 시도하러 감...");
                } catch (InterruptedException e) {
                    logger.error(threadId + " 재연결 대기(SLEEP)중 인터럽트 발생.");
                    break;
                }
			}
		} // while(true) end.
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
	private static void processMessageThread( FileQueueJNI requestQueue, FileQueueJNI resultQueue, int threadId, MyAgentConfig config) {

		while(running) {
			try ( Socket socket = new Socket(config.ackServerIp, config.ackServerPort) ) {
				logger.info("("+threadId+")"+ "Connected to the ack server." + ", IP=" + config.ackServerIp + ", PORT=" + config.ackServerPort );
				try ( DataOutputStream out_socket = new DataOutputStream(socket.getOutputStream());
					DataInputStream in_socket = new DataInputStream(socket.getInputStream()) ) {

					logger.info("("+threadId+")"+ "Connected to server." + ", IP=" + config.ackServerIp + ", PORT=" + config.ackServerPort );

					// 비정상 종료시 미처리로 남아있던 파일을 처리한다.( 미리 커밋을 했을 경우, 메시지 누락 방지 )
					// recovery 
					String fileName = config.backupFilePath + "/" + "thread_" + threadId + ".dat";
					try {
						String backupMsg = readFileIfExists( fileName );

						if( backupMsg != null ) {
							logger.info("("+threadId+")"+ "backup 파일 처리 시작.(" + fileName + ")" );
							boolean  recoveryResult = RecoveryMessage(threadId, backupMsg, out_socket ); // 화면에 메시지 출력
							if( recoveryResult == false ) {
								logger.error("("+threadId+")"+ "backup recovery -> false ");
								return;
							}
							logger.info("("+threadId+")"+ "backup 파일 처리 완료.(" + fileName + ")" );
						}
						else {
							logger.info("("+threadId+")"+ "backup 파일 존재하지 않음");
						}
					} catch (IOException e) {
						logger.error("("+threadId+")"+ "backup recovery 오류: " + e.getMessage());
						return;
					}

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

						logger.info("("+threadId+")"+ "I have taken a message from queue." + "seq=" + out_seq);


						writeMessageToFile(config, threadId, data); // 파일에 메시지 쓰기
						requestQueue.commitXA();
						logger.info("("+threadId+")"+ "Since the message was securely stored, the commit was performed immediately." + "seq=" + out_seq);

						// 큐에서 꺼낸 데이터를 통신사(Simulator)에 보내고 ACK를 수신한다.
						// ACK 메시지를 resultQueue 에 넣는다.
						boolean your_job_result = DoMessage( threadId, read_rc, out_seq, out_run_time,  data, out_socket, in_socket, resultQueue ); 


						if( your_job_result == true) { // normal data
							deleteFile(config, threadId); // 파일 삭제
							logger.info("("+threadId+")"+ "The message was safely delivered to the server, so the backup file was deleted." + "seq=" + out_seq);
						}
						else { // abnormal data
							// 소켓이 중간에 끊어지면 여기로 들어온다. (here-1)
							requestQueue.cancelXA();
							logger.debug("("+threadId+")"+ "abnormal data: cancelXA() sucesss seq : " + out_seq);

							break;
						}
					} // while(true);
				}
			} catch ( IOException | InterruptedException e) {
				logger.error(threadId + " 서버 연결 실패: " + config.ackServerIp + ":" + config.ackServerPort);
                e.printStackTrace();
			}
			finally {	
				logger.info(threadId + "무한에서 빠져나옮.");

				try {
                    logger.info(threadId + " 재연결 대기(SLEEP)중.....");
                    Thread.sleep(RETRY_INTERVAL);
                    logger.info(threadId + ":" + config.ackServerPort +" 로 재열결 시도하러 감...");
                } catch (InterruptedException e) {
                    logger.error(threadId + " 재연결 대기(SLEEP)중 인터럽트 발생.");
                    break;
                }
			}
		} // while(true) 
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
    private static void writeMessageToFile(MyAgentConfig config, int threadId, String message) {
		String fileName = config.backupFilePath + "/" + "thread_" + threadId + ".dat";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write(message);
            writer.newLine();
            writer.flush();
			logger.info("("+threadId+")"+ "To prepare for unexpected termination, the data retrieved from the queue was securely stored in a backup file(" + fileName + ")");
        } catch (IOException e) {
            logger.error("(" + threadId + ")" + "Error writing to file: " + e.getMessage());
        }
    }

    // DeQ and send message to server.
    private static boolean  DoMessage(int threadId, int rc, long out_seq, long out_run_time, String jsonMessage, DataOutputStream out_socket, DataInputStream in_socket, FileQueueJNI ackQueue ) {


		AtomicBoolean healthCheckFlag = new AtomicBoolean(false);
		StringBuilder returnHistoryKey = new StringBuilder();
		StringBuilder returnReceiver = new StringBuilder();
		boolean tf=JsonParserAndVerify ( threadId, jsonMessage, healthCheckFlag, returnHistoryKey, returnReceiver );
		if( tf == false ) {
            logger.error("(" + threadId + ")" + "JdonParerAndVerify() interrupted.");
			return false;
		}
		if( healthCheckFlag.get()== true) {
            logger.info("(" + threadId + ")" + "Health checking message. I do not delivery it.");
			return true;
		}
	    logger.info("(" + threadId + ")" + "JsonParserAndVerify() OK:" + " key=" + returnHistoryKey + ", receiver=" + returnReceiver + " rc: " + rc + " msg: " + jsonMessage + " seq: " + out_seq + " run_time(micro seconds): " + out_run_time);

		// 메시지를 바이트 배열로 변환 후 길이와 함께 전송
		try {
			byte[] data = jsonMessage.getBytes();
			out_socket.writeInt( data.length ); // 길이 Prefix header 전송
			out_socket.write( data ); // 서버에 메시지 전송
		} catch( IOException e) {
            logger.error("(" + threadId + ")" + "socket.write:" + e.getMessage());
			e.printStackTrace();
			return false;
		}
		logger.info("(" + threadId + ")" + "I successfully delivered the data retrieved(dequeue) from the queue to the carrier.");

		// We receive ACK from server.
		try {
			
			// 길이헤더 수신
			int responseLength = in_socket.readInt();

			if( responseLength > 0 ) {
				// 수신 byte[] 버퍼 생성
				byte[] receiveData = new byte[responseLength];
			
				in_socket.readFully( receiveData, 0, responseLength);

				String serverResponse = new String(receiveData);
				logger.info("("+threadId+")" + "I successfully received the ACK message from the server.(" + serverResponse + ")");

				// ACK 메시지를 ackQueue 에 넣는다.
				while(true) {
					int write_rc = ackQueue.write( serverResponse );

					if( write_rc < 0 ) {
						logger.error("("+threadId+")"+ "Write failed: " + ackQueue.path + "," + ackQueue.qname + "," + " rc: " + write_rc);
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

						logger.info("("+threadId+")"+ "enQ(ACK) success(" + ackQueue.path + "," + ackQueue.qname + "), " + "seq=" + writeOutSeq + "," + "rc:" + write_rc);
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
	    logger.info("(" + threadId + ")" + "recovery :"  + message);

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
    private static void deleteFile(MyAgentConfig config, int threadId) {
		String fileName = config.backupFilePath + "/" + "thread_" + threadId + ".dat";
        File file = new File(fileName);
        if (file.delete()) {
            logger.info("(" + threadId + ")" + "Deleted file: " + fileName);
        } else {
            logger.error("(" + threadId + ")" + "Failed to delete file: " + fileName);
        }
		return;
    }

	// Loading configuration file
	public static MyAgentConfig readConfigFromFile(String filePath) {
        MyAgentConfig config = null;

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
            String backupFilePath = doc.getElementsByTagName("backupFilePath").item(0).getTextContent();

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
            config = new MyAgentConfig(logLevel, logFilePath, resultQueuePath, resultQueueName, deQueuePath, deQueueName, backupFilePath, userWorkingTimeForSimulate, senderThreads, ackServerIp, ackServerPort, resultServerIp, resultServerPort);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return config;  // Config 객체 반환
    }
	// print Config
	private static void printConfig( MyAgentConfig config) {
		logger.debug("---------- < configuration begin >--------------- ");
		logger.debug("\t- Log Level: " + config.logLevel);
		logger.debug("\t- Log File Path: " + config.logFilePath);
		logger.debug("\t- Result Queue Path: " + config.resultQueuePath);
		logger.debug("\t- Result Queue Name: " + config.resultQueueName);
		logger.debug("\t- DeQueue Path: " + config.deQueuePath);
		logger.debug("\t- DeQueue Name: " + config.deQueueName);
		logger.debug("\t- backup File Path: " + config.backupFilePath);
		logger.debug("\t- User Working Time for Simulating : " + config.userWorkingTimeForSimulate);
		logger.debug("\t- Sender Threads: " + config.senderThreads);
		logger.debug("\t- ackServer IP for Simulating : " + config.ackServerIp);
		logger.debug("\t- ackServer PORT for Simulating : " + config.ackServerPort);
		logger.debug("\t- resultServer IP for Simulating : " + config.resultServerIp);
		logger.debug("\t- resultServer PORT for Simulating : " + config.resultServerPort);
		logger.debug("---------- < configuration end >--------------- ");
	}
	private static boolean JsonParserAndVerify ( int threadId, String jsonString, AtomicBoolean  hcFlag, StringBuilder returnHistoryKey, StringBuilder returnReceiver ) {
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
				returnHistoryKey.append(seq);
				
                String channel = jsonNode.get("CHANNEL").asText();
				if (Objects.equals(channel, "HC")) { // health check 
					hcFlag.set(true);
				}

                String msgType = jsonNode.get("MSG_TYPE").asText();
                String receiver = jsonNode.get("RECEIVER").asText();
				returnReceiver.append(receiver);
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

            	logger.debug("-------------------------- JSON Check OK ------------------------");
            	logger.debug("\t-(" + threadId + ")" + "SEQ: " + seq);
            	logger.debug("\t-(" + threadId + ")" + "CHANNEL: " + channel);
            	logger.debug("\t-(" + threadId + ")" + "MSG_TYPE: " + msgType);
            	logger.debug("\t-(" + threadId + ")" + "RECEIVER: " + receiver);
            	logger.debug("\t-(" + threadId + ")" + "SENDER: " + sender);
            	logger.debug("\t-(" + threadId + ")" + "BRAND_ID: " + brandId);
            	logger.debug("\t-(" + threadId + ")" + "BRAND_KEY: " + brandKey);
            	logger.debug("\t-(" + threadId + ")" + "MESSAGEBASE_ID: " + messageBaseId);
            	logger.debug("\t-(" + threadId + ")" + "MESSAGE: " + message);

				return true;
            } else {
            	logger.debug("--------------------------JSON Check ERROR------------------------");
            	logger.error("(" + threadId + ")" + "Invalid JSON: missing required fields");
				return false;
            }
        } catch (Exception e) {
            // JSON 파싱 중 예외가 발생했을 경우
            logger.error( "(" + threadId + ")" + "Error verifying or parsing JSON: " + e.getMessage());
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
