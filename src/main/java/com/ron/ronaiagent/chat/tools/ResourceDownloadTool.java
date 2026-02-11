package com.ron.ronaiagent.chat.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.ron.ronaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;

public class ResourceDownloadTool {

    private static final String DOWNLOAD_DIR = FileConstant.FILE_SAVE_DIR + "/download";

    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(
            @ToolParam(description = "URL of the resource to download") String url,
            @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        try {
            URI safeUri = ToolSecurityUtils.validatePublicHttpUrl(url);
            FileUtil.mkdir(DOWNLOAD_DIR);
            Path filePath = ToolSecurityUtils.resolveSafePath(DOWNLOAD_DIR, fileName);
            HttpUtil.downloadFile(safeUri.toString(), new File(filePath.toString()));
            return "Resource downloaded successfully to: " + filePath;
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
