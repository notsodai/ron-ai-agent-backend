package com.ron.ronaiagent.agent.communication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class InMemoryMessageBus implements MessageBus {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryMessageBus.class);
    private static final int MAX_HISTORY_SIZE = 1000;

    private final Map<String, List<Subscription>> topicSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Queue<Message>> agentMessageQueues = new ConcurrentHashMap<>();
    private final Map<String, List<Message>> topicHistory = new ConcurrentHashMap<>();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void publish(String topic, Message message) {
        if (topic == null) {
            throw new IllegalArgumentException("Topic cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        addToHistory(topic, message);

        List<Subscription> subscribers = topicSubscriptions.get(topic);
        if (subscribers != null) {
            // Create defensive snapshot to avoid race condition
            new ArrayList<>(subscribers).forEach(sub -> {
                // Verify subscription still exists before invoking handler
                if (subscriptions.containsKey(sub.subscriptionId)) {
                    try {
                        if (sub.handler != null) {
                            sub.handler.accept(message);
                        }
                    } catch (Exception e) {
                        logger.error("Error in message handler for subscription {}: {}",
                            sub.subscriptionId, e.getMessage(), e);
                    }
                    queueMessage(sub.subscriberId, message);
                }
            });
        }
    }

    @Override
    public String subscribe(String topic, String subscriberId, Consumer<Message> handler) {
        String subscriptionId = UUID.randomUUID().toString();
        Subscription subscription = new Subscription(subscriptionId, topic, subscriberId, handler);

        subscriptions.put(subscriptionId, subscription);
        topicSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
            .add(subscription);

        return subscriptionId;
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        Subscription subscription = subscriptions.remove(subscriptionId);
        if (subscription != null) {
            List<Subscription> subscribers = topicSubscriptions.get(subscription.topic);
            if (subscribers != null) {
                subscribers.remove(subscription);
            }
        }
    }

    @Override
    public void sendDirect(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        String topic = message.getTopic();
        addToHistory(topic, message);

        // Queue message for the specific recipient
        queueMessage(message.getTo(), message);

        // Also notify subscribers if any (and queue for them too)
        List<Subscription> subscribers = topicSubscriptions.get(topic);
        if (subscribers != null) {
            // Create defensive snapshot to avoid race condition
            new ArrayList<>(subscribers).forEach(sub -> {
                if (subscriptions.containsKey(sub.subscriptionId)) {
                    try {
                        if (sub.handler != null) {
                            sub.handler.accept(message);
                        }
                    } catch (Exception e) {
                        logger.error("Error in message handler for subscription {}: {}",
                            sub.subscriptionId, e.getMessage(), e);
                    }
                    // Also queue message for topic subscribers (consistent with publish)
                    queueMessage(sub.subscriberId, message);
                }
            });
        }
    }

    @Override
    public void broadcast(String topic, Message message) {
        if (topic == null) {
            throw new IllegalArgumentException("Topic cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        publish(topic, message);
    }

    @Override
    public List<Message> getMessagesForAgent(String agentId) {
        Queue<Message> queue = agentMessageQueues.get(agentId);
        if (queue == null) {
            return List.of();
        }
        return new ArrayList<>(queue);
    }

    @Override
    public void clearMessagesForAgent(String agentId) {
        agentMessageQueues.remove(agentId);
    }

    @Override
    public List<Message> getMessageHistory(String topic, int limit) {
        List<Message> history = topicHistory.get(topic);
        if (history == null) {
            return List.of();
        }

        int startIndex = Math.max(0, history.size() - limit);
        // Return unmodifiable subList to avoid double copy
        return Collections.unmodifiableList(history.subList(startIndex, history.size()));
    }

    private void queueMessage(String agentId, Message message) {
        agentMessageQueues.computeIfAbsent(agentId, k -> new ConcurrentLinkedQueue<>())
            .add(message);
    }

    private void addToHistory(String topic, Message message) {
        List<Message> history = topicHistory.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>());
        history.add(message);

        // Trim history if it exceeds max size to prevent unbounded memory growth
        if (history.size() > MAX_HISTORY_SIZE) {
            int elementsToRemove = history.size() - MAX_HISTORY_SIZE;
            // Remove oldest messages (from the beginning)
            if (history instanceof CopyOnWriteArrayList<?>) {
                // CopyOnWriteArrayList doesn't support subList clear, so recreate
                List<Message> trimmed = new ArrayList<>(history.subList(elementsToRemove, history.size()));
                history.clear();
                history.addAll(trimmed);
            } else {
                // For other list types, use subList clearing
                history.subList(0, elementsToRemove).clear();
            }
            logger.debug("Trimmed message history for topic '{}' by {} messages to stay within limit of {}",
                topic, elementsToRemove, MAX_HISTORY_SIZE);
        }
    }

    private static class Subscription {
        final String subscriptionId;
        final String topic;
        final String subscriberId;
        final Consumer<Message> handler;

        Subscription(String subscriptionId, String topic, String subscriberId, Consumer<Message> handler) {
            this.subscriptionId = subscriptionId;
            this.topic = topic;
            this.subscriberId = subscriberId;
            this.handler = handler;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Subscription that = (Subscription) o;
            return Objects.equals(subscriptionId, that.subscriptionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subscriptionId);
        }
    }
}
