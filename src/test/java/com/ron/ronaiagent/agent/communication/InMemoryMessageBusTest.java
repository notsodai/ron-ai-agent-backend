package com.ron.ronaiagent.agent.communication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class InMemoryMessageBusTest {
    private MessageBus messageBus;

    @BeforeEach
    void setUp() {
        messageBus = new InMemoryMessageBus();
    }

    @Test
    void testPublishAndSubscribe() {
        List<Message> receivedMessages = new ArrayList<>();

        String subscriptionId = messageBus.subscribe(
            "test-topic",
            "agent1",
            message -> receivedMessages.add(message)
        );

        Message message = new Message.Builder()
            .from("agent2")
            .to("agent1")
            .topic("test-topic")
            .content("Test message")
            .build();

        messageBus.publish("test-topic", message);

        assertEquals(1, receivedMessages.size());
        assertEquals("Test message", receivedMessages.get(0).getContent());
    }

    @Test
    void testDirectMessage() {
        List<Message> receivedMessages = new ArrayList<>();

        messageBus.subscribe("direct", "agent1", msg -> receivedMessages.add(msg));

        Message message = new Message.Builder()
            .from("agent2")
            .to("agent1")
            .topic("direct")
            .content("Direct message")
            .build();

        messageBus.sendDirect(message);

        assertEquals(1, receivedMessages.size());
        assertEquals("agent1", receivedMessages.get(0).getTo());
    }

    @Test
    void testBroadcast() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger agent1Count = new AtomicInteger(0);
        AtomicInteger agent2Count = new AtomicInteger(0);

        messageBus.subscribe("broadcast-topic", "agent1", msg -> {
            agent1Count.incrementAndGet();
            latch.countDown();
        });

        messageBus.subscribe("broadcast-topic", "agent2", msg -> {
            agent2Count.incrementAndGet();
            latch.countDown();
        });

        Message broadcastMsg = Message.createBroadcast(
            "broadcaster",
            "broadcast-topic",
            "Broadcast to all"
        );

        messageBus.broadcast("broadcast-topic", broadcastMsg);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, agent1Count.get());
        assertEquals(1, agent2Count.get());
    }

    @Test
    void testUnsubscribe() {
        List<Message> receivedMessages = new ArrayList<>();

        String subscriptionId = messageBus.subscribe(
            "test-topic",
            "agent1",
            message -> receivedMessages.add(message)
        );

        Message msg1 = new Message.Builder()
            .from("agent2").to("agent1").topic("test-topic").content("msg1").build();
        messageBus.publish("test-topic", msg1);

        messageBus.unsubscribe(subscriptionId);

        Message msg2 = new Message.Builder()
            .from("agent2").to("agent1").topic("test-topic").content("msg2").build();
        messageBus.publish("test-topic", msg2);

        assertEquals(1, receivedMessages.size());
        assertEquals("msg1", receivedMessages.get(0).getContent());
    }

    @Test
    void testMessageQueueForAgent() {
        messageBus.sendDirect(new Message.Builder()
            .from("agent2").to("agent1").topic("direct").content("msg1").build());
        messageBus.sendDirect(new Message.Builder()
            .from("agent3").to("agent1").topic("direct").content("msg2").build());

        List<Message> messages = messageBus.getMessagesForAgent("agent1");

        assertEquals(2, messages.size());
        messageBus.clearMessagesForAgent("agent1");
        assertEquals(0, messageBus.getMessagesForAgent("agent1").size());
    }

    @Test
    void testMessageHistory() {
        for (int i = 0; i < 5; i++) {
            messageBus.publish("history-topic", new Message.Builder()
                .from("agent1").to("agent2").topic("history-topic").content("msg" + i).build());
        }

        List<Message> history = messageBus.getMessageHistory("history-topic", 3);
        assertEquals(3, history.size());
    }

    @Test
    void testConcurrentPublish() throws InterruptedException {
        int threadCount = 10;
        int messagesPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger totalReceived = new AtomicInteger(0);

        // Subscribe with a thread-safe counter
        messageBus.subscribe("concurrent-topic", "collector", msg -> {
            totalReceived.incrementAndGet();
        });

        // Create multiple threads publishing simultaneously
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < messagesPerThread; j++) {
                        messageBus.publish("concurrent-topic", new Message.Builder()
                            .from("sender").to("receiver").topic("concurrent-topic")
                            .content("message-" + j).build());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        assertTrue(endLatch.await(10, TimeUnit.SECONDS),
            "All threads should complete within timeout");

        // Give handlers a moment to finish processing
        Thread.sleep(100);

        // Verify all messages were received
        int expectedMessages = threadCount * messagesPerThread;
        assertEquals(expectedMessages, totalReceived.get(),
            "All messages should be received in concurrent environment");
    }

    @Test
    void testPublishToNonExistentTopic() {
        // Should not throw exception
        assertDoesNotThrow(() -> {
            messageBus.publish("non-existent-topic", new Message.Builder()
                .from("agent1").to("agent2").topic("non-existent-topic")
                .content("test").build());
        });
    }

    @Test
    void testUnsubscribeNonExistentSubscription() {
        // Should not throw exception
        assertDoesNotThrow(() -> {
            messageBus.unsubscribe("non-existent-id");
        });
    }

    @Test
    void testPublishWithNullTopic() {
        assertThrows(IllegalArgumentException.class, () -> {
            messageBus.publish(null, new Message.Builder()
                .from("agent1").to("agent2").topic("test").content("test").build());
        });
    }

    @Test
    void testPublishWithNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> {
            messageBus.publish("test-topic", null);
        });
    }

    @Test
    void testBroadcastWithNullTopic() {
        assertThrows(IllegalArgumentException.class, () -> {
            messageBus.broadcast(null, new Message.Builder()
                .from("agent1").to("agent2").topic("test").content("test").build());
        });
    }

    @Test
    void testBroadcastWithNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> {
            messageBus.broadcast("test-topic", null);
        });
    }

    @Test
    void testSendDirectWithNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> {
            messageBus.sendDirect(null);
        });
    }

    @Test
    void testConcurrentSubscribeUnsubscribe() throws InterruptedException {
        int threadCount = 5;
        int operationsPerThread = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        List<String> subscriptionIds = new ArrayList<>();

        // Create threads that subscribe and unsubscribe concurrently
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        String subId = messageBus.subscribe("concurrent-sub-topic",
                            "agent" + j, msg -> {});
                        synchronized (subscriptionIds) {
                            subscriptionIds.add(subId);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        // Verify we have the expected number of subscriptions
        assertEquals(threadCount * operationsPerThread, subscriptionIds.size());

        // Now unsubscribe all concurrently
        CountDownLatch unsubscribeLatch = new CountDownLatch(subscriptionIds.size());
        for (String subId : subscriptionIds) {
            new Thread(() -> {
                try {
                    messageBus.unsubscribe(subId);
                } finally {
                    unsubscribeLatch.countDown();
                }
            }).start();
        }

        assertTrue(unsubscribeLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void testSendDirectQueuesForAllSubscribers() {
        List<Message> receivedMessages = new ArrayList<>();

        // Subscribe to the topic
        messageBus.subscribe("direct-topic", "agent1", msg -> receivedMessages.add(msg));

        // Send direct message to agent2
        Message directMsg = new Message.Builder()
            .from("sender")
            .to("agent2")
            .topic("direct-topic")
            .content("Direct to agent2")
            .build();

        messageBus.sendDirect(directMsg);

        // Topic subscriber should receive the message
        assertEquals(1, receivedMessages.size());
        assertEquals("Direct to agent2", receivedMessages.get(0).getContent());

        // Message should be queued for both agent2 (recipient) and agent1 (subscriber)
        List<Message> agent2Messages = messageBus.getMessagesForAgent("agent2");
        List<Message> agent1Messages = messageBus.getMessagesForAgent("agent1");

        assertEquals(1, agent2Messages.size());
        assertEquals(1, agent1Messages.size());
    }

    @Test
    void testHistoryTrimming() {
        // Publish more messages than MAX_HISTORY_SIZE
        for (int i = 0; i < 1500; i++) {
            messageBus.publish("trim-topic", new Message.Builder()
                .from("agent1").to("agent2").topic("trim-topic")
                .content("message-" + i).build());
        }

        // History should be trimmed to MAX_HISTORY_SIZE
        List<Message> history = messageBus.getMessageHistory("trim-topic", 2000);
        assertTrue(history.size() <= 1000,
            "History should not exceed max size");
    }
}
