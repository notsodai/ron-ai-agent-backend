package com.ron.ronaiagent.chat.chat.advisor;

import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import reactor.core.publisher.Flux;

import java.util.function.Function;

public class MyLoggerAdvisor  implements CallAdvisor, StreamAdvisor {
    public static final Function<ChatClientRequest, String> DEFAULT_REQUEST_TO_STRING = ChatClientRequest::toString;

    public static final Function<ChatResponse, String> DEFAULT_RESPONSE_TO_STRING = ModelOptionsUtils::toJsonStringPrettyPrinter;

    private static final Logger logger = LoggerFactory.getLogger(MyLoggerAdvisor.class);

    private final Function<ChatClientRequest, String> requestToString;

    private final Function<ChatResponse, String> responseToString;

    private final int order;

    public MyLoggerAdvisor() {
        this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING, 0);
    }

    public MyLoggerAdvisor(int order){
        this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING, order);
    }

    public MyLoggerAdvisor(@Nullable Function<ChatClientRequest, String> requestToString,
                           @Nullable Function<ChatResponse, String> responseToString, int order) {
        this.requestToString = requestToString;
        this.responseToString = responseToString;
        this.order = order;
    }
    @NotNull
    @Override
    public ChatClientResponse adviseCall(@NotNull ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        logRequest(chatClientRequest);

        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

        logResponse(chatClientResponse);

        return chatClientResponse;
    }

    @NotNull
    @Override
    public Flux<ChatClientResponse> adviseStream(@NotNull ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        logRequest(chatClientRequest);

        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

        return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, this::logResponse);
    }

    private void logRequest(ChatClientRequest request) {
        logger.info("request: {}", request.prompt().getUserMessage().getText());
    }

    private void logResponse(ChatClientResponse chatClientResponse) {
        if (chatClientResponse.chatResponse() != null) {
            logger.info("response: {}", chatClientResponse.chatResponse().getResult().getOutput().getText());
        }
    }

    @NotNull
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public String toString() {
        return "MyLoggerAdvisor{" +
                "requestToString=" + requestToString +
                ", responseToString=" + responseToString +
                ", order=" + order +
                '}';
    }

    public static MyLoggerAdvisor.Builder builder() {
        return new MyLoggerAdvisor.Builder();
    }

    public static final class Builder {

        private Function<ChatClientRequest, String> requestToString;

        private Function<ChatResponse, String> responseToString;

        private int order = 0;

        private Builder() {
        }

        public MyLoggerAdvisor.Builder requestToString(Function<ChatClientRequest, String> requestToString) {
            this.requestToString = requestToString;
            return this;
        }

        public MyLoggerAdvisor.Builder responseToString(Function<ChatResponse, String> responseToString) {
            this.responseToString = responseToString;
            return this;
        }

        public MyLoggerAdvisor.Builder order(int order) {
            this.order = order;
            return this;
        }

        public MyLoggerAdvisor build() {
            return new MyLoggerAdvisor(this.requestToString, this.responseToString, this.order);
        }

    }
}
