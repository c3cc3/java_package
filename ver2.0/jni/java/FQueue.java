public class FQueue {
    static {
        System.loadLibrary("fqueue_jni");
    }

    public static final int EN_NORMAL_MODE = 0;
    public static final int EN_CIRCULAR_MODE = 1;
    public static final int EN_FULL_BACKUP_MODE = 2;

    public native int openLogger(String logFile, int logLevel);
    public native int closeLogger();

    public native int openQueue(String path, String qname);
    public native int closeQueue(int queueId);

    public native EnqueueResult enQueue(
        int queueId, byte[] data, int mode
    );

    public native FQueueMessage deQueueXA(int queueId);

    public native int commit(FQueueMessage message);
    public native int cancel(FQueueMessage message);

    public static class EnqueueResult {
        public int rc;
        public long seq;
        public long runTime;

        public EnqueueResult(int rc, long seq, long runTime) {
            this.rc = rc;
            this.seq = seq;
            this.runTime = runTime;
        }
    }

    public static class FQueueMessage {
        public int queueId;
        public byte[] data;
        public long seq;
        public long runTime;
        public String unlinkFilename;

        public FQueueMessage(
            int queueId,
            byte[] data,
            long seq,
            long runTime,
            String unlinkFilename
        ) {
            this.queueId = queueId;
            this.data = data;
            this.seq = seq;
            this.runTime = runTime;
            this.unlinkFilename = unlinkFilename;
        }
    }
}
