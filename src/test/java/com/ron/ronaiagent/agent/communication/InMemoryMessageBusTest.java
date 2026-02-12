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
}
