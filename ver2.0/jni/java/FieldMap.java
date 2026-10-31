import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared six-column map reader and field validator for Generator and Audit. */
public final class FieldMap {
    public static final int MAX_FIELDS = 128;
    public static final int MAX_VALUE_BYTES = 2047;

    public static final class Field {
        public final String name;
        public final String lengthType;
        public final int length;
        public final boolean required;
        public final String validation;
        public final String generator;

        private Field(String[] columns) {
            name = columns[0];
            lengthType = columns[1];
            length = Integer.parseInt(columns[2]);
            required = columns[3].equals("Y");
            validation = columns[4];
            generator = columns[5];
        }
    }

    private FieldMap() {
    }

    public static List<Field> load(Path path) throws IOException {
        List<Field> fields = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

        for (int lineNumber = 1; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] columns = line.split("\\|", -1);
            if (columns.length != 6) {
                throw mapError(path, lineNumber, "expected exactly 6 columns");
            }
            for (int i = 0; i < columns.length; i++) {
                columns[i] = columns[i].trim();
            }

            int length;
            try {
                length = Integer.parseInt(columns[2]);
            } catch (NumberFormatException error) {
                throw mapError(path, lineNumber, "invalid length");
            }
            if (columns[0].isEmpty() || columns[0].getBytes(StandardCharsets.UTF_8).length >= 128 ||
                !(columns[1].equals("EXACT") || columns[1].equals("MAX")) ||
                length < 1 || length > MAX_VALUE_BYTES ||
                !(columns[3].equals("Y") || columns[3].equals("N")) ||
                !(columns[4].equals("ANY") || columns[4].equals("DATETIME14") ||
                  columns[4].startsWith("ENUM:")) || columns[5].isEmpty()) {
                throw mapError(path, lineNumber, "invalid field definition");
            }
            if (!names.add(columns[0])) {
                throw mapError(path, lineNumber, "duplicate field: " + columns[0]);
            }
            if (fields.size() >= MAX_FIELDS) {
                throw mapError(path, lineNumber, "too many fields");
            }
            fields.add(new Field(columns));
        }
        return fields;
    }

    private static IOException mapError(Path path, int line, String message) {
        return new IOException(path + ":" + line + ": " + message);
    }

    public static List<String> validate(Field field, String value) {
        List<String> errors = new ArrayList<>();
        if (value == null) {
            if (field.required) {
                errors.add(field.name + ": missing");
            }
            return errors;
        }

        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (field.required && bytes == 0) {
            errors.add(field.name + ": empty");
        }
        if (field.lengthType.equals("EXACT") && bytes != field.length) {
            errors.add(field.name + ": byte length " + bytes + " (EXACT " + field.length + ")");
        }
        if (field.lengthType.equals("MAX") && bytes > field.length) {
            errors.add(field.name + ": byte length " + bytes + " (MAX " + field.length + ")");
        }
        if (field.validation.startsWith("ENUM:") && !enumContains(field.validation, value)) {
            errors.add(field.name + ": not in " + field.validation);
        }
        if (field.validation.equals("DATETIME14") && !validDateTime14(value)) {
            errors.add(field.name + ": invalid DATETIME14");
        }
        return errors;
    }

    public static boolean enumContains(String specification, String value) {
        if (!specification.startsWith("ENUM:")) {
            return false;
        }
        for (String item : specification.substring(5).split(",", -1)) {
            if (item.trim().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static boolean validDateTime14(String value) {
        if (!value.matches("[0-9]{14}")) {
            return false;
        }
        try {
            LocalDateTime.of(
                Integer.parseInt(value.substring(0, 4)),
                Integer.parseInt(value.substring(4, 6)),
                Integer.parseInt(value.substring(6, 8)),
                Integer.parseInt(value.substring(8, 10)),
                Integer.parseInt(value.substring(10, 12)),
                Integer.parseInt(value.substring(12, 14)));
            return true;
        } catch (DateTimeException error) {
            return false;
        }
    }
}
