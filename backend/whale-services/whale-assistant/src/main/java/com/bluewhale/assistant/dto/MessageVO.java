package com.bluewhale.assistant.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 消息视图对象。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class MessageVO {

    /** 消息ID（字符串） */
    private String id;

    /** 角色：user / assistant / system */
    private String role;

    /** 正文（Markdown） */
    private String content;

    /** 引用清单（仅 assistant 消息） */
    private List<CitationVO> citations;

    /** token 统计 */
    private int tokens;

    /** 回答耗时（毫秒，仅 assistant 消息） */
    private int elapsedMs;

    /** 是否降级回答 */
    private boolean degraded;

    /** 创建时间（ISO-8601） */
    private String createTime;
}
