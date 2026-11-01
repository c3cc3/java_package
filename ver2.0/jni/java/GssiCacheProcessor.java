import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GSSI %cache key% substitution.
 * Java 17.
 */
public final class GssiCacheProcessor {

    private static final Pattern TOKEN = Pattern.compile(
        "%cache\\s+([A-Za-z0-9_.-]+)\\s*%",
        Pattern.CASE_INSENSITIVE
    );

    private static final char[] ALPHABET =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final int generatedLength;

    public GssiCacheProcessor(int generatedLength) {
        if (generatedLength < 1 || generatedLength > 256) {
            throw new IllegalArgumentException(
                "generatedLength must be 1..256"
            );
        }
        this.generatedLength = generatedLength;
    }

    // 메시지마다 새로운 캐시 생성
    public String render(String template) {
        return render(template, new HashMap<>());
    }

    // 외부 캐시를 공유하여 ACK/RESULT 등에서 같은 값 재사용
    public String render(String template, Map<String, String> cache) {
        Matcher matcher = TOKEN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);

            String value = cache.computeIfAbsent(
                key,
                k -> randomString()
            );

            matcher.appendReplacement(
                result,
                Matcher.quoteReplacement(value)
            );
        }

        matcher.appendTail(result);
        return result.toString();
    }

    public byte[] renderBytes(String template) {
        return render(template).getBytes(StandardCharsets.UTF_8);
    }

    private String randomString() {
        char[] chars = new char[generatedLength];

        for (int i = 0; i < chars.length; i++) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }

        return new String(chars);
    }
}
