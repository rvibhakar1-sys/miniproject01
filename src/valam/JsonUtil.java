package valam;

import java.util.*;

public class JsonUtil {

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < ' ') {
                        String hex = Integer.toHexString(ch);
                        sb.append("\\u");
                        for (int k = 0; k < 4 - hex.length(); k++) sb.append('0');
                        sb.append(hex);
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Parses a simple flat JSON object string into a Map of String key to String value.
     * Trims quotes from strings and converts numbers/booleans to string representations.
     */
    public static Map<String, String> parseSimpleObject(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null) return map;
        String trimmed = json.trim();
        if (trimmed.startsWith("{")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("}")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        int len = trimmed.length();
        int i = 0;
        while (i < len) {
            // Find key
            while (i < len && (Character.isWhitespace(trimmed.charAt(i)) || trimmed.charAt(i) == ',')) i++;
            if (i >= len) break;

            String key = null;
            if (trimmed.charAt(i) == '"') {
                int start = ++i;
                while (i < len && trimmed.charAt(i) != '"') {
                    if (trimmed.charAt(i) == '\\' && i + 1 < len) i++;
                    i++;
                }
                key = trimmed.substring(start, i);
                if (i < len) i++; // skip closing quote
            } else {
                int start = i;
                while (i < len && trimmed.charAt(i) != ':' && !Character.isWhitespace(trimmed.charAt(i))) i++;
                key = trimmed.substring(start, i);
            }

            // Find colon
            while (i < len && trimmed.charAt(i) != ':') i++;
            if (i < len) i++; // skip colon
            while (i < len && Character.isWhitespace(trimmed.charAt(i))) i++;

            // Find value
            if (i >= len) break;
            String val = null;
            if (trimmed.charAt(i) == '"') {
                int start = ++i;
                while (i < len && trimmed.charAt(i) != '"') {
                    if (trimmed.charAt(i) == '\\' && i + 1 < len) i++;
                    i++;
                }
                val = unescape(trimmed.substring(start, i));
                if (i < len) i++;
            } else if (trimmed.charAt(i) == '{' || trimmed.charAt(i) == '[') {
                char open = trimmed.charAt(i);
                char close = (open == '{') ? '}' : ']';
                int depth = 0;
                int start = i;
                while (i < len) {
                    char c = trimmed.charAt(i);
                    if (c == open) depth++;
                    else if (c == close) {
                        depth--;
                        if (depth == 0) { i++; break; }
                    }
                    i++;
                }
                val = trimmed.substring(start, i);
            } else {
                int start = i;
                while (i < len && trimmed.charAt(i) != ',' && trimmed.charAt(i) != '}') i++;
                val = trimmed.substring(start, i).trim();
            }

            if (key != null && val != null) {
                map.put(key, val);
            }
        }
        return map;
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); i += 2; break;
                    case '\\': sb.append('\\'); i += 2; break;
                    case '/': sb.append('/'); i += 2; break;
                    case 'b': sb.append('\b'); i += 2; break;
                    case 'f': sb.append('\f'); i += 2; break;
                    case 'n': sb.append('\n'); i += 2; break;
                    case 'r': sb.append('\r'); i += 2; break;
                    case 't': sb.append('\t'); i += 2; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                int code = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                                sb.append((char) code);
                                i += 6;
                            } catch (Exception e) {
                                sb.append(c); i++;
                            }
                        } else {
                            sb.append(c); i++;
                        }
                        break;
                    default:
                        sb.append(next); i += 2; break;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}

