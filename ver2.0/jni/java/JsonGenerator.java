import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Generates one JSON object per line from the same map used by JsonAudit. */
public final class JsonGenerator {
    private static final String DIGITS = "0123456789";
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ALNUM = LETTERS + DIGITS;
    private static final DateTimeFormatter DATE14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final SecureRandom random = new SecureRandom();

    private String randomText(String parameters, String alphabet) {
        String[] parts = parameters.split(",", -1);
        if (parts.length < 1 || parts.length > 2) {
            throw new IllegalArgumentException("RANDOM format: LENGTH[,PREFIX]");
        }
        int total = Integer.parseInt(parts[0].trim());
        String prefix = parts.length == 2 ? parts[1].trim() : "";
        int prefixBytes = prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (total < prefixBytes || total > FieldMap.MAX_VALUE_BYTES) {
            throw new IllegalArgumentException("invalid RANDOM length or PREFIX");
        }
        StringBuilder value = new StringBuilder(prefix);
        for (int i = prefixBytes; i < total; i++) {
            value.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return value.toString();
    }

    private String sequence(String parameters, long index) {
        String[] parts = parameters.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("SEQ format: START,STEP,WIDTH or PREFIX,START,WIDTH");
        }
        String first = parts[0].trim();
        String prefix = "";
        long start;
        long step = 1;
        if (first.matches("[0-9]+")) {
            start = Long.parseLong(first);
            step = Long.parseLong(parts[1].trim());
        } else {
            prefix = first;
            start = Long.parseLong(parts[1].trim());
        }
        int width = Integer.parseInt(parts[2].trim());
        if (width < 1 || width > 32 || start < 0 || step < 0) {
            throw new IllegalArgumentException("invalid SEQ parameters");
        }
        long number = Math.addExact(start, Math.multiplyExact(step, index - 1));
        return prefix + String.format(java.util.Locale.ROOT, "%0" + width + "d", number);
    }

    private String randomEnum(String validation) {
        if (!validation.startsWith("ENUM:")) {
            throw new IllegalArgumentException("RANDOM_ENUM requires ENUM validation");
        }
        String[] choices = validation.substring(5).split(",", -1);
        if (choices.length == 0) {
            throw new IllegalArgumentException("empty ENUM");
        }
        return choices[random.nextInt(choices.length)].trim();
    }

    private String generateValue(FieldMap.Field field, long index) {
        String rule = field.generator;
        if (rule.startsWith("FIXED:")) {
            return rule.substring(6);
        }
        if (rule.equals("EMPTY")) {
            return "";
        }
        if (rule.equals("NOW14")) {
            return LocalDateTime.now().format(DATE14);
        }
        if (rule.startsWith("SEQ:")) {
            return sequence(rule.substring(4), index);
        }
        if (rule.equals("RANDOM_ENUM")) {
            return randomEnum(field.validation);
        }
        if (rule.startsWith("RANDOM_NUM:")) {
            return randomText(rule.substring(11), DIGITS);
        }
        if (rule.startsWith("RANDOM_ALPHA:")) {
            return randomText(rule.substring(13), LETTERS);
        }
        if (rule.startsWith("RANDOM_ALNUM:")) {
            return randomText(rule.substring(13), ALNUM);
        }
        throw new IllegalArgumentException("unknown generator rule: " + rule);
    }

    /** Escape a Java string as a JSON string, including control characters. */
    public static String quote(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                result.append('\\').append(c);
            } else if (c < 0x20) {
                result.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int)c));
            } else {
                result.append(c);
            }
        }
        return result.append('"').toString();
    }

    private String generateRecord(List<FieldMap.Field> fields, long index) {
        StringBuilder json = new StringBuilder("{");
        for (FieldMap.Field field : fields) {
            String value = generateValue(field, index);
            List<String> errors = FieldMap.validate(field, value);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("record " + index + ": " + errors);
            }
            if (json.length() > 1) {
                json.append(',');
            }
            json.append(quote(field.name)).append(':').append(quote(value));
        }
        return json.append('}').toString();
    }

    /** Generate one response with business values overriding map defaults. */
    public String generate(List<FieldMap.Field> fields,
                           long index,
                           java.util.Map<String, String> overrides) {
        java.util.Set<String> allowed = new java.util.HashSet<>();
        for (FieldMap.Field field : fields) {
            allowed.add(field.name);
        }
        for (String key : overrides.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("override field not in map: " + key);
            }
        }
        StringBuilder json = new StringBuilder("{");
        for (FieldMap.Field field : fields) {
            String value = overrides.containsKey(field.name)
                ? overrides.get(field.name) : generateValue(field, index);
            List<String> errors = FieldMap.validate(field, value);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("invalid " + field.name + ": " + errors);
            }
            if (json.length() > 1) {
                json.append(',');
            }
            json.append(quote(field.name)).append(':').append(quote(value));
        }
        return json.append('}').toString();
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java JsonGenerator MAP_FILE [COUNT]");
            System.exit(2);
        }
        try {
            long count = args.length == 2 ? Long.parseLong(args[1]) : 1;
            if (count < 1 || count > 10_000_000) {
                throw new IllegalArgumentException("COUNT must be 1..10000000");
            }
            List<FieldMap.Field> fields = FieldMap.load(Path.of(args[0]));
            JsonGenerator generator = new JsonGenerator();
            for (long index = 1; index <= count; index++) {
                System.out.println(generator.generateRecord(fields, index));
            }
        } catch (Exception error) {
            System.err.println("ERROR: " + error.getMessage());
            System.exit(1);
        }
    }
}
