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
 
	// Test Driver
	public static void main(String[] args) {
		int rc;

		System.out.println("args.length=" + args.length);
		for(int i = 0; i< args.length; i++) {
			System.out.println(String.format("Command Line Argument %d is %s", i, args[i]));
		}

		if( args.length != 3) {
			System.out.println("Usage: $ java CoAgent [qpath] [qname] [user_working_time] <enter>");
			System.out.println("Usage: $ java CoAgent /ums24/wiseu/fq/enmq TST 1000 : 1000 -> 1 second <enter>");
			return;
		}
		String qPath = args[0];
		String qName = args[1];
		int userWorkingTime = Integer.parseInt(args[2]);

		// ExecutorService를 사용하여 스레드 생성
        ExecutorService resultExecutor = Executors.newFixedThreadPool(1);

		// 1 개의 결과 수신 스레드 생성
		final int receiveThreadId = THREAD_COUNT+1;
		String resultQueueName = "GW_ONL_HIS";
		resultExecutor.execute(() -> processReceiver(receiveThreadId, qPath, resultQueueName));


		// ExecutorService를 사용하여 스레드 생성
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // 10개의 발송 스레드 생성
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.execute(() -> processMessages(threadId, qPath, qName, userWorkingTime));
		}

		executor.shutdown();
		resultExecutor.shutdown();
	}

	// 결과 수집 스래드
	private static void processReceiver(int threadId, String qPath, String qName)  {
		int rc;

		FileQueueJNI resultQueue = new FileQueueJNI( threadId, "/tmp/result_jni.log", 4, qPath, qName);
		if(  (rc = resultQueue.open()) < 0 ) {
			System.out.println("open failed: " + "qPath="+qPath + ", qName=" + qName + ", rc=" + rc);
			return;
		}

		while(true) {
			// 실제로는 이곳에 통신사로 부터 받는 소켓 수신 코드가 들어가야 함.
			// enQueueData = receiveResult(socket);
			String enQueueData = "This is a result.";
			// 이곳에도 받은 데이터 분실에 대비한 save 루틴이 필요할 수도 있지만
			// enQ 가 워낙 빠르기 때문에 실제로 불필요 함.

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

	// 발송 스래드
	private static void processMessages( int threadId, String qPath, String qName, int userWorkingTime) {
		int rc;

		// 비정상 종료시 미처리로 남아있던 파일을 처리한다.( 미리 커밋을 했을 경우, 메시지 누락 방지 )
		// recovery 
        String fileName = "thread_" + threadId + ".dat";
		try {
			String backupMsg = readFileIfExists( fileName );

			if( backupMsg != null ) {
				int  recovery_result = RecoveryMessage(threadId, backupMsg ); // 화면에 메시지 출력
			}
		} catch (IOException e) {
            System.err.println("backup recovery 오류: " + e.getMessage());
        }

		// make a FileQueueJNI instance with naming test.
		// 3-th argument is loglevel. (0: trace, 1: debug, 2: info, 3: Warning, 4: error, 5: emerg, 6: request)
		// Use 1 in dev and 4 prod.

		FileQueueJNI queue = new FileQueueJNI( threadId, "/tmp/sender_jni.log", 4, qPath, qName);
		if(  (rc = queue.open()) < 0 ) {
			System.out.println("open failed: " + "qPath="+qPath + ", qName=" + qName + ", rc=" + rc);
			return;
		}

		try {
			// 무한반복 ( daemon )
			while (true) {
				int read_rc = 0;

				read_rc = queue.readXA(); // XA read 
				if( read_rc < 0 ) {
					System.out.println("("+threadId+")"+ "readXA failed: " + queue.path + "," + queue.qname + "," + " rc: " + read_rc);
					break;
				}

				if( read_rc == 0 ) {
					System.out.println("("+threadId+")"+ "There is no data(empty) : " + queue.path + "," + queue.qname + "," + " rc: " + read_rc);
					Thread.sleep(1000); // Pause for 1 second
					continue;
				}

				queue.commitXA();

				String data = queue.get_out_msg();
				long out_seq = queue.get_out_seq();
				String out_unlink_filename = queue.get_out_unlink_filename();
				long out_run_time = queue.get_out_run_time();


				writeMessageToFile(threadId, data); // 파일에 메시지 쓰기

				int your_job_result = DoMessage(threadId, read_rc, out_seq, out_run_time,  data ); // 화면에 메시지 출력

				// input your jobs in here ///////////////////////////////////
				// 
				// 
				///////////////////////////////////////////////////////////// 

				if( userWorkingTime > 0 ) {
					Thread.sleep(userWorkingTime); // Pause for 1 second
				}
				if( your_job_result == 1) { // normal data
					deleteFile(threadId); // 파일 삭제
				}
				else { // abnormal data
					queue.cancelXA();
					break;
				}
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			System.out.println("Thread " + threadId + " interrupted.");
		} finally {
        	queue.close();
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

    // my job
    private static int  DoMessage(int threadId, int rc, long out_seq, long out_run_time, String message) {
	    System.out.println("(" + threadId + ")" + "data read success:" + " rc: " + rc + " msg: " + message + " seq: " + out_seq + " run_time(micro seconds): " + out_run_time);
		return 1;
    }

    // my job
    private static int  RecoveryMessage(int threadId, String message) {
	    System.out.println("(" + threadId + ")" + "recovery :"  + message);
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
} // class block end.
