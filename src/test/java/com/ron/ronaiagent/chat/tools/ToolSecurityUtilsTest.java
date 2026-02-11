package com.ron.ronaiagent.chat.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSecurityUtilsTest {

    @Test
    void resolveSafePathRejectsTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolSecurityUtils.resolveSafePath("temp/file", "../a.txt"));
    }

    @Test
    void resolveSafePathAcceptsSimpleFileName() {
        var path = ToolSecurityUtils.resolveSafePath("temp/file", "a.txt");
        assertTrue(path.toString().endsWith("a.txt"));
    }

    @Test
    void validatePublicHttpUrlRejectsLocalhost() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolSecurityUtils.validatePublicHttpUrl("http://127.0.0.1:8080"));
    }
}
