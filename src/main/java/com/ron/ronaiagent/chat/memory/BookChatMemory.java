package com.ron.ronaiagent.chat.memory;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom ChatMemory implementation wrapping a ChatMemoryRepository.
 */
public class BookChatMemory implements ChatMemory {
    private final ChatMemoryRepository repository;
    private final String repositoryId;

    public BookChatMemory(ChatMemoryRepository repository, String repositoryId) {
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
