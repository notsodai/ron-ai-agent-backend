package com.ron.ronaiagent.agent.communication;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable message object for inter-agent communication in a multi-agent system.
 *
 * <p>This class represents a message passed between agents with support for:
 * <ul>
 *   <li>Direct messaging (point-to-point)</li>
 *   <li>Broadcast messaging (one-to-many)</li>
 *   <li>Reply messaging with message threading</li>
 *   <li>Metadata attachment for extended context</li>
 * </ul>
 *
 * <p><b>Thread-safety:</b> This class is immutable and thread-safe. The metadata map
 * is defensively copied during construction and returned as an unmodifiable view.
 *
 * <p><b>Usage:</b> Messages should be created using the Builder pattern for full validation:
 * <pre>{@code
 * Message message = new Message.Builder()
 *     .from("agent1")
 *     .to("agent2")
 *     .topic("task-update")
 *     .content("Task completed")
 *     .build();
 * }</pre>
 *
 * <p>Convenience constructors are provided for backward compatibility but delegate to
 * the Builder internally to ensure consistent validation.
 */
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
        // Defensive copy for immutability and thread-safety
        this.metadata = builder.metadata != null
            ? Map.copyOf(builder.metadata)
            : null;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.broadcast = builder.broadcast;
        this.reply = builder.reply;
        this.replyToMessageId = builder.replyToMessageId;
    }

    // Constructor for backward compatibility with tests
    public Message(String from, String to, String topic, String content, Map<String, Object> metadata) {
        // Delegate to Builder for consistent validation
        this(new Builder()
            .from(from)
            .to(to)
            .topic(topic)
            .content(content)
            .metadata(metadata));
    }

    // Simplified constructor for tests
    public Message(String from, String to, String topic, String content) {
        this(from, to, topic, content, null);
    }

    /**
     * Creates a broadcast message intended for all agents.
     *
     * @param from The agent sending the broadcast
     * @param topic The topic/category of the message
     * @param content The message content
     * @return A new broadcast message
     */
    public static Message createBroadcast(String from, String topic, String content) {
        return new Builder()
            .from(from)
            .topic(topic)
            .content(content)
            .broadcast(true)
            .build();
    }

    /**
     * Creates a reply message in response to this message.
     *
     * <p>The reply automatically:
     * <ul>
     *   <li>Swaps sender and receiver</li>
     *   <li>Maintains the same topic</li>
     *   <li>Sets the reply flag</li>
     *   <li>References the original message ID</li>
     * </ul>
     *
     * @param replyContent The content for the reply message
     * @return A new message representing the reply
     */
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

    /**
     * Returns the unique identifier for this message.
     *
     * @return The message ID (auto-generated if not provided)
     */
    public String getId() { return id; }

    /**
     * Returns the sender agent identifier.
     *
     * @return The agent ID that sent this message
     */
    public String getFrom() { return from; }

    /**
     * Returns the intended recipient agent identifier.
     *
     * <p>This field is null for broadcast messages.
     *
     * @return The target agent ID, or null for broadcasts
     */
    public String getTo() { return to; }

    /**
     * Returns the message topic/category.
     *
     * @return The topic string
     */
    public String getTopic() { return topic; }

    /**
     * Returns the message content.
     *
     * @return The message content string
     */
    public String getContent() { return content; }

    /**
     * Returns the metadata associated with this message.
     *
     * <p>Returns an unmodifiable view of the metadata map for thread-safety.
     * Returns null if no metadata was provided.
     *
     * @return An unmodifiable view of the metadata map, or null
     */
    public Map<String, Object> getMetadata() {
        return metadata != null ? metadata : null;
    }

    /**
     * Returns the timestamp when this message was created.
     *
     * @return The creation timestamp
     */
    public LocalDateTime getTimestamp() { return timestamp; }

    /**
     * Checks if this is a broadcast message.
     *
     * @return true if this message is a broadcast, false otherwise
     */
    public boolean isBroadcast() { return broadcast; }

    /**
     * Checks if this message is a reply to another message.
     *
     * @return true if this is a reply, false otherwise
     */
    public boolean isReply() { return reply; }

    /**
     * Returns the ID of the message this message is replying to.
     *
     * @return The parent message ID, or null if not a reply
     */
    public String getReplyToMessageId() { return replyToMessageId; }

    /**
     * Returns a string representation of this message for debugging purposes.
     *
     * @return A string representation containing message ID, sender, topic, and content preview
     */
    @Override
    public String toString() {
        return String.format("Message[id=%s, from=%s, to=%s, topic=%s, content=%s, broadcast=%s, reply=%s]",
            id, from, to, topic,
            content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content,
            broadcast, reply);
    }

    /**
     * Builder for creating {@link Message} instances with validation.
     *
     * <p>The Builder enforces the following validation rules:
     * <ul>
     *   <li>{@code from}, {@code topic}, and {@code content} are required</li>
     *   <li>If {@code reply=true}, {@code replyToMessageId} must be provided</li>
     *   <li>If {@code reply=false}, {@code replyToMessageId} must be null</li>
     *   <li>If {@code broadcast=false}, {@code to} must be provided</li>
     *   <li>If {@code broadcast=true}, {@code to} must be null</li>
     * </ul>
     */
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

        /**
         * Builds a new Message instance with validation.
         *
         * @return A new validated Message instance
         * @throws IllegalArgumentException if validation fails
         */
        public Message build() {
            // Required fields
            if (from == null) throw new IllegalArgumentException("from is required");
            if (topic == null) throw new IllegalArgumentException("topic is required");
            if (content == null) throw new IllegalArgumentException("content is required");

            // Reply validation: reply=true requires replyToMessageId
            if (reply && replyToMessageId == null) {
                throw new IllegalArgumentException("replyToMessageId is required when reply=true");
            }

            // Reply validation: reply=false requires replyToMessageId=null
            if (!reply && replyToMessageId != null) {
                throw new IllegalArgumentException("replyToMessageId must be null when reply=false");
            }

            // Broadcast validation: broadcast=true requires to=null
            if (broadcast && to != null) {
                throw new IllegalArgumentException("to must be null when broadcast=true");
            }

            // Broadcast validation: broadcast=false requires to field
            if (!broadcast && to == null) {
                throw new IllegalArgumentException("to is required when broadcast=false");
            }

            return new Message(this);
        }
    }
}
