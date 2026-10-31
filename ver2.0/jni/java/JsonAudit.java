import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Audits JSONL records against five validation columns of a shared six-column map. */
public final class JsonAudit {
    private JsonAudit() {
    }

    /** Minimal strict parser for flat JSON objects whose field values must be strings. */
    private static final class Parser {
        private final String text;
        private int position;

        private Parser(String text) {
            this.text = text;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + position);
        }

        private void whitespace() {
            while (position < text.length() &&
                   (text.charAt(position) == ' ' || text.charAt(position) == '\t' ||
                    text.charAt(position) == '\n' || text.charAt(position) == '\r')) {
                position++;
            }
        }

        private boolean take(char expected) {
            whitespace();
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) {
                throw error("expected '" + expected + "'");
            }
        }

        private int hex4() {
            if (position + 4 > text.length()) {
                throw error("incomplete unicode escape");
            }
            int result = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(text.charAt(position++), 16);
                if (digit < 0) {
                    throw error("invalid unicode escape");
                }
                result = (result << 4) | digit;
            }
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') {
                    return value.toString();
                }
                if (c < 0x20) {
                    throw error("unescaped control character");
                }
                if (c != '\\') {
                    if (Character.isHighSurrogate(c)) {
                        if (position >= text.length() ||
                            !Character.isLowSurrogate(text.charAt(position))) {
                            throw error("unpaired high surrogate");
                        }
                        value.append(c).append(text.charAt(position++));
                    } else if (Character.isLowSurrogate(c)) {
                        throw error("unpaired low surrogate");
                    } else {
                        value.append(c);
                    }
                    continue;
                }
                if (position >= text.length()) {
                    throw error("incomplete escape");
                }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case '"': value.append('"'); break;
                    case '\\': value.append('\\'); break;
                    case '/': value.append('/'); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'u':
                        int first = hex4();
                        if (Character.isHighSurrogate((char)first)) {
                            if (position + 2 > text.length() ||
                                text.charAt(position++) != '\\' || text.charAt(position++) != 'u') {
                                throw error("missing low surrogate escape");
                            }
                            int second = hex4();
                            if (!Character.isLowSurrogate((char)second)) {
                                throw error("invalid low surrogate");
                            }
                            value.append((char)first).append((char)second);
                        } else if (Character.isLowSurrogate((char)first)) {
                            throw error("unpaired low surrogate");
                        } else {
                            value.append((char)first);
                        }
                        break;
                    default: throw error("invalid escape sequence");
                }
            }
            throw error("unterminated string");
        }

        private Map<String, String> object() {
            Map<String, String> values = new LinkedHashMap<>();
            expect('{');
            if (take('}')) {
                whitespace();
                if (position != text.length()) {
                    throw error("trailing JSON data");
                }
                return values;
            }
            do {
                whitespace();
                String key = string();
                expect(':');
                whitespace();
                if (position >= text.length() || text.charAt(position) != '"') {
                    throw error("field " + key + " is not a string");
                }
                String value = string();
                if (values.putIfAbsent(key, value) != null) {
                    throw error("duplicate JSON key " + key);
                }
                if (take('}')) {
                    whitespace();
                    if (position != text.length()) {
                        throw error("trailing JSON data");
                    }
                    return values;
                }
                expect(',');
            } while (true);
        }
    }

    private static boolean audit(List<FieldMap.Field> fields, String json, int record) {
        Map<String, String> values;
        try {
            values = new Parser(json).object();
        } catch (IllegalArgumentException error) {
            System.err.println("record " + record + " FAIL: " + error.getMessage());
            return false;
        }
        int errors = 0;
        for (FieldMap.Field field : fields) {
            for (String error : FieldMap.validate(field, values.get(field.name))) {
                System.err.println("record " + record + " FAIL " + error);
                errors++;
            }
        }
        if (errors == 0) {
            System.out.println("PASS");
        }
        return errors == 0;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java JsonAudit MAP_FILE JSONL_FILE|-");
            System.exit(2);
        }
        try {
            List<FieldMap.Field> fields = FieldMap.load(Path.of(args[0]));
            BufferedReader input = args[1].equals("-")
                ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                : Files.newBufferedReader(Path.of(args[1]), StandardCharsets.UTF_8);
            int total = 0;
            int failed = 0;
            try (BufferedReader reader = input) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    total++;
                    if (!audit(fields, line.trim(), total)) {
                        failed++;
                    }
                }
            }
            System.err.printf("audited=%d failed=%d%n", total, failed);
            if (failed > 0) {
                System.exit(1);
            }
        } catch (Exception error) {
            System.err.println("ERROR: " + error.getMessage());
            System.exit(2);
        }
    }
}
