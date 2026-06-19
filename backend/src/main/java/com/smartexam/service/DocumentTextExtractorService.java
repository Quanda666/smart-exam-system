package com.smartexam.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DocumentTextExtractorService {

    private static final int MAX_FILE_BYTES = 25 * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 80_000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "text", "md", "doc", "docx", "ppt", "pptx", "pdf");
    private static final Pattern XML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]+");

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传文档文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("文档不能超过25MB");
        }
        try {
            byte[] bytes = file.getBytes();
            String filename = safeFilename(file.getOriginalFilename());
            String extension = extensionOf(filename);
            ensureSupportedExtension(extension);
            String text = switch (extension) {
                case "txt", "text", "md" -> decodePlainText(bytes);
                case "docx" -> extractDocx(bytes);
                case "pptx" -> extractPptx(bytes);
                case "pdf" -> extractPdf(bytes);
                case "doc", "ppt" -> extractLegacyOffice(bytes);
                default -> throw unsupportedDocumentExtension(extension);
            };
            String normalized = normalizeText(text);
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("文档未抽取到可识别文本，请确认文件不是扫描图片或加密文档");
            }
            ensureExtractedTextWithinLimit(normalized);
            if ("pdf".equals(extension) && looksLikePdfNoise(normalized)) {
                throw new IllegalArgumentException("PDF文本提取结果疑似字体/语言标记，无法可靠识别题目；请改用Word/TXT，或先将PDF转换为可复制文本后再上传");
            }
            return normalized;
        } catch (IOException ex) {
            throw new IllegalArgumentException("文档读取失败：" + ex.getMessage(), ex);
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("word/") && name.endsWith(".xml")
                        && (name.contains("document") || name.contains("header") || name.contains("footer"))) {
                    String xml = new String(readAll(zip), StandardCharsets.UTF_8);
                    builder.append(xmlToText(xml)).append('\n');
                }
            }
        }
        return builder.toString();
    }

    private String extractPptx(byte[] bytes) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("ppt/") && name.endsWith(".xml")
                        && (name.startsWith("ppt/slides/")
                        || name.startsWith("ppt/notesSlides/")
                        || name.startsWith("ppt/slideMasters/")
                        || name.startsWith("ppt/slideLayouts/"))) {
                    String xml = new String(readAll(zip), StandardCharsets.UTF_8);
                    builder.append(xmlToText(xml)).append('\n');
                }
            }
        }
        return builder.toString();
    }

    private String extractPdf(byte[] bytes) throws IOException {
        String byPdfBox = extractPdfByPdfBox(bytes);
        if (!looksLikePdfNoise(normalizeText(byPdfBox))) {
            return byPdfBox;
        }

        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        while (cursor < bytes.length) {
            int stream = indexOf(bytes, "stream".getBytes(StandardCharsets.ISO_8859_1), cursor);
            if (stream < 0) {
                break;
            }
            int dataStart = stream + "stream".length();
            if (dataStart < bytes.length && bytes[dataStart] == '\r') {
                dataStart++;
            }
            if (dataStart < bytes.length && bytes[dataStart] == '\n') {
                dataStart++;
            }
            int end = indexOf(bytes, "endstream".getBytes(StandardCharsets.ISO_8859_1), dataStart);
            if (end < 0) {
                break;
            }
            byte[] streamBytes = Arrays.copyOfRange(bytes, dataStart, end);
            String header = new String(bytes, Math.max(0, stream - 600), stream - Math.max(0, stream - 600), StandardCharsets.ISO_8859_1);
            if (header.contains("/FlateDecode")) {
                streamBytes = inflate(streamBytes);
            }
            builder.append(extractPdfStringLiterals(new String(streamBytes, StandardCharsets.ISO_8859_1))).append('\n');
            cursor = end + "endstream".length();
        }

        String extracted = builder.toString();
        if (extracted.replaceAll("\\s+", "").length() < 20) {
            extracted = extractBinaryStrings(bytes);
        }
        return extracted;
    }

    private String extractPdfByPdfBox(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractLegacyOffice(byte[] bytes) {
        String utf16 = new String(bytes, StandardCharsets.UTF_16LE);
        String combined = extractReadableRuns(utf16) + "\n" + extractBinaryStrings(bytes);
        return combined;
    }

    private String decodePlainText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        long replacementCount = utf8.chars().filter(ch -> ch == '\uFFFD').count();
        if (replacementCount > Math.max(3, utf8.length() / 100)) {
            return new String(bytes, Charset.forName("GB18030"));
        }
        return utf8;
    }

    private String xmlToText(String xml) {
        String text = xml
                .replaceAll("<w:tab[^>]*/>", "\t")
                .replaceAll("<w:br[^>]*/>", "\n")
                .replaceAll("</w:p>", "\n")
                .replaceAll("</w:tr>", "\n")
                .replaceAll("<a:br[^>]*/>", "\n")
                .replaceAll("</a:p>", "\n")
                .replaceAll("</a:tr>", "\n");
        text = XML_TAG.matcher(text).replaceAll("");
        return unescapeXml(text);
    }

    private byte[] inflate(byte[] bytes) throws IOException {
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        } catch (IOException ex) {
            return bytes;
        }
    }

    private String extractPdfStringLiterals(String streamText) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < streamText.length(); i++) {
            if (streamText.charAt(i) != '(') {
                continue;
            }
            StringBuilder literal = new StringBuilder();
            int depth = 1;
            i++;
            while (i < streamText.length() && depth > 0) {
                char ch = streamText.charAt(i);
                if (ch == '\\' && i + 1 < streamText.length()) {
                    i = appendPdfEscaped(streamText, i + 1, literal);
                } else if (ch == '(') {
                    depth++;
                    literal.append(ch);
                } else if (ch == ')') {
                    depth--;
                    if (depth > 0) {
                        literal.append(ch);
                    }
                } else {
                    literal.append(ch);
                }
                i++;
            }
            String value = literal.toString().trim();
            if (value.length() > 1) {
                parts.add(value);
            }
        }
        return String.join(" ", parts);
    }

    private int appendPdfEscaped(String text, int index, StringBuilder builder) {
        char escaped = text.charAt(index);
        switch (escaped) {
            case 'n' -> builder.append('\n');
            case 'r' -> builder.append('\r');
            case 't' -> builder.append('\t');
            case 'b', 'f' -> {
            }
            case '(', ')', '\\' -> builder.append(escaped);
            default -> {
                if (escaped >= '0' && escaped <= '7') {
                    int end = index;
                    while (end + 1 < text.length() && end - index < 2 && text.charAt(end + 1) >= '0' && text.charAt(end + 1) <= '7') {
                        end++;
                    }
                    try {
                        builder.append((char) Integer.parseInt(text.substring(index, end + 1), 8));
                    } catch (NumberFormatException ignore) {
                        builder.append(escaped);
                    }
                    return end;
                }
                builder.append(escaped);
            }
        }
        return index;
    }

    private String extractBinaryStrings(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        StringBuilder run = new StringBuilder();
        for (byte value : bytes) {
            int ch = value & 0xff;
            if (ch == '\n' || ch == '\r' || ch == '\t' || (ch >= 32 && ch <= 126)) {
                run.append((char) ch);
            } else {
                appendRun(builder, run);
            }
        }
        appendRun(builder, run);
        return builder.toString();
    }

    private String extractReadableRuns(String text) {
        StringBuilder builder = new StringBuilder();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch) || Character.toString(ch).matches("[\\u4e00-\\u9fa5，。！？；：、（）《》“”\\s]")) {
                run.append(ch);
            } else {
                appendRun(builder, run);
            }
        }
        appendRun(builder, run);
        return builder.toString();
    }

    private void appendRun(StringBuilder builder, StringBuilder run) {
        String value = run.toString().trim();
        if (value.length() >= 4) {
            builder.append(value).append('\n');
        }
        run.setLength(0);
    }

    private byte[] readAll(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        zip.transferTo(output);
        return output.toByteArray();
    }

    private int indexOf(byte[] bytes, byte[] target, int start) {
        outer:
        for (int i = Math.max(0, start); i <= bytes.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (bytes[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private String normalizeText(String text) {
        String normalized = CONTROL_CHARS.matcher(text == null ? "" : text).replaceAll(" ");
        normalized = normalized.replace('\u00A0', ' ')
                .replace('\u200B', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return normalized;
    }

    private void ensureExtractedTextWithinLimit(String normalized) {
        if (normalized != null && normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Extracted document text must be 80000 characters or less; split large materials before upload");
        }
    }

    private boolean looksLikePdfNoise(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return true;
        }
        String compact = value.replaceAll("\\s+", " ");
        int langMarkers = countMatches(compact, "\\b(?:en-US|zh-CN|zh-TW|ja-JP|ko-KR)\\b");
        int questionMarkers = countMatches(compact, "(?:\\d{1,3}\\s*[.、)）]|[一二三四五六七八九十]+[、.])");
        long chinese = compact.chars().filter(ch -> ch >= 0x4e00 && ch <= 0x9fa5).count();
        long mathUseful = compact.chars().filter(ch -> Character.isDigit(ch) || "＋+-*/=()（）√∑∞∫≤≥<>_".indexOf(ch) >= 0).count();
        int words = compact.isBlank() ? 0 : compact.split("\\s+").length;
        boolean mostlyLanguageTags = words > 30 && langMarkers > words * 0.35;
        boolean tooFewUsefulChars = chinese + mathUseful < 20 && questionMarkers == 0;
        return mostlyLanguageTags || tooFewUsefulChars;
    }

    private int countMatches(String text, String regex) {
        return (int) Pattern.compile(regex).matcher(text).results().count();
    }

    private String unescapeXml(String text) {
        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private String safeFilename(String filename) {
        return filename == null ? "" : filename;
    }

    private void ensureSupportedExtension(String extension) {
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw unsupportedDocumentExtension(extension);
        }
    }

    private IllegalArgumentException unsupportedDocumentExtension(String extension) {
        return new IllegalArgumentException("Unsupported document file type; supported: txt, text, md, doc, docx, ppt, pptx, pdf");
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
