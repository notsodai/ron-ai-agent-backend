package com.ron.ronaiagent.agent.communication;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class InMemoryMessageBus implements MessageBus {

    private final Map<String, List<Subscription>> topicSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Queue<Message>> agentMessageQueues = new ConcurrentHashMap<>();
    private final Map<String, List<Message>> topicHistory = new ConcurrentHashMap<>();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    @Override
    public void publish(String topic, Message message) {
        addToHistory(topic, message);

        List<Subscription> subscribers = topicSubscriptions.get(topic);
        if (subscribers != null) {
            subscribers.forEach(sub -> {
                if (sub.handler != null) {
                    sub.handler.accept(message);
                }
                queueMessage(sub.subscriberId, message);
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
        String topic = message.getTopic();
        addToHistory(topic, message);

        // Queue message for the specific recipient
        queueMessage(message.getTo(), message);

        // Also notify subscribers if any
        List<Subscription> subscribers = topicSubscriptions.get(topic);
        if (subscribers != null) {
            subscribers.forEach(sub -> {
                if (sub.handler != null) {
                    sub.handler.accept(message);
                }
            });
        }
    }

    @Override
    public void broadcast(String topic, Message message) {
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
        return new ArrayList<>(history.subList(startIndex, history.size()));
    }

    private void queueMessage(String agentId, Message message) {
        agentMessageQueues.computeIfAbsent(agentId, k -> new ConcurrentLinkedQueue<>())
            .add(message);
    }

    private void addToHistory(String topic, Message message) {
        topicHistory.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
            .add(message);
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
    }
}
