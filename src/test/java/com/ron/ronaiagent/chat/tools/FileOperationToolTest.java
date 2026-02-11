package com.ron.ronaiagent.chat.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileOperationToolTest {

    @Test
    void writeFileShouldRejectTraversalName() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String result = fileOperationTool.writeFile("../test.txt", "Hello");
        assertTrue(result.startsWith("Error writing to file:"));
    }

    @Test
    void readFileShouldRejectTraversalName() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String result = fileOperationTool.readFile("../test.txt");
        assertTrue(result.startsWith("Error reading file:"));
    }
}
