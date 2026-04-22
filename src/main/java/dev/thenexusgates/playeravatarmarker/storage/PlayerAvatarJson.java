package dev.thenexusgates.playeravatarmarker;

import java.util.LinkedHashMap;
import java.util.Map;

final class PlayerAvatarJson {

    private final Map<String, String> values;

    private PlayerAvatarJson(Map<String, String> values) {
        this.values = values;
    }

    static PlayerAvatarJson parseObject(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON content is missing");
        }
        Parser parser = new Parser(json);
        return new PlayerAvatarJson(parser.parseObject());
    }

    String getString(String key, String fallback) {
        String value = values.get(key);
        return value != null ? value : fallback;
    }

    int getInt(String key, int fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    boolean getBoolean(String key, boolean fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    Map<String, String> values() {
        return values;
    }

    static String writeObject(LinkedHashMap<String, Object> values) {
        StringBuilder builder = new StringBuilder(values.size() * 40);
        builder.append("{\n");
        int index = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            builder.append("  ")
                    .append(quote(entry.getKey()))
                    .append(": ");
            appendValue(builder, entry.getValue());
            if (++index < values.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("}\n");
        return builder.toString();
    }

    private static void appendValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
            return;
        }
        if (value instanceof String stringValue) {
            builder.append(quote(stringValue));
            return;
        }
        if (value instanceof Boolean || value instanceof Number) {
            builder.append(value);
            return;
        }
        throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass().getName());
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04X", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    private static final class Parser {

        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source;
        }

        private Map<String, String> parseObject() {
            skipWhitespace();
            expect('{');
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                skipWhitespace();
                ensureFullyConsumed();
                return result;
            }

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                String value = parseValue();
                result.put(key, value);
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    continue;
                }
                if (peek('}')) {
                    index++;
                    break;
                }
                throw error("Expected ',' or '}'");
            }

            skipWhitespace();
            ensureFullyConsumed();
            return result;
        }

        private String parseValue() {
            if (peek('"')) {
                return parseString();
            }
            int start = index;
            while (index < source.length()) {
                char ch = source.charAt(index);
                if (ch == ',' || ch == '}') {
                    break;
                }
                if (ch == '{' || ch == '[' || ch == ']' || ch == ':') {
                    throw error("Nested JSON values are not supported");
                }
                index++;
            }
            String token = source.substring(start, index).trim();
            if (token.isEmpty()) {
                throw error("Expected a value");
            }
            return token;
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < source.length()) {
                char ch = source.charAt(index++);
                if (ch == '"') {
                    return builder.toString();
                }
                if (ch != '\\') {
                    builder.append(ch);
                    continue;
                }
                if (index >= source.length()) {
                    throw error("Unterminated escape sequence");
                }
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> builder.append(parseUnicodeEscape());
                    default -> throw error("Unsupported escape sequence: \\" + escaped);
                }
            }
            throw error("Unterminated string literal");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > source.length()) {
                throw error("Incomplete unicode escape");
            }
            int value = 0;
            for (int offset = 0; offset < 4; offset++) {
                char ch = source.charAt(index + offset);
                int digit = Character.digit(ch, 16);
                if (digit < 0) {
                    throw error("Invalid unicode escape");
                }
                value = (value << 4) | digit;
            }
            index += 4;
            return (char) value;
        }

        private void ensureFullyConsumed() {
            if (index != source.length()) {
                throw error("Unexpected trailing content");
            }
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private boolean peek(char expected) {
            return index < source.length() && source.charAt(index) == expected;
        }

        private void expect(char expected) {
            if (!peek(expected)) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at index " + index);
        }
    }
}