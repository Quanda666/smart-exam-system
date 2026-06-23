package com.smartexam.common;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 生成带 UTF-8 BOM 的 CSV 字节流：加 BOM 是为了 Excel 双击打开时能正确识别 UTF-8，
 * 否则中文会乱码。字段按 RFC 4180 转义（含逗号/引号/换行的值用双引号包裹并转义内部引号）。
 */
public final class CsvExport {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvExport() {
    }

    public static byte[] build(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, headers);
        for (List<Object> row : rows) {
            appendRow(sb, row);
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
        return out;
    }

    private static void appendRow(StringBuilder sb, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
    }

    private static String escape(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
