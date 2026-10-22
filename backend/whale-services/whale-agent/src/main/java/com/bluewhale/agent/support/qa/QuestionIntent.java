package com.bluewhale.agent.support.qa;

/**
 * 问题意图类型：决定检索走事实通道、语义通道还是两者。
 *
 * @author BlueWhale
 */
public enum QuestionIntent {

    /** 结构化问题（端口/版本/表字段/路由等），优先查事实 */
    STRUCTURED,

    /** 语义问题（架构/流程/实现原理等），走全文检索 */
    SEMANTIC,

    /** 混合问题，事实优先注入 + 全文证据补充 */
    MIXED
}
