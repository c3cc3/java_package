// Java code for thread creation by extending
// the Thread class

import com.clang.fq.*;

class MultithreadingDemo_deQ extends Thread {
	static {
        System.loadLibrary("jfq"); // Load native library at runtime
                                   // hello.dll (Windows) or libhello.so (Unixes)
        System.loadLibrary("fq");
    }
    private FileQueueJNI fq;

    public MultithreadingDemo_deQ (FileQueueJNI fq_obj) {
        this.fq = fq_obj;
    }

	// @Override
    public void run()
    {
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
                    // System.out.println( myID + "->empty: " + fq.path + "," + fq.qname + "," + " rc: " + rc);

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

                    // System.out.println(myID + "->rc=" + rc);
                    // System.out.println(myID + "->read success: " +  fq.path + "," + fq.qname + "," + " rc: " + rc + " msg: " + data + " seq: " + out_seq + " unlink_filename: " + out_unlink_filename + " run_timme(micro seconds): " + out_run_time);
					System.out.println("seq=" + out_seq);

					data.trim();
                    if( data.length() < 100 ) {
                        System.out.println(myID + "lenght is 0.");
                        System.exit(0);
                    }

                    // input your jobs in here
                    //
                    //
                    if( rc > 10) { // normal data
                        rc = fq.commitXA();
                        // System.out.println("commit success: rc: " + rc);
                    }
                    else { // abnormal data
                        rc = fq.cancelXA();
                        // System.out.println("cancel success: rc: " + rc);
                        break;
                    }
                    continue;
                }
            }
        }
        catch (Exception e) {
            // Throwing an exception
            System.out.println("Exception is caught");
        }
    }
}
class MultithreadingDemo_enQ extends Thread {
    static {
        System.loadLibrary("jfq"); // Load native library at runtime
                                   // hello.dll (Windows) or libhello.so (Unixes)
        System.loadLibrary("fq");
    }
    private FileQueueJNI fq;

    public MultithreadingDemo_enQ (FileQueueJNI fq_obj) {
        this.fq = fq_obj;
    }

    // @Override
    public void run()
    {
        int rc;
        long myID = Thread.currentThread().getId();
        try {
            while(true) {
                float usage;
                usage = fq.usage();
                // System.out.println("Current Usage: " + usage);


                // String kind_media = "LM"; /* kind_media  kakao:KA */
                String kind_media = "SM"; /* kind_media  kakao:KA */
                // String kind_media = "PU"; /* kind_media  Push:PU */
                // String kind_media = "EM"; /* kind_media  Email:EM */
                // String kind_media = "SM"; /* kind_media  SMS:Short Message */
                String phone_no = "01072021516";
                String send_msg = "01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567";
                String template = "Hello %var1% ! my name is %var2%________________________________________________________________________________________________";
                String var_data = "Choi|Gwisang";

                String SendData = kind_media+phone_no+send_msg+template+var_data;

                // System.out.println( "Send Data: " + SendData );
                rc = fq.write( SendData );
                if( rc < 0 ) {
                    System.out.println("Write failed: " + fq.path + "," + fq.qname + "," + " rc: " + rc);
                    fq.close();
                    return;
                }
                else if( rc == 0 ) {
                    System.out.println("full: " + fq.path + "," + fq.qname + "," + " rc: " + rc);
                    fq.close();
                    return;
                }
				 else {
                    long out_seq = fq.get_out_seq();
                    long out_run_time = fq.get_out_run_time();

                    // System.out.println("Write success: " +  fq.path + "," + fq.qname + "," + " rc: " + rc + "," + " seq: " + out_seq + " run_time(micro seconds): " + out_run_time);
                }

				try {
					Thread.sleep(1); // Pause for 1 second
				}
				catch(InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
            }
        }
        catch (Exception e) {
            // Throwing an exception
            System.out.println("Exception is caught");
        }
    }
}

 
// Main Class
public class Multithread_example_share {
    public static void main(String[] args)
    {
		int rc;
		int myid=0;

		System.out.println("args.length=" + args.length);
		for( int i=0; i<args.length; i++) {
			System.out.println(String.format("Command line argument %d is %s.", i , args[i]));
		}

		if( args.length != 2 ) {
			System.out.println("Usage  : $ java Multithread_example [qpath] [qname] <enter>\n");
			return;
		}
		String qpath = args[0];
		String qname = args[1];

		// make a FileQueueJNI instance with naming test.
        // 3-th argument is loglevel. (0: trace, 1: debug, 2: info, 3: Warning, 4: error, 5: emerg, 6: request)
        // Use 1 in dev and 4 real.
        FileQueueJNI fq_obj1 = new FileQueueJNI( myid, "/tmp/jni_TestDeQ.log", 4, qpath, qname);
		// FileQueueJNI fq_obj2 = new FileQueueJNI( myid, "/tmp/jni_thread.log", 4, qpath, qname);
        // FileQueueJNI fq_obj3 = new FileQueueJNI( myid, "/tmp/jni_TestDeQ.log", 4, qpath, qname);
		// FileQueueJNI fq_obj4 = new FileQueueJNI( myid, "/tmp/jni_thread.log", 4, qpath, qname);

        rc = fq_obj1.open();
        if( rc < 0 ) {
            System.out.println("open failed: " + fq_obj1.logname + "," + fq_obj1.path + "," + fq_obj1.qname + "," + " rc: " + rc);
            return;
        }
        System.out.println("open success: " + fq_obj1.logname + "," + fq_obj1.path + "," + fq_obj1.qname + "," + " rc: " + rc);

		/* fqueue object sharing model */
        MultithreadingDemo_deQ de_object1 = new MultithreadingDemo_deQ( fq_obj1 );
        de_object1.start();
        MultithreadingDemo_deQ de_object2 = new MultithreadingDemo_deQ( fq_obj1 );
        de_object2.start();

        MultithreadingDemo_enQ en_object3 = new MultithreadingDemo_enQ( fq_obj1 );
        en_object3.start();
        MultithreadingDemo_enQ en_object4 = new MultithreadingDemo_enQ( fq_obj1 );
        en_object4.start();
    }
}
