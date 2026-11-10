package com.bluewhale.agent.support.qa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 意图路由规则测试。
 *
 * @author BlueWhale
 */
class IntentRouterTest {

    private final IntentRouter router = new IntentRouter();

    @Test
    @DisplayName("端口/版本类问题判定为结构化")
    void structured() {
        assertEquals(QuestionIntent.STRUCTURED, router.route("whale-agent 的端口是多少"));
        assertEquals(QuestionIntent.STRUCTURED, router.route("agent_fact 表字段有哪些"));
    }

    @Test
    @DisplayName("架构/为什么类问题判定为语义")
    void semantic() {
        assertEquals(QuestionIntent.SEMANTIC, router.route("审批流程是如何实现的"));
        assertEquals(QuestionIntent.SEMANTIC, router.route("为什么要采用双通道问答设计"));
    }

    @Test
    @DisplayName("同时命中两类关键词判定为混合")
    void mixed() {
        assertEquals(QuestionIntent.MIXED, router.route("为什么 whale-agent 的端口是 8090"));
    }

    @Test
    @DisplayName("空问题回落语义")
    void blank() {
        assertEquals(QuestionIntent.SEMANTIC, router.route("  "));
    }
}
