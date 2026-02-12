package com.ron.ronaiagent.agent.communication;

import java.util.List;
import java.util.function.Consumer;

/**
 * MessageBus interface for publish/subscribe messaging between agents.
 * <p>
 * This interface defines the contract for inter-agent communication,
 * supporting both topic-based pub/sub messaging and direct point-to-point messaging.
 * Implementations can choose different transport mechanisms (in-memory, message queues, etc.).
 * </p>
 */
public interface MessageBus {

    /**
     * Publish a message to a specific topic.
     * <p>
     * All subscribers to the topic will receive the message.
     * Topics are hierarchical and can use dot notation (e.g., "agent.task.update").
     * </p>
     *
     * @param topic the topic to publish to (must not be null or empty)
     * @param message the message to publish (must not be null)
     */
    void publish(String topic, Message message);

    /**
     * Subscribe to a topic with a message handler.
     * <p>
     * The handler will be called asynchronously when messages are published to the topic.
     * Multiple agents can subscribe to the same topic.
     * </p>
     *
     * @param topic the topic to subscribe to (must not be null or empty)
     * @param subscriberId unique identifier for the subscriber (typically agent ID)
     * @param handler the consumer function to handle incoming messages (must not be null)
     * @return subscription ID that can be used to unsubscribe later
     */
    String subscribe(String topic, String subscriberId, Consumer<Message> handler);

    /**
     * Unsubscribe from a topic.
     * <p>
     * Removes the subscription identified by the subscription ID.
     * After unsubscription, the handler will no longer receive messages.
     * </p>
     *
     * @param subscriptionId the subscription ID returned by subscribe()
     */
    void unsubscribe(String subscriptionId);

    /**
     * Send a direct message from one agent to another.
     * <p>
     * Point-to-point messaging that bypasses topic subscriptions.
     * The message is delivered directly to the recipient agent's message queue.
     * </p>
     *
     * @param message the message to send (must have valid from and to agent IDs)
     */
    void sendDirect(Message message);

    /**
     * Broadcast a message to all subscribers of a topic.
     * <p>
     * Similar to publish(), but explicitly indicates broadcast semantics.
     * All current subscribers will receive the message.
     * </p>
     *
     * @param topic the topic to broadcast to (must not be null or empty)
     * @param message the message to broadcast (must not be null)
     */
    void broadcast(String topic, Message message);

    /**
     * Get all pending messages for a specific agent.
     * <p>
     * Retrieves messages from the agent's message queue.
     * This includes both direct messages and topic-based messages if queued.
     * The queue is typically cleared after retrieval.
     * </p>
     *
     * @param agentId the unique identifier of the agent
     * @return list of messages for the agent (empty list if no messages)
     */
    List<Message> getMessagesForAgent(String agentId);

    /**
     * Clear message queue for an agent.
     * <p>
     * Removes all pending messages for the specified agent.
     * Useful for cleanup or when an agent is shutting down.
     * </p>
     *
     * @param agentId the unique identifier of the agent
     */
    void clearMessagesForAgent(String agentId);

    /**
     * Get message history for a topic.
     * <p>
     * Retrieves historical messages published to a topic.
     * Useful for debugging, auditing, or agent state recovery.
     * </p>
     *
     * @param topic the topic to get history for
     * @param limit maximum number of messages to retrieve (most recent first)
     * @return list of historical messages (empty list if no history)
     */
    List<Message> getMessageHistory(String topic, int limit);
}
