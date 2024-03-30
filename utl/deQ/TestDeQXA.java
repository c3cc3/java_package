import com.clang.fq.*;

/*
** Warning: max buffer size is 65536
*/
public class TestDeQXA {
   static {
      System.loadLibrary("jfq"); // Load native library at runtime
                                   // hello.dll (Windows) or libhello.so (Unixes)
	  System.loadLibrary("fq");
   }
	TestDeQXA () {

	}
 
	// Test Driver
	public static void main(String[] args) {
		int rc;

		System.out.println("args.length=" + args.length);
		for( int i=0; i<args.length; i++) {
			System.out.println(String.format("Command line argument %d is %s.", i , args[i]));
		}

		if( args.length != 4 ) {
			System.out.println("Usage  : $ java TestFQXA [qpath] [qname] [user_working_time] [deQ count] <enter>\n");
			System.out.println("Example: $ java TestFQXA /ums/enmq GW-REAL-REQ 1000 1 <enter>\n");
			return;
		}

		String qpath = args[0];
		String qname = args[1];
		String user_working_time_str = args[2];
		String count_str = args[3];

		int user_working_time_int = Integer.parseInt(user_working_time_str);
		int	count_int = Integer.parseInt(count_str);
			
		// make a FileQueueJNI instance with naming test.
		// 3-th argument is loglevel. (0: trace, 1: debug, 2: info, 3: Warning, 4: error, 5: emerg, 6: request)
		// Use 1 in dev and 4 real.
		FileQueueJNI test = new FileQueueJNI( 0, "/tmp/jni.log", 1, qpath, qname);

		rc = test.open();
		if( rc < 0 ) {
			System.out.println("open failed: " + test.logname + "," + qpath + "," + qname + "," + " rc: " + rc);
			return;
		}
		System.out.println("open success: " + test.logname + "," + test.path + "," + test.qname + "," + " rc: " + rc);

		int deQ_count=0;
		for(;;) { // polling file queue.

			rc = test.readXA(); // XA read 
			if( rc < 0 ) {
				if( rc == -99 ) {
					System.out.println("Manager Stop: " + test.path + "," + test.qname);
					try {
						Thread.sleep(1000); // Pause for 1 second
					}
					catch(InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
					continue;
				}
				else {
					System.out.println("readXA failed: " + test.path + "," + test.qname + "," + " rc: " + rc);
					break;
				}
			}
			else if( rc == 0 ) {
				System.out.println("empty: " + test.path + "," + test.qname + "," + " rc: " + rc);

				try {
                    Thread.sleep(1000); // Pause for 1 second
                }
                catch(InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
				if( deQ_count >= count_int ) break;
				else continue;
			}
			else {
				String data = test.get_out_msg();
				long out_seq = test.get_out_seq();
				String out_unlink_filename = test.get_out_unlink_filename();
				long out_run_time = test.get_out_run_time();

				System.out.println("read success: " +  test.path + "," + test.qname + "," + " rc: " + rc + " msg: " + data + " seq: " + out_seq + " unlink_filename: " + out_unlink_filename + " run_timme(micro seconds): " + out_run_time);
				System.out.println(" seq: " + out_seq);

				// input your jobs in here
				//
				//

				if( rc > 10) { // normal data
					rc = test.commitXA();
					System.out.println("commit success: rc: " + rc);

					try {
						Thread.sleep(user_working_time_int); // Pause for 1 second
					}
					catch(InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
				else { // abnormal data
					rc = test.commitXA();
					// rc = test.cancelXA();
					System.out.println("cancel success: rc: " + rc);
					break;
				}
	
				if( user_working_time_int > 0 ) {
					try {
						Thread.sleep(user_working_time_int); // Pause for 1 second
					}
					catch(InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}

				deQ_count++;
				continue;
			}
		}

		test.close(); // Close only when the process terminates.
		return;
	} 
} // class block end.
