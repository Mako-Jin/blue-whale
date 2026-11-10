package com.bluewhale.agent.support.agent;

import java.util.List;

/**
 * 一次工具执行结果。
 *
 * @param text     回填给模型的紧凑文本
 * @param evidence 供引用面板使用的证据
 * @author BlueWhale
 */
public record ToolExecution(String text, List<ToolEvidence> evidence) {

    /**
     * 仅文本结果（无引用价值）。
     *
     * @param text 文本
     * @return 执行结果
     */
    public static ToolExecution textOnly(String text) {
        return new ToolExecution(text, List.of());
    }
}
