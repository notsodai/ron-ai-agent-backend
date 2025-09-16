package com.ron.ronaiagent.chat.memory;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义ChatMemory
 */
public class BookChatMemory implements ChatMemory {
    private final InMemoryChatMemoryRepository repository;
    private final String repositoryId;

    public BookChatMemory(InMemoryChatMemoryRepository repository, String repositoryId) {
        this.repository = repository;
        this.repositoryId = repositoryId;
    }

    @Override
    public void add(@NotNull String conversationId, @NotNull List<Message> messages) {
        List<Message> existingMessages = this.repository.findByConversationId(conversationId);
        List<Message> updatedMessages = new ArrayList<>(existingMessages);
        updatedMessages.addAll(messages);
        this.repository.saveAll(conversationId, updatedMessages);
    }


    @NotNull
    @Override
    public List<Message> get(@NotNull String conversationId) {
        return this.repository.findByConversationId(conversationId);
    }

    @Override
    public void clear(@NotNull String conversationId) {
        this.repository.deleteByConversationId(conversationId);
    }

}
