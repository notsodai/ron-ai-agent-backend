package com.ron.ronaiagent.chat.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.ron.ronaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * @author admin
 * @date 2025/9/14 下午3:13
 */
public class PDFGenerationTool {

    // Emoji字符的正则表达式 - 简化版本
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
        "[\\uD83C\\uDF00-\\uD83D\\uDDFF]|[\\uD83E\\uDD00-\\uD83E\\uDDFF]|[\\uD83D\\uDE00-\\uD83D\\uDE4F]|[\\uD83D\\uDE80-\\uD83D\\uDEFF]"
    );

    /**
     * 处理Emoji字符，将其替换为文本描述
     * @param content 原始内容
     * @return 处理后的内容
     */
    private String processEmojiCharacters(String content) {
        if (content == null) {
            return null;
        }

        // 简单的Emoji字符替换映射
        String processed = content
            .replace("😊", "[微笑]")
            .replace("🎉", "[庆祝]")
            .replace("❤️", "[爱心]")
            .replace("👍", "[赞]")
            .replace("🤔", "[思考]")
            .replace("😢", "[哭泣]")
            .replace("😡", "[愤怒]")
            .replace("🎁", "[礼物]")
            .replace("⭐", "[星星]")
            .replace("🔥", "[火焰]")
            .replace("💯", "[100分]")
            .replace("🚀", "[火箭]");

        // 对于未映射的Emoji，使用通用替换
        return EMOJI_PATTERN.matcher(processed).replaceAll("[表情]");
    }

    @Tool(description = "Generate a PDF file with given content (supports ASCII, Chinese, Japanese, Korean characters; emojis will be converted to text)")
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF (emojis will be converted to text descriptions)") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;

        // 验证文件名
        if (fileName == null || fileName.trim().isEmpty()) {
            return "Error: File name cannot be empty";
        }

        // 验证内容
        if (content == null) {
            return "Error: Content cannot be null";
        }

        // 处理Emoji字符，转换为文本描述
        String processedContent = processEmojiCharacters(content);

        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                // 创建段落并设置合适的字体大小
                Paragraph paragraph = new Paragraph(processedContent);
                paragraph.setFontSize(12);

                // 尝试使用支持多种字符集的字体组合
                try {
                    // 创建支持中文的字体
                    PdfFont chineseFont = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H",
                            PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

                    // 设置默认字体为支持中文的字体
                    document.setFont(chineseFont);

                    // 添加内容
                    document.add(paragraph);

                } catch (Exception fontException) {
                    try {
                        // 备选方案：使用另一种中文字体
                        PdfFont chineseFont = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H",
                                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                        document.setFont(chineseFont);
                        document.add(paragraph);

                    } catch (Exception fontException2) {
                        try {
                            // 最后备选：使用字体-亚洲库的字体
                            PdfFont asianFont = PdfFontFactory.createFont("MHei-Medium", "UniCNS-UCS2-H",
                                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                            document.setFont(asianFont);
                            document.add(paragraph);

                        } catch (Exception fontException3) {
                            // 使用Unicode字体作为最后备选
                            try {
                                PdfFont unicodeFont = PdfFontFactory.createFont("Arial", "Identity-H",
                                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                                document.setFont(unicodeFont);
                                document.add(paragraph);
                            } catch (Exception fontException4) {
                                // 最终备选：使用标准字体
                                PdfFont standardFont = PdfFontFactory.createFont("Helvetica");
                                document.setFont(standardFont);
                                System.err.println("Warning: Extended Unicode fonts not available. Some characters (Emoji, Chinese) may not display correctly.");
                                document.add(paragraph);
                            }
                        }
                    }
                }
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        } catch (Exception e) {
            return "Unexpected error generating PDF: " + e.getMessage();
        }
    }
}
