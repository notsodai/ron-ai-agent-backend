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
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public class PDFGenerationTool {

    private static final String PDF_DIR = FileConstant.FILE_SAVE_DIR + "/pdf";
    private static final Pattern EMOJI_PATTERN = Pattern.compile("[\\p{So}\\p{Cn}]");

    @Tool(description = "Generate a PDF file with given content")
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return ToolResult.error("File name cannot be empty").toJson();
        }
        if (content == null) {
            return ToolResult.error("Content cannot be null").toJson();
        }
        try {
            String normalizedName = fileName.endsWith(".pdf") ? fileName : fileName + ".pdf";
            FileUtil.mkdir(PDF_DIR);
            Path filePath = ToolSecurityUtils.resolveSafePath(PDF_DIR, normalizedName);
            String processedContent = EMOJI_PATTERN.matcher(content).replaceAll("[emoji]");

            try (PdfWriter writer = new PdfWriter(filePath.toString());
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                Paragraph paragraph = new Paragraph(processedContent).setFontSize(12);
                applyBestEffortFont(document);
                document.add(paragraph);
            }
            return ToolResult.success("PDF generated successfully to: " + filePath)
                    .withArtifacts(List.of(filePath.toString()))
                    .withNextActions(List.of("Read the generated PDF", "Download the PDF"))
                    .toJson();
        } catch (IOException e) {
            return ToolResult.error("Error generating PDF: " + e.getMessage()).toJson();
        } catch (Exception e) {
            return ToolResult.error("Unexpected error generating PDF: " + e.getMessage()).toJson();
        }
    }

    private void applyBestEffortFont(Document document) throws IOException {
        PdfFont font;
        try {
            font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H",
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        } catch (Exception e) {
            try {
                font = PdfFontFactory.createFont("Arial", "Identity-H",
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            } catch (Exception fallbackError) {
                font = PdfFontFactory.createFont("Helvetica");
            }
        }
        document.setFont(font);
    }
}
