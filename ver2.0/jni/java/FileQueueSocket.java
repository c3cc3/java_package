package com.clang.socket;

import org.json.JSONObject;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * FileQueue Protocol v10 client. Not thread-safe: use one instance per worker.
 * Wire format: four-byte big-endian JSON byte count followed by UTF-8 JSON.
 * The protocol's MSG_LENGTH byte-vs-character interpretation must be checked
 * against the server implementation before production use.
 */
public final class FileQueueSocket implements AutoCloseable {
    public enum Status { OK, FULL, EMPTY, ERROR }

    public static final class Result {
        private final Status status;
        private final String message;
        private final String serverResult;

        private Result(Status status, String message, String serverResult) {
            this.status = status;
            this.message = message;
            this.serverResult = serverResult;
        }

        public Status status() { return status; }
        public String message() { return message; }
        public String serverResult() { return serverResult; }
        public boolean isOk() { return status == Status.OK; }
    }

    private static final String VERSION = "10";
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    private static final int DEFAULT_MAX_FRAME_BYTES = 1024 * 1024;

    private final String host;
    private final int port;
    private final String queuePath;
    private final String queueName;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxFrameBytes;

    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private String sessionId;

    public FileQueueSocket(String host, int port, String queuePath, String queueName) {
        this(host, port, queuePath, queueName,
                DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS,
                DEFAULT_MAX_FRAME_BYTES);
    }

    public FileQueueSocket(String host, int port, String queuePath, String queueName,
                           int connectTimeoutMs, int readTimeoutMs, int maxFrameBytes) {
        this.host = Objects.requireNonNull(host, "host");
        this.queuePath = Objects.requireNonNull(queuePath, "queuePath");
        this.queueName = Objects.requireNonNull(queueName, "queueName");
        if (host.isEmpty() || queuePath.isEmpty() || queueName.isEmpty()
                || port < 1 || port > 65535
                || connectTimeoutMs <= 0 || readTimeoutMs <= 0
                || maxFrameBytes < 4) {
            throw new IllegalArgumentException("Invalid FileQueue socket configuration");
        }
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxFrameBytes = maxFrameBytes;
    }

    /** Establish TCP connection, send LINK, and save SESSION_ID. */
    public synchronized void open() throws IOException {
        if (socket != null) throw new IllegalStateException("Already connected");
        Socket candidate = new Socket();
        try {
            candidate.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            candidate.setSoTimeout(readTimeoutMs);
            candidate.setTcpNoDelay(true);
            socket = candidate;
            input = candidate.getInputStream();
            output = candidate.getOutputStream();
            JSONObject response = exchange(request("LINK", "", 0));
            String result = response.optString("RESULT", "");
            if (!"OK".equals(result)) {
                throw new IOException("LINK rejected: " + result);
            }
            String newSession = response.optString("SESSION_ID", "");
            if (newSession.isEmpty()) {
                throw new IOException("LINK succeeded without SESSION_ID");
            }
            sessionId = newSession;
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    /** ENQU once. FULL is returned, not retried automatically. */
    public synchronized Result write(String message) throws IOException {
        requireOpen();
        Objects.requireNonNull(message, "message");
        int byteLength = message.getBytes(StandardCharsets.UTF_8).length;
        JSONObject response = exchange(request("ENQU", message, byteLength));
        return classify(response, false);
    }

    /** DEQU once. Returns the message as Result.message() on success. */
    public synchronized Result read() throws IOException {
        requireOpen();
        JSONObject response = exchange(request("DEQU", "", 0));
        return classify(response, true);
    }

    /** Retry ONLY an explicit FULL response. Other errors stop immediately. */
    public Result writeRetryOnFull(String message, long retryDelayMs)
            throws IOException, InterruptedException {
        if (retryDelayMs < 0) throw new IllegalArgumentException("retryDelayMs < 0");
        while (true) {
            Result result = write(message);
            if (result.status() != Status.FULL) return result;
            Thread.sleep(retryDelayMs);
        }
    }

    private JSONObject request(String action, String message, int messageLength) {
        JSONObject json = new JSONObject();
        json.put("FQP_VERSION", VERSION);
        json.put("SESSION_ID", sessionId == null ? "" : sessionId);
        json.put("QUEUE_PATH", queuePath);
        json.put("QUEUE_NAME", queueName);
        json.put("ACK_MODE", "Y");
        json.put("ACTION", action);
        json.put("MSG_LENGTH", messageLength);
        json.put("MESSAGE", message);
        return json;
    }

    private JSONObject exchange(JSONObject request) throws IOException {
        byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length > maxFrameBytes) {
            throw new IOException("Request too large: " + body.length);
        }
        byte[] header = ByteBuffer.allocate(4).putInt(body.length).array();
        try {
            output.write(header);
            output.write(body);
            output.flush();

            byte[] responseHeader = new byte[4];
            readFully(input, responseHeader);
            int length = ByteBuffer.wrap(responseHeader).getInt();
            if (length <= 0 || length > maxFrameBytes) {
                throw new IOException("Invalid response frame length: " + length);
            }
            byte[] responseBody = new byte[length];
            readFully(input, responseBody);
            return new JSONObject(new String(responseBody, StandardCharsets.UTF_8));
        } catch (SocketTimeoutException e) {
            // The server may have performed ENQU before the timeout.
            close();
            throw new IOException("Socket timeout; operation outcome is uncertain", e);
        } catch (IOException e) {
            close();
            throw e;
        } catch (RuntimeException e) {
            close();
            throw new IOException("Invalid FileQueue response", e);
        }
    }

    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int n = in.read(buffer, offset, buffer.length - offset);
            if (n < 0) throw new EOFException("Connection closed during frame reception");
            if (n == 0) continue;
            offset += n;
        }
    }

    private static Result classify(JSONObject response, boolean dequeue) {
        String code = response.optString("RESULT", "");
        if ("OK".equals(code)) {
            if (dequeue && !response.has("MESSAGE")) {
                return new Result(Status.ERROR, null, "MISSING_MESSAGE");
            }
            return new Result(Status.OK,
                    dequeue ? response.optString("MESSAGE", "") : null, code);
        }
        if ("FULL".equals(code)) return new Result(Status.FULL, null, code);
        if ("EMPTY".equals(code)) return new Result(Status.EMPTY, null, code);
        return new Result(Status.ERROR, null, code);
    }

    private void requireOpen() {
        if (socket == null || socket.isClosed() || sessionId == null) {
            throw new IllegalStateException("Call open() before ENQU/DEQU");
        }
    }

    /** Safe to call multiple times. To reconnect, call open() again explicitly. */
    @Override
    public synchronized void close() {
        sessionId = null;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        socket = null;
        input = null;
        output = null;
    }
}
