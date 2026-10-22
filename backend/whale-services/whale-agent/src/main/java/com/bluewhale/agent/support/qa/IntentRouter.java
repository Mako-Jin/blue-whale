package com.bluewhale.agent.support.qa;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 意图路由：规则优先，识别结构化（事实可答）与语义（需检索解释）问题的组合。
 *
 * <p>规则刻意保持简单可解释：命中端口/版本/表字段等词视为结构化诉求，
 * 命中架构/流程/为什么等词视为语义诉求；两者同时命中或问题含具体标识符时为混合。</p>
 *
 * @author BlueWhale
 */
@Component
public class IntentRouter {

    /** 结构化诉求关键词 */
    private static final List<String> STRUCTURED_WORDS = List.of(
            "端口", "port", "版本", "version", "依赖", "路由", "网关", "服务清单",
            "表结构", "表字段", "字段", "列类型", "ddl", "主键", "启动类", "端点", "接口路径");

    /** 语义诉求关键词 */
    private static final List<String> SEMANTIC_WORDS = List.of(
            "为什么", "为何", "如何", "怎么", "怎样", "架构", "设计", "流程", "实现",
            "原理", "方案", "区别", "作用", "机制", "策略", "规范", "约定");

    /**
     * 判定意图。
     *
     * @param question 用户问题
     * @return 意图类型
     */
    public QuestionIntent route(String question) {
        boolean structured = containsAny(question, STRUCTURED_WORDS);
        boolean semantic = containsAny(question, SEMANTIC_WORDS);
        if (structured && semantic) {
            return QuestionIntent.MIXED;
        }
        if (structured) {
            return QuestionIntent.STRUCTURED;
        }
        return QuestionIntent.SEMANTIC;
    }

    /**
     * 判断文本是否命中任一关键词。
     *
     * @param text  文本
     * @param words 关键词
     * @return 命中返回 {@code true}
     */
    private static boolean containsAny(String text, List<String> words) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String word : words) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
