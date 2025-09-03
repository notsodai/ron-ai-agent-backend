package com.ron.ronaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Spring AI 调用阿里千问大模型
 */
public class SpringAIInvoke implements CommandLineRunner
{
    @Resource
    private ChatModel dashScopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        String text = dashScopeChatModel.call(new Prompt("我的右边肩膀受伤了，肩膀MRI报告如下：右肩冈上下肌腱变性。 右肩胛下肌腱及下盂肱韧带增粗。请你给我一些专业意见，让我在健身的时候避免加重伤情？")).getResult().getOutput().getText();
        System.out.println(text);
    }
}
