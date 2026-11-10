package com.bluewhale.twin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 解读结果视图（对齐 whale-assistant OpenChatVO，引用以通用结构透传，
 * 不引入 assistant 模块依赖）。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class InsightVO {

    /** 回答正文 */
    private String answer;

    /** 是否降级（模型未配置或执行异常时的检索摘要回落） */
    private boolean degraded;

    /** token 统计 */
    private int tokens;

    /** 耗时（毫秒） */
    private int elapsedMillis;

    /** 会话ID（前端可留存续聊，当前实现仅透出） */
    private String sessionId;

    /** 引用清单（键结构同 assistant CitationVO） */
    private List<Map<String, Object>> citations;
}
