package com.ron.ronaiagent.chat.chat.memory;

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
    private String BASE_DIR;
    private static final Kryo KRYO = new Kryo();

    static {
        // 允许未注册的类
        KRYO.setRegistrationRequired(false);
        // 设置实例化策略
        KRYO.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    public FileBasedChatMemory(String baseDir) {
        this.BASE_DIR = baseDir;
        File file = new File(BASE_DIR);
        if (!file.exists()){
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

    }

    @NotNull
    @Override
    public List<Message> get(@NotNull String conversationId) {
        return getOrCreateConversation(conversationId);
    }

    /**
     * 获取会话
     * @param conversationId 会话ID
     * @param lastN 获取最后N条消息
     * @return 会话
     */
    public List<Message> get(String conversationId, int lastN) {
        List<Message> allMessages = getOrCreateConversation(conversationId);
        return allMessages.stream()
                .skip(Math.max(0, allMessages.size() - lastN))
                .toList();
    }

    @Override
    public void clear(@NotNull String conversationId) {
        File conversationFile = getConversationFile(conversationId);
        if (conversationFile.exists()){
            conversationFile.delete();
        }

    }

    /**
     * 获取会话
     * @param conversationId 会话ID
     * @return 会话
     */
    public List<Message> getOrCreateConversation(String conversationId){
        File conversationFile = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();

        if (conversationFile.exists()){
            try(Input input = new Input(new FileInputStream(conversationFile))) {
                KRYO.readObject(input, ArrayList.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return messages;
    }

    /**
     * 保存会话
     * @param conversationId 会话ID
     * @param messages 会话
     */
    public void saveConversation(String conversationId, List<Message> messages){
        File conversationFile = getConversationFile(conversationId);
        try(Output output = new Output(new FileOutputStream(conversationFile))) {
            KRYO.writeObject(output, messages);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File getConversationFile(String conversationId){
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}
