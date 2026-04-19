package com.ron.ronaiagent.chat.tools;

import cn.hutool.core.io.FileUtil;
import com.ron.ronaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileOperationTool {

    private static final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of the file to read") String fileName) {
        try {
            Path filePath = ToolSecurityUtils.resolveSafePath(FILE_DIR, fileName);
            if (!Files.exists(filePath)) {
                return ToolResult.error("File not found: " + fileName).toJson();
            }
            String content = FileUtil.readUtf8String(filePath.toFile());
            return ToolResult.success(content)
                    .withArtifacts(List.of(filePath.toString()))
                    .withNextActions(List.of("Summarize the file content", "Search within the file"))
                    .toJson();
        } catch (Exception e) {
            return ToolResult.error("Error reading file: " + e.getMessage()).toJson();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(
            @ToolParam(description = "Name of the file to write") String fileName,
            @ToolParam(description = "Content to write to the file") String content) {
        try {
            FileUtil.mkdir(FILE_DIR);
            Path filePath = ToolSecurityUtils.resolveSafePath(FILE_DIR, fileName);
            FileUtil.writeUtf8String(content, filePath.toFile());
            return ToolResult.success("File written successfully to: " + filePath)
                    .withArtifacts(List.of(filePath.toString()))
                    .toJson();
        } catch (Exception e) {
            return ToolResult.error("Error writing to file: " + e.getMessage()).toJson();
        }
    }
}
