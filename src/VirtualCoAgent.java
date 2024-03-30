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

	private StringBuilder sharedString;

    public DeQueueThread ( int qId, String qPath, String qName, String logLevel, String logPathFile, StringBuilder sharedString ) {
        this.qId = qId;
        this.qPath = qPath;
        this.qName = qName;
        this.logLevel = logLevel;
        this.logPathFile = logPathFile;
		this.sharedString = sharedString;
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
                        rc = fqObj.commitXA();
                        System.out.println("commit success: rc: " + rc);
                    }
                    else { // abnormal data
                        rc = fqObj.cancelXA();
                        System.out.println("cancel success: rc: " + rc);
                        break;
                    }
					synchronized(sharedString) {
						sharedString.append("Arrived new data");
						sharedString.notify();
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

	private StringBuilder sharedString;

    public EnQueueThread ( int qId, String qPath, String qName, String logLevel, String logPathFile, StringBuilder sharedString ) {
        this.qId = qId;
        this.qPath = qPath;
        this.qName = qName;
        this.logLevel = logLevel;
        this.logPathFile = logPathFile;
		this.sharedString = sharedString;
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
				// Wait signal from DeQThread
				synchronized(sharedString) {
					try {
						sharedString.wait();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
				
				String kind_media = "SM";
				String phone_no = "01072021516";
				String send_msg = "01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567";
				String template = "Hello %var1% ! my name is %var2%________________________________________________________________________________________________";
				String var_data = "Choi|Gwisang";
				String SendData = kind_media+phone_no+send_msg+template+var_data;

				while(true) {
					// System.out.println( "Send Data: " + SendData );
					rc = fqObj.write( SendData );

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
						long out_seq = fqObj.get_out_seq();
						long out_run_time = fqObj.get_out_run_time();

						// System.out.println("Write success: " +  test.path + "," + test.qname + "," + " rc: " + rc + "," + " seq: " + out_seq + " run_time(micro seconds): " + out_run_time);

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

		StringBuilder sharedStringObj = new StringBuilder();

		DeQueueThread DeThreadObj = new DeQueueThread( 0, deQueuePath, deQueueName, logLevel, logPathFile,  sharedStringObj);
		DeThreadObj.start();
	
		try {
			Thread.sleep(1000);
		} catch(InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

		EnQueueThread EnThreadObj = new EnQueueThread( 1, enQueuePath, enQueueName, logLevel, logPathFile, sharedStringObj);
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
