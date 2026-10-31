import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Shared, thread-safe map-based audit and response generator. */
public final class MapMessageCodec {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final Map<String, List<FieldMap.Field>> inputs = new HashMap<>();
    private final List<FieldMap.Field> ack, result;
    private final JsonGenerator generator = new JsonGenerator();
    private final AtomicLong sequence = new AtomicLong();

    public MapMessageCodec(Properties props) throws Exception {
        inputs.put("S", load(props, "map.sms"));
        inputs.put("L", load(props, "map.lms"));
        inputs.put("M", load(props, "map.mms"));
        ack = load(props, "map.ack");
        result = load(props, "map.result");
    }
    private static List<FieldMap.Field> load(Properties p, String key) throws Exception {
        String path = p.getProperty(key);
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return FieldMap.load(Path.of(path.trim()));
    }
    public Map<String,String> parse(String raw) { return JsonAudit.parseObject(raw); }
    public List<String> validate(Map<String,String> request) {
        String kind = request.get("MSG_KIND");
        List<FieldMap.Field> map = inputs.get(kind);
        if (map == null) return List.of("Missing/unsupported MSG_KIND: " + kind);
        return JsonAudit.validateObject(map, request);
    }
    public String generate(Map<String,String> request, String responseKind,
                           String code, String group, String reason) {
        List<FieldMap.Field> map = "ACK".equals(responseKind) ? ack : result;
        if (!"ACK".equals(responseKind) && !"RESULT".equals(responseKind))
            throw new IllegalArgumentException("Invalid response kind " + responseKind);
        String history = request.get("HISTORY_KEY");
        if (history == null || history.isBlank())
            throw new IllegalArgumentException("Missing HISTORY_KEY: cannot correlate response");
        Map<String,String> values = new HashMap<>();
        values.put("RESP_KIND", responseKind);
        values.put("RESULT_CODE", code);
        values.put("RESULT_GROUP_CODE", group);
        values.put("REASON", asciiReason(reason));
        values.put("MSG_KIND", request.getOrDefault("MSG_KIND", ""));
        values.put("HISTORY_KEY", history);
        values.put("SEND_TIME", LocalDateTime.now().format(TS));
        values.put("DONE_DATE", LocalDateTime.now().format(TS));
        for (String k : List.of("SERVICE", "CUSTOMER", "TELECOM_CODE", "SESSION_ID", "SESSION_NO")) {
            if (request.containsKey(k)) values.put(k, request.get(k));
        }
        Map<String,String> selected = new HashMap<>();
        for (FieldMap.Field f : map) if (values.containsKey(f.name)) selected.put(f.name, values.get(f.name));
        synchronized (generator) {
            return generator.generate(map, sequence.incrementAndGet(), selected);
        }
    }
    private static String asciiReason(String reason) {
        if (reason == null || reason.isBlank()) return "SUCCESS";
        StringBuilder b = new StringBuilder();
        for (int i=0; i<reason.length() && b.length()<32; i++) {
            char c=reason.charAt(i);
            if (c>=32 && c<=126) b.append(c);
        }
        return b.length()==0 ? "AUDIT_ERROR" : b.toString();
    }
}
