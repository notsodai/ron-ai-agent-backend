package com.ron.ronaiagent.examples.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileBasedChatMemoryTest {

    @Test
    void addListShouldPersistMessages() throws Exception {
        Path dir = Files.createTempDirectory("chat-memory-test");
        FileBasedChatMemory memory = new FileBasedChatMemory(dir.toString());
        String conversationId = "c1";

        memory.add(conversationId, List.of(new UserMessage("a"), new UserMessage("b")));
        List<?> messages = memory.get(conversationId);

        assertEquals(2, messages.size());
    }
}
