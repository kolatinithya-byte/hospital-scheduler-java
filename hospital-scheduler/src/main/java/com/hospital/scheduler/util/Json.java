package com.hospital.scheduler.util;

import com.hospital.scheduler.model.Shift;
import com.hospital.scheduler.model.Staff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Tiny dependency-free JSON helper: serializes our models, parses flat {"k":"v"} objects. */
public final class Json {
    private Json() {}

    private static final Pattern FIELD =
            Pattern.compile("\"(\\w+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^,}\\s]+))");

    public static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> sb.append(ch < 0x20 ? String.format("\\u%04x", (int) ch) : String.valueOf(ch));
            }
        }
        return sb.append('"').toString();
    }

    public static String staffToJson(Staff s) {
        return "{\"id\":" + s.id() + ",\"name\":" + quote(s.name()) +
               ",\"role\":" + quote(s.role().name()) + ",\"department\":" + quote(s.department()) +
               ",\"contact\":" + quote(s.contact()) + "}";
    }

    public static String shiftToJson(Shift s) {
        return "{\"id\":" + s.id() + ",\"staffId\":" + s.staffId() +
               ",\"staffName\":" + quote(s.staffName()) +
               ",\"staffRole\":" + quote(s.staffRole() == null ? null : s.staffRole().name()) +
               ",\"department\":" + quote(s.department()) +
               ",\"shiftDate\":" + quote(s.shiftDate().toString()) +
               ",\"startTime\":" + quote(s.startTime().toString()) +
               ",\"endTime\":" + quote(s.endTime().toString()) +
               ",\"status\":" + quote(s.status().name()) + "}";
    }

    public static <T> String array(List<T> items, Function<T, String> mapper) {
        return items.stream().map(mapper).collect(Collectors.joining(",", "[", "]"));
    }

    public static String error(String message) {
        return "{\"error\":" + quote(message) + "}";
    }

    /** Parses a flat JSON object (string/number/boolean values) into a map. */
    public static Map<String, String> parseFlat(String json) {
        Map<String, String> out = new HashMap<>();
        Matcher m = FIELD.matcher(json == null ? "" : json);
        while (m.find()) {
            String v = m.group(2) != null
                    ? m.group(2).replace("\\\"", "\"").replace("\\\\", "\\")
                    : m.group(3);
            out.put(m.group(1), v);
        }
        return out;
    }
}
