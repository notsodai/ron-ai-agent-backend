package com.ron.ronaiagent.chat.tools;

import cn.hutool.core.io.FileUtil;
import com.ron.ronaiagent.constant.FileConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileOperationToolTest {

    private FileOperationTool fileOperationTool;
    private static final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    @BeforeEach
    void setUp() {
        fileOperationTool = new FileOperationTool();
    }

    @Test
    void writeFileShouldRejectTraversalName() {
        String result = fileOperationTool.writeFile("../test.txt", "Hello");
        assertTrue(result.contains("\"status\":\"error\""));
    }

    @Test
    void readFileShouldRejectTraversalName() {
        String result = fileOperationTool.readFile("../test.txt");
        assertTrue(result.contains("\"status\":\"error\""));
    }

    @Test
    void writeFile_success_shouldReturnToolResultJson() {
        String result = fileOperationTool.writeFile("test-write.txt", "hello world");
        assertTrue(result.contains("\"status\":\"success\""));
        assertTrue(result.contains("test-write.txt"));
    }

    @Test
    void readFile_success_shouldReturnToolResultJson() {
        fileOperationTool.writeFile("test-read.txt", "hello world");
        String result = fileOperationTool.readFile("test-read.txt");
        assertTrue(result.contains("\"status\":\"success\""));
        assertTrue(result.contains("hello world"));
    }

    @Test
    void readFile_error_shouldReturnToolResultError() {
        String result = fileOperationTool.readFile("nonexistent-file.txt");
        assertTrue(result.contains("\"status\":\"error\""));
    }
}
