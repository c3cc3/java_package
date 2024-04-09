import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class FileQueueSocket {
	private Socket socket;
	private OutputStream out;
	private InputStream in;
	private String qPath;
	private String qName;
	private String sessionId;
	private String message;

	public FileQueueSocket(String serverAddress, int serverPort, String qPath, String qName ) throws IOException {
        this.socket = new Socket(serverAddress, serverPort);
        this.out = socket.getOutputStream();
        this.in = socket.getInputStream();
		this.qPath = qPath;
		this.qName = qName;
    }
	public int open() throws IOException {

		JSONObject requestJson = new JSONObject();

		requestJson.put("FQP_VERSION", "10");
		requestJson.put("SESSION_ID", "");
		requestJson.put("QUEUE_PATH", qPath);
		requestJson.put("QUEUE_NAME", qName);
		requestJson.put("ACK_MODE", "Y");
		requestJson.put("ACTION", "LINK");
		requestJson.put("MSG_LENGTH", 0);
		requestJson.put("MESSAGE", "");

        byte[] requestBytes = requestJson.toString().getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(4 + requestBytes.length);
        buffer.putInt(requestBytes.length);
        buffer.put(requestBytes);

        out.write(buffer.array());
        out.flush();

        byte[] responseHeader = new byte[4];
        in.read(responseHeader);
        int responseBodyLength = ByteBuffer.wrap(responseHeader).getInt();
        byte[] responseBody = new byte[responseBodyLength];
        in.read(responseBody);

        JSONObject responseJSON =  new JSONObject(new String(responseBody));
		
        String result = responseJSON.getString("RESULT");

		System.out.println("RESULT:" + result);

        if ("OK".equals(result)) {
            System.out.println("서버 응답이 성공했습니다.");
			this.sessionId = responseJSON.getString("SESSION_ID");
            System.out.println("SESSION_ID: " + this.sessionId );
			return 0;
        } else {
			return -1;
		}
	}

	public int read() throws IOException {
		
		// JSON 객체 생성
		JSONObject requestJson = new JSONObject();
		requestJson.put("SESSION_ID", this.sessionId);
		requestJson.put("FQP_VERSION", "10");
		requestJson.put("QUEUE_PATH", this.qPath);
		requestJson.put("QUEUE_NAME", this.qName);
		requestJson.put("ACK_MODE", "Y");
		requestJson.put("ACTION", "DEQU");
		requestJson.put("MSG_LENGTH", 0);
		requestJson.put("MESSAGE", "");

        byte[] requestBytes = requestJson.toString().getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(4 + requestBytes.length);
        buffer.putInt(requestBytes.length);
        buffer.put(requestBytes);

        out.write(buffer.array());
        out.flush();

        byte[] responseHeader = new byte[4];
        in.read(responseHeader);
        int responseBodyLength = ByteBuffer.wrap(responseHeader).getInt();
        byte[] responseBody = new byte[responseBodyLength];
        in.read(responseBody);
		
        JSONObject responseJSON =  new JSONObject(new String(responseBody));
		
        String result = responseJSON.getString("RESULT");

		System.out.println("RESULT:" + result);

        if ("OK".equals(result)) {
            System.out.println("서버 응답이 성공했습니다.");
            String message = responseJSON.optString("MESSAGE", "No Message");
            // System.out.println("서버로부터 받은 메시지: " + message);
			this.message = message;
			return message.length();
        } else if( "EMPTY".equals(result))  {
			System.out.println("큐에 데이터가 비어있습니다. 1초 대기 후 재요청하세요.");
			return 0;
		}
		else {
			System.out.println("서버 응답이 실패했습니다. 클라이언트 종료하세요.");
			return -1;
		}
	}
	public int write(String userMessage) throws IOException {
		
		// JSON 객체 생성
		JSONObject requestJson = new JSONObject();
		requestJson.put("SESSION_ID", this.sessionId);
		requestJson.put("FQP_VERSION", "10");
		requestJson.put("QUEUE_PATH", this.qPath);
		requestJson.put("QUEUE_NAME", this.qName);
		requestJson.put("ACK_MODE", "Y");
		requestJson.put("ACTION", "ENQU");
		requestJson.put("MSG_LENGTH", userMessage.length());
		requestJson.put("MESSAGE", userMessage);

        byte[] requestBytes = requestJson.toString().getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(4 + requestBytes.length);
        buffer.putInt(requestBytes.length);
        buffer.put(requestBytes);

        out.write(buffer.array());
        out.flush();

        byte[] responseHeader = new byte[4];
        in.read(responseHeader);
        int responseBodyLength = ByteBuffer.wrap(responseHeader).getInt();
        byte[] responseBody = new byte[responseBodyLength];
        in.read(responseBody);
		
        JSONObject responseJSON =  new JSONObject(new String(responseBody));
		
        String result = responseJSON.getString("RESULT");

		System.out.println("RESULT:" + result);

        if ("OK".equals(result)) {
			System.out.println("큐에 저장을 성공했습니다.");
			return userMessage.length();
        } else if( "FULL".equals(result))  {
			System.out.println("큐에 데이터가 꽉 차 있습니다(full). 1초 대기 후 재요청합니다.");
			return 0;
		}
		else {
			System.out.println("서버 응답이 실패했습니다. 클라이언트 종료하세요.");
			return -1;
		}
	}
	public String getmsg() {
		return this.message;
	}

    public void close() throws IOException {
        socket.close();
    }
}
