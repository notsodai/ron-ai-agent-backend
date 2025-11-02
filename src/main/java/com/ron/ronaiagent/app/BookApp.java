package com.ron.ronaiagent.app;

import cn.hutool.core.lang.UUID;
import com.ron.ronaiagent.chat.advisor.MyLoggerAdvisor;
import com.ron.ronaiagent.chat.memory.BookChatMemory;
import com.ron.ronaiagent.chat.memory.FileBasedChatMemory;
import com.ron.ronaiagent.chat.rag.BookAppQueryRewriter;
import com.ron.ronaiagent.chat.rag.BookAppRagCustomAdvisorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Component

public class BookApp {
    private static ChatClient chatClient;
    private static final Logger log = LoggerFactory.getLogger(BookApp.class);
    // private final SystemPromptTemplate systemPromptTemplate;


    /**
     * 加载系统提示模板内容
     *
     * @param systemPromptResource 系统提示模板文件资源
     * @return Markdown 文件内容的字符串形式
     */
    public String loadSystemPrompt(Resource systemPromptResource) {
        try (BufferedReader reader = new BufferedReader(
            // 读取文件内容并转换为字符串
                new InputStreamReader(systemPromptResource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            // 记录错误日志
            System.err.println("Failed to load system prompt: " + e.getMessage());
            return ""; // 返回默认值
        }
    }

    /**
     * 构造函数
     * @param dashScopeChatModel 阿里云AI模型
     * @param systemPromptResource 系统提示模板文件资源
     */
    public BookApp(ChatModel dashScopeChatModel,
                   @Value("classpath:templates/prompts/BookAssistantSystemPrompt.md") Resource systemPromptResource) {
        //this.systemPromptTemplate = new SystemPromptTemplate(loadSystemPrompt(systemPromptResource));
        String repositoryId = UUID.randomUUID().toString();
        //String fileDir = System.getProperty("user.dir") + File.separator + "temp/chatMemory" + File.separator + repositoryId;
        BookChatMemory chatMemory = new BookChatMemory(new InMemoryChatMemoryRepository(), repositoryId);
//        FileBasedChatMemory fileBasedChatMemory = new FileBasedChatMemory(fileDir);
        chatClient = ChatClient.builder(dashScopeChatModel)
                //.defaultSystem(systemPromptTemplate.getTemplate())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
    }


    /**
     * AI基础对话
     * @param message 用户消息
     * @param conversationId 会话ID
     * @return AI回复
     */
    public String doChat(String message, String conversationId){
        ChatResponse chatResponse = chatClient
                .prompt()
                //.system(systemPromptTemplate.getTemplate() + "每次都需要生成一个标题为{用户名}的书籍推荐总结，内容为书籍列表")
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();
        if (chatResponse != null){
            return chatResponse.getResult().getOutput().getText();
        }
        return null;
    }

    record Book(String name, String author, String summary) { }
    public record BookList(ArrayList<Book> books) { }

    /**
     * AI基础对话
     * @param message 用户消息
     * @param conversationId 会话ID
     * @return AI回复
     */
    public String doChatWithBookList(String message, String conversationId){
        BookList bookList = chatClient
                .prompt()
                // .system(systemPromptTemplate.getTemplate() + "每次都需要生成一个标题为{用户名}的书籍推荐总结，内容为书籍列表")
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(BookList.class);

        if (bookList != null){
            return bookList.books().stream().map(book -> book.name() + " by " + book.author() + ": " + book.summary()).collect(Collectors.joining("\n"));
        }
        return null;
    }

/*    @jakarta.annotation.Resource
    private VectorStore bookAppVectorStore;

    *//**
     * 带RAG的对话
     * @param message 用户消息
     * @param conversationId 会话ID
     * @return AI回复
     *//*
    public BookList doChatWithRag(String message, String conversationId){
        BookList bookList = chatClient
                .prompt()
                .system(systemPromptTemplate.getTemplate() + "每次都需要生成一个标题为{用户名}的书籍推荐总结，内容为书籍列表")
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                .advisors(new QuestionAnswerAdvisor(bookAppVectorStore))
                .call()
                .entity(BookList.class);
        return bookList;
    }


    @jakarta.annotation.Resource
    private Advisor bookAppRagCloudAdvisor;
    *//**
     * 带云知识库的RAG的对话
     * @param message 用户消息
     * @param conversationId 会话ID
     * @return AI回复
     *//*
    public String doChatWithRagCloud(String message, String conversationId){
        ChatResponse chatResponse = chatClient
                .prompt()
                .system(systemPromptTemplate.getTemplate() + "每次都需要生成一个标题为{用户名}的书籍推荐总结，内容为书籍列表")
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                .advisors(bookAppRagCloudAdvisor)
                .call()
                .chatResponse();

        assert chatResponse != null;
        String res = chatResponse.getResult().getOutput().getText();
        log.info("Rag Cloud response: {}", res);
        return res;
    }

    @jakarta.annotation.Resource
    private VectorStore bookAppPgVectorStore;

    @jakarta.annotation.Resource
    private BookAppQueryRewriter bookAppQueryRewriter;

    *//**
     * 带PgVector的RAG的对话
     * @param message 用户消息
     * @param conversationId 会话ID
     * @return AI回复
     *//*
    public String doChatWithRagPgVector(String message, String conversationId){
        String rewrittenMessage = bookAppQueryRewriter.rewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .system(systemPromptTemplate.getTemplate() + "每次都需要生成一个标题为{用户名}的书籍推荐总结，内容为书籍列表")
                .user(rewrittenMessage)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                //.advisors(new QuestionAnswerAdvisor(bookAppPgVectorStore))
                .advisors(BookAppRagCustomAdvisorFactory.createCustomAdvisor(bookAppPgVectorStore, "悬疑"))
                .call()
                .chatResponse();

        assert chatResponse != null;
        String res = chatResponse.getResult().getOutput().getText();
        log.info("Rag PgVector response: {}", res);
        return res;
    }*/

    @jakarta.annotation.Resource
    private ToolCallback[] allTools;
    public String doChatWithTools(String message, String conversationId){
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();

        assert chatResponse != null;
        return chatResponse.getResult().getOutput().getText();
    }

    @jakarta.annotation.Resource
    private ToolCallbackProvider toolCallbackProvider;
    public String doChatWithMcp(String message, String conversationId){
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();

        assert chatResponse != null;
        return chatResponse.getResult().getOutput().getText();
    }

    public Flux<String> doChatByStream(String message, String conversationId){
        return chatClient.prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                .stream()
                .content();
    }
}
