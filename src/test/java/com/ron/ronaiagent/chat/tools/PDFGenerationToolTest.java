package com.ron.ronaiagent.chat.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author admin
 * @date 2025/9/14 下午3:14
 */
class PDFGenerationToolTest {

    private final PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();

    @Test
    @DisplayName("Should generate PDF successfully with ASCII characters")
    void generatePDFWithValidASCIICharacters() {
        String result = pdfGenerationTool.generatePDF("test.pdf", "Hello, World! This is a test with ASCII characters only.");
        assertNotNull(result);
        assertTrue(result.contains("PDF generated successfully to:"));
        assertTrue(result.contains("test.pdf"));
    }

    @Test
    @DisplayName("Should generate PDF successfully with Chinese characters")
    void generatePDFWithChineseCharacters() {
        String result = pdfGenerationTool.generatePDF("test_chinese.pdf", "你好，世界！这是中文内容。");
        assertNotNull(result);
        assertTrue(result.contains("PDF generated successfully to:"));
        assertTrue(result.contains("test_chinese.pdf"));
    }

    @Test
    @DisplayName("Should generate PDF successfully with mixed ASCII and Chinese characters")
    void generatePDFWithMixedCharacters() {
        String result = pdfGenerationTool.generatePDF("test_mixed.pdf", "Hello World 你好!");
        assertNotNull(result);
        assertTrue(result.contains("PDF generated successfully to:"));
        assertTrue(result.contains("test_mixed.pdf"));
    }

    @Test
    @DisplayName("Should reject PDF generation with empty filename")
    void generatePDFWithEmptyFilename() {
        String result = pdfGenerationTool.generatePDF("", "Valid content");
        assertNotNull(result);
        assertTrue(result.contains("Error:"));
        assertTrue(result.contains("File name cannot be empty"));
    }

    @Test
    @DisplayName("Should reject PDF generation with null filename")
    void generatePDFWithNullFilename() {
        String result = pdfGenerationTool.generatePDF(null, "Valid content");
        assertNotNull(result);
        assertTrue(result.contains("Error:"));
        assertTrue(result.contains("File name cannot be empty"));
    }

    @Test
    @DisplayName("Should reject PDF generation with whitespace-only filename")
    void generatePDFWithWhitespaceFilename() {
        String result = pdfGenerationTool.generatePDF("   ", "Valid content");
        assertNotNull(result);
        assertTrue(result.contains("Error:"));
        assertTrue(result.contains("File name cannot be empty"));
    }

    @Test
    @DisplayName("Should generate PDF successfully with empty content")
    void generatePDFWithEmptyContent() {
        String result = pdfGenerationTool.generatePDF("test_empty.pdf", "");
        assertNotNull(result);
        assertTrue(result.contains("PDF generated successfully to:"));
    }

    @Test
    @DisplayName("Should reject PDF generation with null content")
    void generatePDFWithNullContent() {
        String result = pdfGenerationTool.generatePDF("test_null.pdf", null);
        assertNotNull(result);
        assertTrue(result.contains("Error:"));
        assertTrue(result.contains("Content cannot be null"));
    }

    @Test
    @DisplayName("Should generate PDF successfully with numbers and special characters")
    void generatePDFWithNumbersAndSpecialCharacters() {
        String content = "Test with numbers: 123456789 and special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?";
        String result = pdfGenerationTool.generatePDF("test_special.pdf", content);
        assertNotNull(result);
        assertTrue(result.contains("PDF generated successfully to:"));
    }

    @Test
    @DisplayName("Should generate PDF successfully with newline characters")
    void generatePDFWithNewlineCharacters() {
        String content = "Line 1\nLine 2\nLine 3";
        String result = pdfGenerationTool.generatePDF("test_newlines.pdf", content);
        assertNotNull(result);
        assertTrue(result.contains("PDF generated successfully to:"));
    }

    @Test
    @DisplayName("Should generate PDF successfully with Japanese characters")
    void generatePDFWithJapaneseCharacters() {
        String result = pdfGenerationTool.generatePDF("test_japanese.pdf", "こんにちは世界");
        assertNotNull(result);
        assertTrue(result.contains("PDF generated successfully to:"));
    }

    @Test
    @DisplayName("Should generate PDF successfully with emoji characters (converted to text)")
    void generatePDFWithEmojiCharacters() {
        String result = pdfGenerationTool.generatePDF("test_emoji.pdf", "Hello World! 😊🎉");
        assertNotNull(result);
        System.out.println("Emoji test result: " + result);
        assertTrue(result.contains("PDF generated successfully to:"));
    }

    @Test
    @DisplayName("Should generate PDF successfully with Chinese and Japanese mixed content")
    void generatePDFWithChineseAndJapaneseMixed() {
        String content = "你好世界！こんにちは！Hello World!";
        String result = pdfGenerationTool.generatePDF("test_multilingual.pdf", content);
        assertNotNull(result);
        assertTrue(result.contains("PDF generated successfully to:"));
    }

    @Test
    @DisplayName("Should generate PDF successfully with Chinese punctuation and symbols")
    void generatePDFWithChinesePunctuation() {
        String content = "这是中文测试！包含标点符号：，。；？\"\"''（）【】《》";
        String result = pdfGenerationTool.generatePDF("test_chinese_punctuation.pdf", content);
        assertNotNull(result);
        assertTrue(result.contains("PDF generated successfully to:"));
    }

    @Test
    @DisplayName("Should generate PDF successfully with multiline Chinese content")
    void generatePDFWithMultilineChineseContent() {
        String content = "第一行：你好世界\n第二行：这是中文测试\n第三行：PDF生成成功！";
        String result = pdfGenerationTool.generatePDF("test_multiline_chinese.pdf", content);
        assertNotNull(result);
        assertTrue(result.contains("PDF generated successfully to:"));
    }
}