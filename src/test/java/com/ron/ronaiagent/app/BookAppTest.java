package com.ron.ronaiagent.app;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BookAppTest {

    @Resource
    private BookApp bookApp;
    @Test
    void doChatTest() {
        String conversationId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好,我是Ron";
        String answer = bookApp.doChat(message, conversationId);

        // 第二轮
        message = "我比较喜欢悬疑类的书籍，你能向我推荐一些值得阅读的书吗？";
        answer = bookApp.doChat(message, conversationId);
        Assertions.assertNotNull(answer);

        // 第三轮
        message = "谢谢，那我应该先阅读哪一本？";
        answer = bookApp.doChat(message, conversationId);
        Assertions.assertNotNull(answer);

    }

    @Test
    void doChatWithBookListTest() {
        String conversationId = UUID.randomUUID().toString();
        String message = "你好,我是Ron。我比较喜欢科幻类的书籍，你能向我推荐一些值得阅读的书吗？";
        String answer = bookApp.doChatWithBookList(message, conversationId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithRagTest() {
        String conversationId = UUID.randomUUID().toString();
        String message = "你好,我是Ron。我比较喜欢科幻类的书籍，你能向我推荐一些值得阅读的书吗？";
        BookApp.BookList answer = bookApp.doChatWithRag(message, conversationId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithRagCloud() {
        String conversationId = UUID.randomUUID().toString();
        String message = "你好,我是Ron。我比较喜欢悬疑类的书籍，你能向我推荐一些值得阅读的书吗？";
        String answer = bookApp.doChatWithRagCloud(message, conversationId);
        Assertions.assertNotNull(answer);
    }
}