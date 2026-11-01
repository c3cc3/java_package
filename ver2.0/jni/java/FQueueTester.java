import java.io.*;
import com.clang.socket.FileQueueSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;

/** FileQueue JNI tester, direct API (OPERATION=1,2). Java 17+. */
public final class FQueueTester {
    private static final AtomicBoolean RUN = new AtomicBoolean(true);
    private static final LongAdder OK = new LongAdder(), FULL = new LongAdder(), ERR = new LongAdder(), EMPTY = new LongAdder();
    private static volatile boolean started;
    private static volatile long startNs;
    private static final int MAX_MESSAGE = 65532;
    private static final String[] OPS = {"", "enqueue(producer) - Direct", "dequeue(consumer) - Direct", "enqueue(producer) - Network", "dequeue(consumer) - Network"};

    static final class Config {
        int operation=1, messageKind=1, deliveryType=1, deliveryItems=1, targetTps=100;
        int messageSize=4096, bufferSize=65536, logLevel=4, saveToFile=0, gssiRandomLength=32;
        String queuePath=System.getenv().getOrDefault("FQ_DATA_HOME", "/sendq1/enmq");
        String queueName="TST", logFile="/tmp/tester_en.log", messageFile="";
        String saveFilename="/tmp/tester.de.message";
        String serverIp="", serverPort="7777";
        static Config load(Path file) throws IOException {
            Config c=new Config();
            int lineNo=0;
            for (String line:Files.readAllLines(file,StandardCharsets.UTF_8)) {
                lineNo++; line=line.trim();
                if(line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                int eq=line.indexOf('=');
                if(eq<1) throw new IllegalArgumentException("config line "+lineNo+": expected KEY=VALUE");
                String k=line.substring(0,eq).trim(), v=line.substring(eq+1).trim();
                try {
                    switch(k) {
                        case "OPERATION" -> c.operation=Integer.parseInt(v);
                        case "MESSAGE_KIND" -> c.messageKind=Integer.parseInt(v);
                        case "DELIVERY_TYPE" -> c.deliveryType=Integer.parseInt(v);
                        case "DELIVERY_ITEMS" -> c.deliveryItems=Integer.parseInt(v);
                        case "TARGET_TPS" -> c.targetTps=Integer.parseInt(v);
                        case "MESSAGE_SIZE" -> c.messageSize=Integer.parseInt(v);
                        case "BUFFER_SIZE" -> c.bufferSize=Integer.parseInt(v);
                        case "LOG_LEVEL" -> c.logLevel=Integer.parseInt(v);
                        case "SAVE_TO_FILE" -> c.saveToFile=Integer.parseInt(v);
                        case "QUEUE_PATH" -> c.queuePath=v;
                        case "QUEUE_NAME" -> c.queueName=v;
                        case "LOG_FILE" -> c.logFile=v;
                        case "MESSAGE_FILE" -> c.messageFile=v;
                        case "GSSI_RANDOM_LENGTH" -> c.gssiRandomLength=Integer.parseInt(v);
                        case "SAVE_FILENAME" -> c.saveFilename=v;
                        case "SERVER_IP" -> c.serverIp=v;
                        case "SERVER_PORT" -> c.serverPort=v;
                        default -> throw new IllegalArgumentException("Unknown config key: "+k);
                    }
                } catch(NumberFormatException ex) {throw new IllegalArgumentException("config line "+lineNo+": invalid number for "+k,ex);}
            }
            c.validate(); return c;
        }
        void validate() {
            if(gssiRandomLength<1||gssiRandomLength>256) throw new IllegalArgumentException("GSSI_RANDOM_LENGTH must be 1..256");
            if(operation<1||operation>4) throw new IllegalArgumentException("OPERATION must be 1..4");
            if(messageKind<1||messageKind>3 || deliveryType<1||deliveryType>2 || deliveryItems<1 || targetTps<1 || messageSize<1 || messageSize>MAX_MESSAGE || bufferSize<1 || logLevel<0 || logLevel>4 || saveToFile<0 || saveToFile>1)
                throw new IllegalArgumentException("Invalid configuration value");
            if(operation>=3) {
                if(serverIp.isBlank()) throw new IllegalArgumentException("SERVER_IP required");
                try {int port=Integer.parseInt(serverPort);if(port<1||port>65535) throw new NumberFormatException();}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid SERVER_PORT");}
            }
            if(queuePath.isBlank() || queueName.isBlank()) throw new IllegalArgumentException("QUEUE_PATH/QUEUE_NAME required");
            if((operation==1 || operation==3) && messageKind==2 && messageFile.isBlank()) throw new IllegalArgumentException("MESSAGE_FILE required for GSSI mode");
        }
    }
    private static String ask(BufferedReader in,String prompt,String def) throws IOException {
        System.out.print(prompt+" ["+def+"]: "); String s=in.readLine();
        if(s==null) throw new EOFException("stdin closed");
        return s.trim().isEmpty()?def:s.trim();
    }
    private static int askInt(BufferedReader in,String prompt,int def) throws IOException {return Integer.parseInt(ask(in,prompt,""+def));}
    private static Config interactive(BufferedReader in) throws IOException {
        Config c=new Config();
        c.operation=askInt(in,"Operation (1=Direct EN, 2=Direct DE, 3=Network EN, 4=Network DE)",1);
        c.queuePath=ask(in,"Queue path",c.queuePath); c.queueName=ask(in,"Queue name",c.queueName);
        c.deliveryType=askInt(in,"Delivery (1=fixed, 2=continuous)",2);
        if(c.deliveryType==1) c.deliveryItems=askInt(in,"Number of items",1000);
        c.targetTps=askInt(in,"Target TPS",100);
        if(c.operation==1 || c.operation==3) {
            c.messageKind=askInt(in,"Message (1=random, 2=GSSI file, 3=direct input)",1);
            if(c.messageKind==2) c.messageFile=ask(in,"Message file","");
            else if(c.messageKind==1) c.messageSize=askInt(in,"Message bytes",4096);
        } else {
            c.bufferSize=askInt(in,"Receive buffer size",65536);
            c.saveToFile=askInt(in,"Save to file (0=no, 1=yes)",0);
            if(c.saveToFile==1) c.saveFilename=ask(in,"Save filename",c.saveFilename);
        }
        if(c.operation>=3) {c.serverIp=ask(in,"Handler IP",c.serverIp.isBlank()?"127.0.0.1":c.serverIp);c.serverPort=ask(in,"Handler port",c.serverPort);}
        c.logFile=ask(in,"Log filename",c.logFile);
        c.logLevel=askInt(in,"Log level (0..4, default ERROR)",4);
        c.validate();return c;
    }
    private static void summary(Config c,String input) {
        System.out.println("\n==================================================\n FileQueue Tester - Execution Summary\n==================================================");
        System.out.println(" Input mode       : "+input);
        System.out.println(" Operation        : "+OPS[c.operation]);
        System.out.println(" Queue path       : "+c.queuePath+"\n Queue name       : "+c.queueName);
        if(c.operation>=3) System.out.println(" Handler          : "+c.serverIp+":"+c.serverPort);
        System.out.println(" Delivery mode    : "+(c.deliveryType==2?"continuous":"fixed ("+c.deliveryItems+")"));
        System.out.println(" Target TPS       : "+c.targetTps);
        if(c.operation==1 || c.operation==3) {
            System.out.println(" Message kind     : "+c.messageKind+"\n Message size     : "+c.messageSize);
            if(c.messageKind==2) {System.out.println(" GSSI file        : "+c.messageFile);System.out.println(" GSSI random len  : "+c.gssiRandomLength);}
        } else System.out.println(" Buffer size      : "+c.bufferSize+"\n Save to file     : "+(c.saveToFile==1?c.saveFilename:"no"));
        System.out.println(" Log file         : "+c.logFile+"\n Log level        : "+c.logLevel);
        System.out.println("==================================================");
    }
    private static void finalStats() {
        if(!started) return;
        double sec=Math.max(0.001,(System.nanoTime()-startNs)/1e9);
        System.out.printf(Locale.ROOT,"%n============================================%n Final Statistics%n============================================%nDelivery OK     : %d%nQueue Full      : %d%nDelivery Error  : %d%nDequeue Empty   : %d%nElapsed Seconds : %.3f%nAverage TPS     : %.2f%n============================================%n", OK.sum(),FULL.sum(),ERR.sum(),EMPTY.sum(),sec,OK.sum()/sec);
    }
    private static byte[] randomMessage(int length,Random r) {
        byte[] bytes=new byte[length];
        for(int i=0;i<length;i++) bytes[i]=(byte)('A'+r.nextInt(26));
        return bytes;
    }
    private static void pace(long start,long count,int tps) {
        // Absolute deadline prevents cumulative sleep drift; skip sleeping when behind.
        long target=start+(long)(count*(1_000_000_000.0/tps));
        while(RUN.get()) {
            long remaining=target-System.nanoTime();
            if(remaining<=0) return;
            LockSupport.parkNanos(Math.min(remaining,10_000_000L));
        }
    }
    private static void producer(FQueue fq,int id,Config c,BufferedReader input) throws IOException {
        byte[] template=null;
        if(c.messageKind==2) {
            template=Files.readAllBytes(Path.of(c.messageFile));
            if(template.length==0||template.length>MAX_MESSAGE) throw new IOException("Invalid GSSI template length: "+template.length);
        } else if(c.messageKind==3) {
            System.out.print("Message text: "); String line=input.readLine();
            if(line==null) throw new EOFException("Message text missing");
            template=line.getBytes(StandardCharsets.UTF_8);
            if(template.length==0||template.length>MAX_MESSAGE) throw new IOException("Invalid message length: "+template.length);
        }
        Random rnd=new Random(); GssiCacheProcessor gssi=new GssiCacheProcessor(c.gssiRandomLength);
        String gssiTemplate=c.messageKind==2?new String(template,StandardCharsets.UTF_8):null;
        long count=0, base=System.nanoTime();
        while(RUN.get() && (c.deliveryType==2||count<c.deliveryItems)) {
            byte[] data=c.messageKind==1?randomMessage(c.messageSize,rnd):
                    c.messageKind==2?gssi.renderBytes(gssiTemplate):template;
            if(data.length>MAX_MESSAGE) throw new IOException("Expanded GSSI message too large: "+data.length);
            FQueue.EnqueueResult result=fq.enQueue(id,data,FQueue.EN_NORMAL_MODE);
            if(result==null) {ERR.increment();}
            else if(result.rc>0) OK.increment();
            else if(result.rc==0) FULL.increment();
            else {ERR.increment(); if(result.rc==-99) {System.err.println("Queue manager stopped");break;}}
            count++; pace(base,count,c.targetTps);
        }
    }
    private static void consumer(FQueue fq,int id,Config c) throws IOException {
        OutputStream out=null;
        try {
            if(c.saveToFile==1) out=new BufferedOutputStream(Files.newOutputStream(Path.of(c.saveFilename),StandardOpenOption.CREATE,StandardOpenOption.APPEND));
            long count=0,base=System.nanoTime();
            while(RUN.get() && (c.deliveryType==2||count<c.deliveryItems)) {
                FQueue.FQueueMessage msg=fq.deQueueXA(id);
                if(msg==null) {EMPTY.increment();LockSupport.parkNanos(10_000_000L);continue;}
                if(msg.data==null || msg.data.length>c.bufferSize) {
                    ERR.increment();
                    try {fq.cancel(msg);} catch(Exception e){System.err.println("Cancel failed: "+e);}
                    System.err.println("Message exceeds receive buffer; stopping to avoid repeated XA cancel");break;
                }
                // Commit only after output is successfully written. File flush is not fsync.
                try {
                    if(out!=null) {out.write(msg.data);out.write('\n');out.flush();}
                    int rc=fq.commit(msg);
                    if(rc>=0) OK.increment();
                    else {ERR.increment();System.err.println("Commit failed rc="+rc);break;}
                } catch(Exception e) {
                    ERR.increment();
                    try {fq.cancel(msg);} catch(Exception ce){System.err.println("Cancel failed: "+ce);}
                    throw e;
                }
                count++;pace(base,count,c.targetTps);
            }
        } finally {if(out!=null)out.close();}
    }
    // Agent_socket.java pattern: reconnect only on transport errors; retry FULL unchanged.
    private static FileQueueSocket connect(Config c) throws IOException {
        FileQueueSocket s = new FileQueueSocket(c.serverIp, Integer.parseInt(c.serverPort),
                c.queuePath, c.queueName, 5000, 1000, 1024 * 1024);
        s.open();
        return s;
    }
    private static void delay(long ms) {
        long until = System.nanoTime() + ms * 1_000_000L;
        while (RUN.get() && System.nanoTime() < until)
            LockSupport.parkNanos(Math.min(10_000_000L, until - System.nanoTime()));
    }
    private static void networkProducer(Config c, BufferedReader input) throws IOException {
        byte[] template=null;
        if(c.messageKind==2) {
            template=Files.readAllBytes(Path.of(c.messageFile));
            if(template.length==0||template.length>MAX_MESSAGE) throw new IOException("Invalid GSSI template length: "+template.length);
        } else if(c.messageKind==3) {
            System.out.print("Message text: "); String line=input.readLine();
            if(line==null) throw new EOFException("Message missing");
            template=line.getBytes(StandardCharsets.UTF_8);
            if(template.length==0||template.length>MAX_MESSAGE) throw new IOException("Invalid message length");
        }
        FileQueueSocket socket=null;
        try {
            Random rnd=new Random(); GssiCacheProcessor gssi=new GssiCacheProcessor(c.gssiRandomLength);
            String gssiTemplate=c.messageKind==2?new String(template,StandardCharsets.UTF_8):null;
            long count=0,base=System.nanoTime();
            while(RUN.get() && (c.deliveryType==2||count<c.deliveryItems)) {
                byte[] data=c.messageKind==1?randomMessage(c.messageSize,rnd):
                        c.messageKind==2?gssi.renderBytes(gssiTemplate):template;
                if(data.length>MAX_MESSAGE) throw new IOException("Expanded GSSI message too large: "+data.length);
                String message=new String(data,StandardCharsets.UTF_8);
                if(message.isEmpty()) throw new IOException("Empty message");
                // Keep exactly the same message while FULL; never advance count.
                while(RUN.get()) {
                    try {
                        if(socket==null) socket=connect(c);
                        FileQueueSocket.Result r=socket.write(message);
                        if(r.status()==FileQueueSocket.Status.OK) {OK.increment();break;}
                        if(r.status()==FileQueueSocket.Status.FULL) {FULL.increment();delay(100);continue;}
                        ERR.increment(); throw new IOException("ENQU rejected: "+r.serverResult());
                    } catch(IOException | IllegalStateException e) {
                        if(!RUN.get()) break;
                        ERR.increment();
                        System.err.println("Network ENQU failure; reconnecting (delivery uncertain; duplicate possible): "+e);
                        if(socket!=null) socket.close();
                        socket=null;delay(1000);
                    }
                }
                if(!RUN.get()) break;
                count++;pace(base,count,c.targetTps);
            }
        } finally {if(socket!=null) socket.close();}
    }
    private static void networkConsumer(Config c) throws IOException {
        FileQueueSocket socket=null;
        OutputStream out=null;
        try {
            if(c.saveToFile==1) out=new BufferedOutputStream(Files.newOutputStream(Path.of(c.saveFilename),StandardOpenOption.CREATE,StandardOpenOption.APPEND));
            long count=0,base=System.nanoTime();
            while(RUN.get() && (c.deliveryType==2||count<c.deliveryItems)) {
                try {
                    if(socket==null) socket=connect(c);
                    FileQueueSocket.Result r=socket.read();
                    if(r.status()==FileQueueSocket.Status.EMPTY) {EMPTY.increment();delay(10);continue;}
                    if(r.status()!=FileQueueSocket.Status.OK) {
                        ERR.increment();throw new IOException("DEQU rejected: "+r.serverResult());
                    }
                    byte[] data=r.message().getBytes(StandardCharsets.UTF_8);
                    if(data.length>c.bufferSize) {ERR.increment();throw new IOException("Message exceeds BUFFER_SIZE; already dequeued");}
                    if(out!=null) {out.write(data);out.write('\n');out.flush();}
                    OK.increment();count++;pace(base,count,c.targetTps);
                } catch(IOException | IllegalStateException e) {
                    if(!RUN.get()) break;
                    ERR.increment();System.err.println("Network DEQU failure; reconnecting: "+e);
                    if(socket!=null) socket.close();socket=null;delay(1000);
                }
            }
        } finally {if(out!=null)out.close();if(socket!=null)socket.close();}
    }
    public static void main(String[] args) {
        if(args.length>1){System.err.println("Usage: java FQueueTester [tester.conf]");System.exit(2);}
        BufferedReader in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
        FQueue fq=null;int queueId=-1;boolean loggerOpen=false;
        try {
            Config c=args.length==1?Config.load(Path.of(args[0])):interactive(in);
            summary(c,args.length==1?"configuration file: "+args[0]:"interactive");
            System.out.print("Proceed? (y/N) ");String answer=in.readLine();
            if(!"y".equals(answer)){System.out.println("Cancelled.");return;}
            // Load JNI only after user confirmation; shutdown hook prints stats even on Ctrl+C.
            Runtime.getRuntime().addShutdownHook(new Thread(()->{RUN.set(false);finalStats();},"tester-shutdown"));
            if(c.operation<=2) {
                fq=new FQueue();
                if(fq.openLogger(c.logFile,c.logLevel)<=0) throw new IOException("openLogger failed");
                loggerOpen=true;
                queueId=fq.openQueue(c.queuePath,c.queueName);
                if(queueId<0) throw new IOException("openQueue failed: "+c.queuePath+"/"+c.queueName);
                System.out.println("Started. Press Ctrl+C to stop.");
                started=true;startNs=System.nanoTime();
                if(c.operation==1) producer(fq,queueId,c,in);else consumer(fq,queueId,c);
            } else {
                System.out.println("Network mode. Press Ctrl+C to stop.");
                started=true;startNs=System.nanoTime();
                if(c.operation==3) networkProducer(c,in);else networkConsumer(c);
            }
        } catch(Exception|UnsatisfiedLinkError e){System.err.println("FQueueTester ERROR: "+e);ERR.increment();}
        finally {
            RUN.set(false);
            if(fq!=null) {
                if(queueId>=0) try{fq.closeQueue(queueId);}catch(Exception e){System.err.println("closeQueue: "+e);}
                if(loggerOpen) try{fq.closeLogger();}catch(Exception e){System.err.println("closeLogger: "+e);}
            }
            // Final statistics are emitted by the shutdown hook exactly once.
        }
    }
}
