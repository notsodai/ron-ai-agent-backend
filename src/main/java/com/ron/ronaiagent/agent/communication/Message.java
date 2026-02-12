package com.ron.ronaiagent.agent.communication;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class Message {
    private final String id;
    private final String from;
    private final String to;
    private final String topic;
    private final String content;
    private final Map<String, Object> metadata;
    private final LocalDateTime timestamp;
    private final boolean broadcast;
    private final boolean reply;
    private final String replyToMessageId;

    private Message(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.from = builder.from;
        this.to = builder.to;
        this.topic = builder.topic;
        this.content = builder.content;
        this.metadata = builder.metadata;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.broadcast = builder.broadcast;
        this.reply = builder.reply;
        this.replyToMessageId = builder.replyToMessageId;
    }

    // Constructor for backward compatibility with tests
    public Message(String from, String to, String topic, String content, Map<String, Object> metadata) {
        this.id = UUID.randomUUID().toString();
        this.from = from;
        this.to = to;
        this.topic = topic;
        this.content = content;
        this.metadata = metadata;
        this.timestamp = LocalDateTime.now();
        this.broadcast = false;
        this.reply = false;
        this.replyToMessageId = null;
    }

    // Simplified constructor for tests
    public Message(String from, String to, String topic, String content) {
        this(from, to, topic, content, null);
    }

    public static Message createBroadcast(String from, String topic, String content) {
        return new Builder()
            .from(from)
            .topic(topic)
            .content(content)
            .broadcast(true)
            .build();
    }

    public Message createReply(String replyContent) {
        return new Builder()
            .from(this.to)
            .to(this.from)
            .topic(this.topic)
            .content(replyContent)
            .reply(true)
            .replyToMessageId(this.id)
            .build();
    }

    // Getters
    public String getId() { return id; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getTopic() { return topic; }
    public String getContent() { return content; }
    public Map<String, Object> getMetadata() { return metadata; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public boolean isBroadcast() { return broadcast; }
    public boolean isReply() { return reply; }
    public String getReplyToMessageId() { return replyToMessageId; }

    public static class Builder {
        private String id;
        private String from;
        private String to;
        private String topic;
        private String content;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        private boolean broadcast;
        private boolean reply;
        private String replyToMessageId;

        public Builder id(String id) { this.id = id; return this; }
        public Builder from(String from) { this.from = from; return this; }
        public Builder to(String to) { this.to = to; return this; }
        public Builder topic(String topic) { this.topic = topic; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder broadcast(boolean broadcast) { this.broadcast = broadcast; return this; }
        public Builder reply(boolean reply) { this.reply = reply; return this; }
        public Builder replyToMessageId(String replyToMessageId) { this.replyToMessageId = replyToMessageId; return this; }

        public Message build() {
            if (from == null) throw new IllegalArgumentException("from is required");
            if (topic == null) throw new IllegalArgumentException("topic is required");
            if (content == null) throw new IllegalArgumentException("content is required");
            return new Message(this);
        }
    }
}
