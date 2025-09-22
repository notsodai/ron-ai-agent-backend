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

/*    @Test
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

    @Test
    void doChatWithRagPgVector() {
        String conversationId = UUID.randomUUID().toString();
        String message = "你好,我是Ron。我比较喜欢玄幻类的书籍，你能向我推荐一些值得阅读的书吗？";
        String answer = bookApp.doChatWithRagPgVector(message, conversationId);
        Assertions.assertNotNull(answer);
    }*/

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("想要看关于Java进阶学习的书籍，有什么值得推荐的");

        // 测试网页抓取
        testMessage("想要看一下关于java学习的内容，请在{codefather.cn}查找相关内容");

        // 测试资源下载：图片下载
        testMessage("直接下载一张适合做手机壁纸的群山图片为文件");

        // 测试文件操作：保存用户档案
        testMessage("保存我的书籍推荐记录为文件");

        // 测试 PDF 生成
        testMessage("生成一份“书籍推荐记录PDF”，包含推荐的书籍，推荐原因以及获得方式");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = bookApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String conversationId = UUID.randomUUID().toString();
        String message = "我想要一张适合做手机壁纸的大海的风景图片";
        String answer = bookApp.doChatWithMcp(message, conversationId);
        Assertions.assertNotNull(answer);
    }
}