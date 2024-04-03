import com.clang.fq.*;

/*
** Warning: max buffer size is 65536
*/
// This is a example code for XA_dequeuing.
// Java code for thread creation by implementing
// the Runnable Interface

public class MultiThread_DeQ_Demo implements Runnable {
	static {
		System.loadLibrary("jfq"); // Load native library at runtime
								   // hello.dll (Windows) or libhello.so (Unixes)
		System.loadLibrary("fq");
	}
	private FileQueueJNI fq;

	public MultiThread_DeQ_Demo (FileQueueJNI fq_obj) {
		this.fq = fq_obj;
		
	}
	public void run() {
		long myID = Thread.currentThread().getId();
		try {
			while(true) {
				int rc;
				rc = fq.readXA();
				if( rc < 0 ) {
					System.out.println( myID + "->read failed: " + fq.path + "," + fq.qname + "," + " rc: " + rc);
					break;
				}
				else if( rc == 0 ) {
					System.out.println( myID + "->empty: " + fq.path + "," + fq.qname + "," + " rc: " + rc);

					try {
						Thread.sleep(1000); // Pause for 1 second
					}
					catch(InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
					continue;
				}
				else {
					String data = fq.get_out_msg();
					long out_seq = fq.get_out_seq();
					String out_unlink_filename = fq.get_out_unlink_filename();
					long out_run_time = fq.get_out_run_time();

					System.out.println(myID + "->rc=" + rc);
					System.out.println(myID + "->read success: " +  fq.path + "," + fq.qname + "," + " rc: " + rc + " msg: " + data + " seq: " + out_seq + " unlink_filename: " + out_unlink_filename + " run_timme(micro seconds): " + out_run_time);

					// input your jobs in here
					//
					//
					if( rc > 10) { // normal data
						rc = fq.commitXA();
						System.out.println("commit success: rc: " + rc);
					}
					else { // abnormal data
						rc = fq.cancelXA();
						System.out.println("cancel success: rc: " + rc);
						break;
					}
					continue;
				}
			}
		}
		catch( Exception e) {
			System.out.println( "Exception is caught.");
		}

	}
 
	public static void main(String[] args)  {
		int rc;
		int myid = 0; /* unique id : from 0 ~ */

		// make a FileQueueJNI instance with naming test.
		// 3-th argument is loglevel. (0: trace, 1: debug, 2: info, 3: Warning, 4: error, 5: emerg, 6: request)
		// Use 1 in dev and 4 real.
		FileQueueJNI fq_obj = new FileQueueJNI( myid, "/tmp/jni_TestDeQ.log", 4, "/home/ums/fq/enmq", "TST");

		rc = fq_obj.open();
		if( rc < 0 ) {
			System.out.println("open failed: " + fq_obj.logname + "," + fq_obj.path + "," + fq_obj.qname + "," + " rc: " + rc);
			return;
		}
		System.out.println("open success: " + fq_obj.logname + "," + fq_obj.path + "," + fq_obj.qname + "," + " rc: " + rc);

		int threads = 3;
		for(int i=0; i<threads; i++) {
			Thread object = new Thread( new MultiThread_DeQ_Demo( fq_obj ) );
			object.start();
		}

		// test.close();
	} 
} // class block end.
