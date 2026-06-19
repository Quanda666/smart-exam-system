package com.smartexam.service;

import com.smartexam.dto.ai.AiGeneratedQuestion;
import com.smartexam.dto.ai.AiGeneratedQuestionOption;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ExcelQuestionTemplateService {

    private static final int COLUMN_TYPE = 0;
    private static final int COLUMN_STEM = 1;
    private static final int COLUMN_OPTIONS = 2;
    private static final int COLUMN_ANSWER = 3;
    private static final int COLUMN_ANALYSIS = 4;
    private static final int COLUMN_SCORE = 5;
    private static final int HEADER_ROW = 0;
    private static final int DATA_START_ROW = 2;

    private static final Map<String, String> TYPE_ALIASES;
    static {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("单选", "SINGLE_CHOICE");
        aliases.put("单选题", "SINGLE_CHOICE");
        aliases.put("多选", "MULTIPLE_CHOICE");
        aliases.put("多选题", "MULTIPLE_CHOICE");
        aliases.put("判断", "TRUE_FALSE");
        aliases.put("判断题", "TRUE_FALSE");
        aliases.put("填空", "FILL_BLANK");
        aliases.put("填空题", "FILL_BLANK");
        aliases.put("主观", "SUBJECTIVE");
        aliases.put("主观题", "SUBJECTIVE");
        aliases.put("简答", "SUBJECTIVE");
        aliases.put("简答题", "SUBJECTIVE");
        TYPE_ALIASES = Collections.unmodifiableMap(aliases);
    }

    private static final List<String> VALID_TYPES = List.of(
            "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SUBJECTIVE"
    );

    public byte[] generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("题目模板");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle exampleStyle = createExampleStyle(workbook);

            Row headerRow = sheet.createRow(HEADER_ROW);
            String[] headers = {"题型", "题干", "选项", "答案", "解析", "分值"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            createExampleRow(sheet, 1, exampleStyle,
                    "单选", "以下哪个是 Java 的基本数据类型？",
                    "A. String\nB. int\nC. Integer\nD. Object",
                    "B", "int 是 Java 的 8 种基本数据类型之一", "5");

            createExampleRow(sheet, 2, exampleStyle,
                    "多选", "以下哪些是 Spring 框架的核心模块？",
                    "A. Spring Core\nB. Spring MVC\nC. Spring Boot\nD. Spring AOP",
                    "A,B,D", "Spring Boot 是基于 Spring 的快速开发框架，但不是 Spring 框架本身的核心模块", "5");

            createExampleRow(sheet, 3, exampleStyle,
                    "判断", "Java 中的 String 是可变的。",
                    "A. 正确\nB. 错误",
                    "B", "String 在 Java 中是不可变的（immutable）", "3");

            createExampleRow(sheet, 4, exampleStyle,
                    "填空", "Java 中使用 _____ 关键字来声明常量。",
                    "",
                    "final", "final 关键字用于声明常量、防止继承和方法重写", "4");

            createExampleRow(sheet, 5, exampleStyle,
                    "主观", "请简述 Java 面向对象的三大特性。",
                    "",
                    "封装、继承、多态。封装隐藏对象内部实现细节；继承实现代码复用；多态允许不同对象对同一消息作出不同响应。",
                    "封装保护数据安全，继承提高代码复用性，多态提升系统扩展性", "10");

            sheet.setColumnWidth(0, 3000);
            sheet.setColumnWidth(1, 12000);
            sheet.setColumnWidth(2, 10000);
            sheet.setColumnWidth(3, 4000);
            sheet.setColumnWidth(4, 12000);
            sheet.setColumnWidth(5, 2500);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public List<AiGeneratedQuestion> parseExcel(MultipartFile file, Long subjectId, Long knowledgePointId, String difficulty, BigDecimal defaultScore) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传的 Excel 文件为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new IllegalArgumentException("仅支持 .xlsx 或 .xls 格式的 Excel 文件");
        }

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<AiGeneratedQuestion> questions = new ArrayList<>();

            for (int rowIndex = DATA_START_ROW; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                try {
                    AiGeneratedQuestion question = parseRow(row, rowIndex, subjectId, knowledgePointId, difficulty, defaultScore);
                    if (question != null) {
                        questions.add(question);
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("第 " + (rowIndex + 1) + " 行解析失败：" + e.getMessage(), e);
                }
            }

            if (questions.isEmpty()) {
                throw new IllegalArgumentException("Excel 文件中没有有效的题目数据，请检查格式是否正确");
            }

            return questions;
        } catch (IOException e) {
            throw new IllegalArgumentException("Excel 文件读取失败：" + e.getMessage(), e);
        }
    }

    private AiGeneratedQuestion parseRow(Row row, int rowIndex, Long subjectId, Long knowledgePointId, String difficulty, BigDecimal defaultScore) {
        String typeRaw = getCellValue(row, COLUMN_TYPE);
        String stem = getCellValue(row, COLUMN_STEM);

        if (isBlank(typeRaw) && isBlank(stem)) {
            return null;
        }

        if (isBlank(typeRaw)) {
            throw new IllegalArgumentException("题型不能为空");
        }
        if (isBlank(stem)) {
            throw new IllegalArgumentException("题干不能为空");
        }

        String questionType = normalizeQuestionType(typeRaw);
        if (!VALID_TYPES.contains(questionType)) {
            throw new IllegalArgumentException("不支持的题型：" + typeRaw + "，请使用：单选、多选、判断、填空、主观");
        }

        AiGeneratedQuestion question = new AiGeneratedQuestion();
        question.setSubjectId(subjectId);
        question.setKnowledgePointId(knowledgePointId);
        question.setQuestionType(questionType);
        question.setDifficulty(difficulty == null ? "MEDIUM" : difficulty.toUpperCase(Locale.ROOT));
        question.setStem(stem.trim());
        question.setStatus(0);

        String scoreText = getCellValue(row, COLUMN_SCORE);
        if (!isBlank(scoreText)) {
            try {
                question.setDefaultScore(new BigDecimal(scoreText.trim()));
            } catch (NumberFormatException e) {
                question.setDefaultScore(defaultScore != null ? defaultScore : BigDecimal.valueOf(5));
            }
        } else {
            question.setDefaultScore(defaultScore != null ? defaultScore : BigDecimal.valueOf(5));
        }

        String analysis = getCellValue(row, COLUMN_ANALYSIS);
        question.setAnalysis(isBlank(analysis) ? "请参考题目解答要点" : analysis.trim());

        if (isObjectiveType(questionType)) {
            parseObjectiveQuestion(question, row, questionType);
        } else {
            parseSubjectiveQuestion(question, row);
        }

        question.setSourceType("EXCEL_TEMPLATE");
        question.setSourceDetail("Excel template import");

        return question;
    }

    private void parseObjectiveQuestion(AiGeneratedQuestion question, Row row, String questionType) {
        String optionsText = getCellValue(row, COLUMN_OPTIONS);
        String answerText = getCellValue(row, COLUMN_ANSWER);

        if (isBlank(optionsText)) {
            throw new IllegalArgumentException("客观题必须提供选项");
        }
        if (isBlank(answerText)) {
            throw new IllegalArgumentException("客观题必须提供答案");
        }

        List<AiGeneratedQuestionOption> options = parseOptions(optionsText, questionType);
        if (options.isEmpty()) {
            throw new IllegalArgumentException("选项格式错误，请使用 A. 选项内容 格式，每行一个选项");
        }

        Set<String> correctLabels = parseAnswerLabels(answerText.trim());
        if (correctLabels.isEmpty()) {
            throw new IllegalArgumentException("答案格式错误，请使用 A 或 A,B,C 格式");
        }

        if ("SINGLE_CHOICE".equals(questionType) && correctLabels.size() > 1) {
            throw new IllegalArgumentException("单选题只能有一个正确答案");
        }
        if ("MULTIPLE_CHOICE".equals(questionType) && correctLabels.size() < 2) {
            throw new IllegalArgumentException("多选题至少需要两个正确答案");
        }
        if ("TRUE_FALSE".equals(questionType)) {
            if (options.size() != 2) {
                options = Arrays.asList(
                        createOption("A", "正确", false),
                        createOption("B", "错误", false)
                );
            }
            if (correctLabels.size() != 1) {
                throw new IllegalArgumentException("判断题只能有一个正确答案（A 或 B）");
            }
        }

        for (AiGeneratedQuestionOption option : options) {
            option.setCorrect(correctLabels.contains(option.getOptionLabel()));
        }

        question.setOptions(options);
        question.setCorrectAnswer(String.join(",", correctLabels));
    }

    private void parseSubjectiveQuestion(AiGeneratedQuestion question, Row row) {
        String answer = getCellValue(row, COLUMN_ANSWER);
        if (isBlank(answer)) {
            throw new IllegalArgumentException("填空题和主观题必须提供参考答案");
        }
        question.setCorrectAnswer(answer.trim());
        question.setOptions(List.of());
    }

    private List<AiGeneratedQuestionOption> parseOptions(String optionsText, String questionType) {
        List<AiGeneratedQuestionOption> options = new ArrayList<>();
        String[] lines = optionsText.split("\\r?\\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("[.．、)]", 2);
            if (parts.length < 2) {
                continue;
            }

            String label = parts[0].trim().toUpperCase(Locale.ROOT);
            String content = parts[1].trim();

            if (label.matches("^[A-H]$") && !content.isEmpty()) {
                options.add(createOption(label, content, false));
            }
        }

        return options;
    }

    private Set<String> parseAnswerLabels(String answerText) {
        Set<String> labels = new LinkedHashSet<>();
        String upper = answerText.toUpperCase(Locale.ROOT);

        for (String part : upper.split("[,，、;；\\s]+")) {
            part = part.trim();
            if (part.matches("^[A-H]$")) {
                labels.add(part);
            }
        }

        return labels;
    }

    private AiGeneratedQuestionOption createOption(String label, String content, boolean correct) {
        AiGeneratedQuestionOption option = new AiGeneratedQuestionOption();
        option.setOptionLabel(label);
        option.setOptionContent(content);
        option.setCorrect(correct);
        return option;
    }

    private String normalizeQuestionType(String typeRaw) {
        String normalized = typeRaw.trim();
        String mapped = TYPE_ALIASES.get(normalized);
        if (mapped != null) {
            return mapped;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private boolean isObjectiveType(String questionType) {
        return "SINGLE_CHOICE".equals(questionType)
                || "MULTIPLE_CHOICE".equals(questionType)
                || "TRUE_FALSE".equals(questionType);
    }

    private String getCellValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private boolean isEmptyRow(Row row) {
        for (int i = 0; i < 6; i++) {
            if (!isBlank(getCellValue(row, i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createExampleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }

    private void createExampleRow(Sheet sheet, int rowIndex, CellStyle style, String type, String stem, String options, String answer, String analysis, String score) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(60);

        createStyledCell(row, COLUMN_TYPE, type, style);
        createStyledCell(row, COLUMN_STEM, stem, style);
        createStyledCell(row, COLUMN_OPTIONS, options, style);
        createStyledCell(row, COLUMN_ANSWER, answer, style);
        createStyledCell(row, COLUMN_ANALYSIS, analysis, style);
        createStyledCell(row, COLUMN_SCORE, score, style);
    }

    private void createStyledCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}
