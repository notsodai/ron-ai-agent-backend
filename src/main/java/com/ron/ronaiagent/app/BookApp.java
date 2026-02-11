package com.ron.ronaiagent.app;

import cn.hutool.core.lang.UUID;
import com.ron.ronaiagent.chat.advisor.MyLoggerAdvisor;
import com.ron.ronaiagent.chat.memory.BookChatMemory;
import com.ron.ronaiagent.chat.memory.FileBasedChatMemory;
import com.ron.ronaiagent.chat.rag.BookAppQueryRewriter;
import com.ron.ronaiagent.chat.rag.BookAppRagCustomAdvisorFactory;
import com.ron.ronaiagent.core.CacheManager;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BookApp {
    private static ChatClient chatClient;
    private static final Logger log = LoggerFactory.getLogger(BookApp.class);

    @Autowired
    private CacheManager cacheManager;

    // 用于跟踪正在进行的流式请求
    private final List<String> activeStreamRequests = new ArrayList<>();
    private final Object streamLock = new Object();


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
            log.error("Failed to load system prompt", e);
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
     * AI基础对话（带缓存和防重复）
     * @param message 用户消息
     * @param conversationId 会话ID
     * @return AI回复
     */
    public String doChatWithBookList(String message, String conversationId) {
        log.debug("Processing BookApp chat request - Conversation: {}, Message: {}",
                 conversationId, message.substring(0, Math.min(50, message.length())));

        // 生成缓存键
        String cacheKey = generateCacheKey("booklist", conversationId, message);

        // 检查缓存
        String cachedResponse = cacheManager.get(cacheKey, String.class);
        if (cachedResponse != null) {
            log.info("Cache hit for BookApp request - Conversation: {}", conversationId);
            return cachedResponse;
        }

        try {
            BookList bookList = chatClient
                    .prompt()
                    // .system(systemPromptTemplate.getTemplate() + "每次都需要生成一个标题为{用户名}的书籍推荐总结，内容为书籍列表")
                    .user(message)
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .entity(BookList.class);

            if (bookList != null) {
                String response = bookList.books().stream()
                        .map(book -> book.name() + " by " + book.author() + ": " + book.summary())
                        .collect(Collectors.joining("\n"));

                // 缓存响应结果（15分钟）
                cacheManager.put(cacheKey, response, 15);

                log.info("BookApp chat completed successfully - Conversation: {}, Books: {}",
                        conversationId, bookList.books().size());
                return response;
            }

            log.warn("BookApp returned null response - Conversation: {}", conversationId);
            return "抱歉，我暂时无法处理您的请求。";

        } catch (Exception e) {
            log.error("Error in BookApp chat - Conversation: {}", conversationId, e);
            return "处理您的请求时遇到了错误：" + e.getMessage();
        }
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

    /**
     * 流式对话（带防重复和连接管理）
     * @param message 用户消息
     * @param conversationId 会话ID
     * @return 流式响应
     */
    public Flux<String> doChatByStream(String message, String conversationId) {
        log.debug("Starting BookApp stream - Conversation: {}, Message: {}",
                 conversationId, message.substring(0, Math.min(50, message.length())));

        String streamKey = conversationId + "_" + message.hashCode();

        synchronized (streamLock) {
            // 检查是否已有相同的流式请求在进行
            if (activeStreamRequests.contains(streamKey)) {
                log.warn("Duplicate stream request detected for conversation: {}", conversationId);
                return Flux.error(new RuntimeException("Duplicate stream request detected. Please wait for the current request to complete."));
            }

            activeStreamRequests.add(streamKey);
        }

        return chatClient.prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(new MyLoggerAdvisor())
                .stream()
                .content()
                .doOnNext(chunk -> log.debug("Stream chunk for conversation {}: {}",
                        conversationId, chunk.substring(0, Math.min(30, chunk.length()))))
                .doOnComplete(() -> {
                    log.info("BookApp stream completed for conversation: {}", conversationId);
                    synchronized (streamLock) {
                        activeStreamRequests.remove(streamKey);
                    }
                })
                .doOnError(error -> {
                    log.error("BookApp stream error for conversation: {}", conversationId, error);
                    synchronized (streamLock) {
                        activeStreamRequests.remove(streamKey);
                    }
                })
                .doOnCancel(() -> {
                    log.info("BookApp stream cancelled for conversation: {}", conversationId);
                    synchronized (streamLock) {
                        activeStreamRequests.remove(streamKey);
                    }
                });
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(String type, String conversationId, String message) {
        try {
            // 使用消息内容的哈希值生成缓存键
            String content = type + ":" + conversationId + ":" + message.toLowerCase().trim();
            byte[] hash = java.security.MessageDigest.getInstance("MD5").digest(content.getBytes());
            return java.util.Base64.getEncoder().encodeToString(hash).substring(0, 12);
        } catch (Exception e) {
            log.warn("Failed to generate cache key, using fallback", e);
            return type + "_" + conversationId + "_" + Math.abs(message.hashCode());
        }
    }

    /**
     * 清理指定会话的缓存
     */
    public void clearConversationCache(String conversationId) {
        log.info("Clearing cache for conversation: {}", conversationId);
        // 这里可以实现具体的缓存清理逻辑
        // 由于当前的缓存管理器没有基于模式的清理，这个方法预留用于未来扩展
    }

    /**
     * 获取活跃的流式请求数量
     */
    public int getActiveStreamRequestCount() {
        synchronized (streamLock) {
            return activeStreamRequests.size();
        }
    }
}
