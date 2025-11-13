package server;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple JSON utility for parsing and building JSON strings.
 * No external dependencies - basic string manipulation.
 */
public class JsonUtil {

    /**
     * Parse a simple JSON object into a Map.
     * Handles basic {"key":"value","key2":"value2"} format.
     */
    public static Map<String, String> parseObject(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return result;
        }

        // Remove outer braces
        json = json.trim();
        if (json.startsWith("{"))
            json = json.substring(1);
        if (json.endsWith("}"))
            json = json.substring(0, json.length() - 1);

        // Split by comma (simple approach - doesn't handle nested objects)
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = unquote(kv[0].trim());
                String value = unquote(kv[1].trim());
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * Build a JSON object from key-value pairs.
     */
    public static String buildObject(Object... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0)
                sb.append(",");
            sb.append("\"").append(keyValues[i]).append("\":");

            Object value = keyValues[i + 1];
            if (value instanceof String) {
                sb.append("\"").append(escape((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value instanceof String[]) {
                sb.append(buildArray((String[]) value));
            } else if (value instanceof int[]) {
                sb.append(buildArray((int[]) value));
            } else if (value instanceof double[]) {
                sb.append(buildArray((double[]) value));
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(escape(value.toString())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Build a JSON array from strings.
     */
    public static String buildArray(String[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append("\"").append(escape(values[i])).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Build a JSON array from integers.
     */
    public static String buildArray(int[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(values[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Build a JSON array from doubles.
     */
    public static String buildArray(double[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(String.format("%.1f", values[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Remove quotes from a string.
     */
    private static String unquote(String s) {
        if (s.startsWith("\""))
            s = s.substring(1);
        if (s.endsWith("\""))
            s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * Escape special characters for JSON.
     */
    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Quote a string for JSON (with escaping).
     */
    public static String quote(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + escape(s) + "\"";
    }
}