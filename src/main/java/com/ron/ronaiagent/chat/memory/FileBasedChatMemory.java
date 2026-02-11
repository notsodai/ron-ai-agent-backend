package com.ron.ronaiagent.chat.memory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.jetbrains.annotations.NotNull;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class FileBasedChatMemory implements ChatMemory {

    private final String baseDir;
    private static final Kryo KRYO = new Kryo();

    static {
        KRYO.setRegistrationRequired(false);
        KRYO.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    public FileBasedChatMemory(String baseDir) {
        this.baseDir = baseDir;
        File file = new File(baseDir);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    @Override
    public void add(@NotNull String conversationId, @NotNull Message message) {
        List<Message> messageList = getOrCreateConversation(conversationId);
        messageList.add(message);
        saveConversation(conversationId, messageList);
    }

    @Override
    public void add(@NotNull String conversationId, @NotNull List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        List<Message> messageList = getOrCreateConversation(conversationId);
        messageList.addAll(messages);
        saveConversation(conversationId, messageList);
    }

    @NotNull
    @Override
    public List<Message> get(@NotNull String conversationId) {
        return getOrCreateConversation(conversationId);
    }

    public List<Message> get(String conversationId, int lastN) {
        List<Message> allMessages = getOrCreateConversation(conversationId);
        return allMessages.stream()
                .skip(Math.max(0, allMessages.size() - lastN))
                .toList();
    }

    @Override
    public void clear(@NotNull String conversationId) {
        File conversationFile = getConversationFile(conversationId);
        if (conversationFile.exists()) {
            conversationFile.delete();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Message> getOrCreateConversation(String conversationId) {
        File conversationFile = getConversationFile(conversationId);
        if (conversationFile.exists()) {
            try (Input input = new Input(new FileInputStream(conversationFile))) {
                List<Message> saved = KRYO.readObject(input, ArrayList.class);
                return saved != null ? saved : new ArrayList<>();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return new ArrayList<>();
    }

    public void saveConversation(String conversationId, List<Message> messages) {
        File conversationFile = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(conversationFile))) {
            KRYO.writeObject(output, messages);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File getConversationFile(String conversationId) {
        return new File(baseDir, conversationId + ".kryo");
    }
}
