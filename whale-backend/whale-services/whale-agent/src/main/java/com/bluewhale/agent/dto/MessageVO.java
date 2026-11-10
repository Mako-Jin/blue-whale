package com.bluewhale.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 消息视图对象（含引用块，供前端证据面板回放）。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class MessageVO {

    /** 消息ID（字符串，避免前端精度丢失） */
    private String id;

    /** 角色：user / assistant */
    private String role;

    /** 消息正文（Markdown） */
    private String content;

    /** 引用块 */
    private List<CitationVO> citations;

    /** token 统计 */
    private Integer tokens;

    /** 创建时间（ISO-8601） */
    private String createTime;
}
