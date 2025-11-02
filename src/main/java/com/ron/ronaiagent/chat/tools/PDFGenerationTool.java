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

/**
 * @author admin
 * @date 2025/9/14 下午3:13
 */
public class PDFGenerationTool {
    @Tool(description = "Generate a PDF file with given content")
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                // 自定义字体（需要人工下载字体文件到特定目录）
//                String fontPath = Paths.get("src/main/resources/static/fonts/simsun.ttf")
//                        .toAbsolutePath().toString();
//                PdfFont font = PdfFontFactory.createFont(fontPath,
//                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                        // 使用内置中文字体，使用正确的iText API
                try {
                    PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
                    document.setFont(font);
                } catch (Exception fontException) {
                    // 如果中文字体不可用，使用默认字体并添加警告
                    System.err.println("Warning: Chinese font not available, using default font. Chinese characters may not display correctly.");
                    // 使用默认字体（无需设置）
                }
                // 创建段落
                Paragraph paragraph = new Paragraph(content);
                // 添加段落并关闭文档
                document.add(paragraph);
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }
}
