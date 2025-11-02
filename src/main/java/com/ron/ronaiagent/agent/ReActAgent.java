package com.ron.ronaiagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author admin
 * @date 2025/10/12 下午2:38
 * @description: ReAct (Reasoning and Acting) 模式的代理抽象类，实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
public abstract class ReActAgent extends BaseAgent{
    /**
     * 思考
     * @return 思考结果
     */
    public abstract boolean think();

    /**
     * 行动
     * @return 行动结果
     */
    public abstract String act();

    @Override
    public String step() {
        try {
            boolean isShouldAct = think();
            if (!isShouldAct) {
                return "思考完成，无需行动";
            }
            return act();
        } catch (Exception e) {
            return "步骤执行发生错误：" + e.getMessage();
        }
    }
}
