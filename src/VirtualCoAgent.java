// Java code for thread creation by extending
// the Thread class

import com.clang.fq.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory; 

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import java.io.IOException;

import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.json.JSONObject;

class DeQueueThread extends Thread {
	static {
        System.loadLibrary("jfq"); // Load native library at runtime
                                   // hello.dll (Windows) or libhello.so (Unixes)
        System.loadLibrary("fq");
    }

	private static int qId;
	private static String qPath;
	private static String qName;
	private static String logLevel;
	private static String logPathFile;
	private final BlockingQueue<String> sharedQueue;

    public DeQueueThread ( int qId, String qPath, String qName, String logLevel, String logPathFile, BlockingQueue<String> sharedQueue ) {
        this.qId = qId;
        this.qPath = qPath;
        this.qName = qName;
        this.logLevel = logLevel;
        this.logPathFile = logPathFile;
		this.sharedQueue = sharedQueue;
		System.out.println("qId is [" + qId + "]");
    }

	// @Override
    public void run()
    {
		long myID = Thread.currentThread().getId();
		int rc;

    	FileQueueJNI fqObj;
        fqObj = new FileQueueJNI(qId, logPathFile, Integer.parseInt(logLevel), qPath, qName);

		int openResult;
		openResult = fqObj.open();
		if( openResult < 0 ) {
			System.out.println("file queue open failed.(" + qPath + "," + qName + ")" + "result: " + openResult);
			if( openResult == -4 ) {
				System.out.println("log file open error.");
			}
			return;
		}
		System.out.println("file queue open success.(" + qPath + "," + qName + ")");

        try {
			while(true) {
                rc = fqObj.readXA();
                if( rc < 0 ) {
                    System.out.println( myID + "->read failed: " + fqObj.path + "," + fqObj.qname + "," + " rc: " + rc);
                    break;
                }
                else if( rc == 0 ) {
                    System.out.println( myID + "->empty: " + fqObj.path + "," + fqObj.qname + "," + " rc: " + rc);
                    try {
                        Thread.sleep(1000); // Pause for 1 second
                    }
                    catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                else { // read success
                    String data = fqObj.get_out_msg();
                    long out_seq = fqObj.get_out_seq();
                    String out_unlink_filename = fqObj.get_out_unlink_filename();
                    long out_run_time = fqObj.get_out_run_time();

                    System.out.println(myID + "->rc=" + rc);
                    System.out.println(myID + "->read success: " +  fqObj.path + "," + fqObj.qname + "," + " rc: " + rc + " msg: " + data + " seq: " + out_seq + " unlink_filename: " + out_unlink_filename + " run_timme(micro seconds): " + out_run_time);
					System.out.println("seq=" + out_seq);

                    // input your jobs in here
                    // rc = yourJob();
                    //

                    if( rc > 4) { // normal data
						try {
							sharedQueue.put(data);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

                        rc = fqObj.commitXA();
                        System.out.println("commit success: rc: " + rc);
                    }
                    else { // abnormal data
                        rc = fqObj.cancelXA();
                        System.out.println("cancel success: rc: " + rc);
                        break;
                    }
                    continue;
                }
            } // while end
        }
        catch (Exception e) {
            // Throwing an exception
            System.out.println("Exception is caught");
        }
    }

}
class EnQueueThread extends Thread {
	static {
        System.loadLibrary("jfq"); // Load native library at runtime
                                   // hello.dll (Windows) or libhello.so (Unixes)
        System.loadLibrary("fq");
    }

	private static int qId;
	private static String qPath;
	private static String qName;
	private static String logLevel;
	private static String logPathFile;
	private final BlockingQueue<String> sharedQueue;

    public EnQueueThread ( int qId, String qPath, String qName, String logLevel, String logPathFile, BlockingQueue<String> sharedQueue ) {

        this.qId = qId;
        this.qPath = qPath;
        this.qName = qName;
        this.logLevel = logLevel;
        this.logPathFile = logPathFile;
		this.sharedQueue = sharedQueue;

		System.out.println("qId is [" + qId + "]");
    }

	// @Override
    public void run()
    {
		long myID = Thread.currentThread().getId();
		int rc;

    	FileQueueJNI fqObj;
        fqObj = new FileQueueJNI(qId, logPathFile, Integer.parseInt(logLevel), qPath, qName);

		int openResult;
		openResult = fqObj.open();
		if( openResult < 0 ) {
			System.out.println("file queue open failed.(" + qPath + "," + qName + ")");
			return;
		}
		System.out.println("file queue open success.(" + qPath + "," + qName + ")");

        try {

			while(true) {
				String qMessage = null;

				try {
					while (true) {
						// Check if the queue is empty before taking the value
						if (!sharedQueue.isEmpty()) {
							qMessage = sharedQueue.take();
							System.out.println("Consumed in sharedQueue: " + qMessage);
							break;
						} else {
							System.out.println("sharedQueue is empty");
							// Add some delay before checking again
							Thread.sleep(1000);
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				// JSON 파싱
				JSONObject jsonObj = new JSONObject(qMessage);
				String historyKey = jsonObj.getString("HISTORY_KEY");
				String channelType = jsonObj.getString("CHANNEL_TYPE");
				String msgKind = jsonObj.getString("MSG_KIND");
				String rcvPhnNo = jsonObj.getString("RCV_PHN_NO");

				System.out.println("HISTORY_KEY: " + historyKey);
				System.out.println("CHANNEL_TYPE: " + channelType);
				System.out.println("MSG_KIND: " + msgKind);
				System.out.println("RCV_PHN_NO: " + rcvPhnNo);

				// 새로운 JSON 생성
				JSONObject resultJson = new JSONObject();

				resultJson.put("HISTORY_KEY", historyKey);
				resultJson.put("CHANNEL_TYPE", channelType);
				resultJson.put("RESULT_CODE", "0000");

				String newJsonString = resultJson.toString();
				System.out.println("New JSON String: " + newJsonString);

				while(true) {
					System.out.println("FileQueue enQ start");
					rc = fqObj.write( newJsonString );

					if( rc < 0 ) {
						System.out.println("Write failed: " + fqObj.path + "," + fqObj.qname + "," + " rc: " + rc);
						fqObj.close();
						return;
					}
					else if( rc == 0 ) {
						System.out.println("full: " + fqObj.path + "," + fqObj.qname + "," + " rc: " + rc);
						try {
							Thread.sleep(1); // Pause for 1 second (1000)
						}
						catch(InterruptedException ex) {
								Thread.currentThread().interrupt();
						}
						continue;
					}
					else {
						System.out.println( "FileQueue enQ  success: " + qMessage );
						long out_seq = fqObj.get_out_seq();
						long out_run_time = fqObj.get_out_run_time();

						try {
							Thread.sleep(1); // Pause for 1 second (1000)
						}
						catch(InterruptedException ex) {
								Thread.currentThread().interrupt();
						}
						break;
					}
				}// inner while
            } // outer while
        }
        catch (Exception e) {
            // Throwing an exception
            System.out.println("Exception is caught");
        }
    }

}

public class VirtualCoAgent {

	private static String qPath;
	private static String qName;
	private static String logLevel;
	private static String logPathFile;
	private static String deQueuePath;
	private static String deQueueName;
	private static String enQueuePath;
	private static String enQueueName;

	public static void main(String[] args) {
		int rc;
		int myid=0;

		BlockingQueue<String> sharedQueue = new ArrayBlockingQueue<>(10);


		System.out.println("args.length=" + args.length);
		for( int i=0; i<args.length; i++) {
			System.out.println(String.format("Command line argument %d is %s.", i , args[i]));
		}

		if( args.length != 1 ) {
			System.out.println("Usage  : $ java VirtualHist [yourConfigFile] <enter>\n");
			return;
		}
		String configFilePath = args[0];

		try {
			parseConfigFile(configFilePath);
		}
		catch ( Exception e) {
			e.printStackTrace();
		}

		DeQueueThread DeThreadObj = new DeQueueThread( 0, deQueuePath, deQueueName, logLevel, logPathFile,  sharedQueue);
		DeThreadObj.start();
	
		try {
			Thread.sleep(1000);
		} catch(InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

		EnQueueThread EnThreadObj = new EnQueueThread( 1, enQueuePath, enQueueName, logLevel, logPathFile, sharedQueue);
		EnThreadObj.start();


	} // main end.

	private static void parseConfigFile(String configFilePath) throws Exception {
		File configFile = new File(configFilePath);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(configFile);
		doc.getDocumentElement().normalize();

		Element rootElement = doc.getDocumentElement();
		logLevel = getElementValue(rootElement, "log_level");
		logPathFile = getElementValue(rootElement, "log_filename");

		deQueuePath = getElementValue(rootElement, "de_queue_path");
		deQueueName = getElementValue(rootElement, "de_queue_name");
		enQueuePath = getElementValue(rootElement, "en_queue_path");
		enQueueName = getElementValue(rootElement, "en_queue_name");
	
		printConfigValues();
	}
	private static void printConfigValues() {
		System.out.println("log_level" + logLevel);
		System.out.println("log_filename" + logPathFile);
		System.out.println("en_queue_path" + enQueuePath);
		System.out.println("en_queue_name" + enQueueName);
		System.out.println("de_queue_path" + deQueuePath);
		System.out.println("de_queue_name" + deQueueName);
	}
	private static String getElementValue( Element element, String tagName) {
		NodeList nodeList = element.getElementsByTagName( tagName) ;
		Node node = nodeList.item(0);
		return node.getTextContent();
	}
}  // VirtualCoAgent class end.
