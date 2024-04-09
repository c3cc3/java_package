/*
** Warning: max buffer size is 65536
*/
import java.io.IOException;

public class TestWrite {
	// Test Driver
	public static void main(String[] args) {
		int rc;

		System.out.println("args.length=" + args.length);
		for( int i=0; i<args.length; i++) {
			System.out.println(String.format("Command line argument %d is %s.", i , args[i]));
		}

		if( args.length != 4 ) {
			System.out.println("Usage  : $ java TestFQXA [ip] [port] [qpath] [qname] <enter>\n");
			System.out.println("Example: $ java TestFQXA 172.30.1.31 7777 /ums/enmq TST <enter>\n");
			return;
		}

		String ip = args[0];
		String port = args[1];
		String qpath = args[2];
		String qname = args[3];

		try {

			FileQueueSocket test = new FileQueueSocket( ip, Integer.parseInt(port), qpath, qname);

			rc = test.open();
			if( rc < 0 ) {
				System.out.println("open failed rc =" + rc);
				return;
			}
			System.out.println("open success: rc= "  + rc);

			int deQ_count=0;
			for(;;) { // polling file queue.
				rc = test.write("This is a test data"); 
				if( rc < 0 ) {
					System.out.println("readXA failed: " + qpath + "," + qname + "," + " rc: " + rc);
					break;
				}
				else if( rc == 0 ) {
					System.out.println("full: " + qpath + "," + qname + "," + " rc: " + rc);

					try {
						Thread.sleep(1000); // Pause for 1 second
					}
					catch(InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
				else {
					System.out.println(" rc: " + rc);
					break;
				}
			}
			test.close(); // Close only when the process terminates.
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		return;
	} 
} // class block end.
