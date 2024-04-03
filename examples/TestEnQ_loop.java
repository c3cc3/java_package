import com.clang.fq.*;

public class TestEnQ_loop {
   static {
      System.loadLibrary("jfq"); // Load native library at runtime
                                   // hello.dll (Windows) or libhello.so (Unixes)
	  System.loadLibrary("fq");
   }
	TestEnQ_loop () {

	}
 
	// Test Driver
	public static void main(String[] args) {
		int rc;

		System.out.println("args.length=" + args.length);
		for( int i=0; i<args.length; i++) {
			System.out.println(String.format("Command line argument %d is %s.", i , args[i]));
		}

		if( args.length != 3 ) {
			System.out.println("Usage  : $ java TestFQXA [qpath] [qname] [count] <enter>\n");
			System.out.println("Example: $ java TestFQXA /ums/enmq GW-REAL-REQ 1 <enter>\n");
			return;
		}

		String qpath = args[0];
		String qname = args[1];
		String count_str = args[2];

		// make a FileQueueJNI instance with naming test.
		// 3-th argument is loglevel. (0: trace, 1: debug, 2: info, 3: Warning, 4: error, 5: emerg, 6: request)
		// Use 1 in dev and 4 real.
		FileQueueJNI test = new FileQueueJNI( 0, "/tmp/jni.log", 4, qpath, qname);

		rc = test.open();
		if( rc < 0 ) {
			System.out.println("open failed: " + test.logname + "," + test.path + "," + test.qname + "," + " rc: " + rc);
			return;
		}
		System.out.println("open success: " + test.logname + "," + test.path + "," + test.qname + "," + " rc: " + rc);

		int count_int = Integer.parseInt(count_str);

		for(int i=0; i < count_int; i++) { 
			String kind_media = "SM";
			// String kind_media = "LM";
			// String kind_media = "KA"; /* kakao */
			String phone_no = "01072021516";
			String send_msg = "01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567";
			String template = "Hello %var1% ! my name is %var2%________________________________________________________________________________________________";
			String var_data = "Choi|Gwisang";

			String SendData = kind_media+phone_no+send_msg+template+var_data;

			// System.out.println( "Send Data: " + SendData );
			rc = test.write( SendData );

			if( rc < 0 ) {
				System.out.println("Write failed: " + test.path + "," + test.qname + "," + " rc: " + rc);
				test.close();
				return;
			}
			else if( rc == 0 ) {
				System.out.println("full: " + test.path + "," + test.qname + "," + " rc: " + rc);
				try {
					Thread.sleep(1); // Pause for 1 second (1000)
				}
				catch(InterruptedException ex) {
				        Thread.currentThread().interrupt();
			    }
				continue;
				// test.close();
				// return;
			}
			else {
				long out_seq = test.get_out_seq();
				long out_run_time = test.get_out_run_time();

				// System.out.println("Write success: " +  test.path + "," + test.qname + "," + " rc: " + rc + "," + " seq: " + out_seq + " run_time(micro seconds): " + out_run_time);

				try {
					Thread.sleep(1); // Pause for 1 second (1000)
				}
				catch(InterruptedException ex) {
				        Thread.currentThread().interrupt();
			    }

				continue;
			}
		}
	} 
} // class block end.
