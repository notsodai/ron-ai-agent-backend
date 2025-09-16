package com.ron.ronaiagent.chat.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * @author admin
 * @date 2025/9/9 下午10:30
 */
public class BookAppContextualQueryAugmenterFactory {
    public static ContextualQueryAugmenter createContextualQueryAugmenter()
    {
        PromptTemplate promptTemplate = new PromptTemplate("基于以下上下文信息：\n{context}\n请回答用户关于 \"{query}\" 的问题。抱歉，我只能回答与悬疑、科幻、历史类书籍有关的问题。请联系客服人员：xxxx");
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .promptTemplate(promptTemplate)
                .build();
    }
}
