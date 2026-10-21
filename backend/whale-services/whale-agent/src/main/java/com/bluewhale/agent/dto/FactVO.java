package com.bluewhale.agent.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 结构化事实视图对象（{@code GET /agent/facts} 响应项）。
 *
 * @author BlueWhale
 */
@Data
@Builder
public class FactVO {

    /** 事实 ID（字符串，避免前端精度丢失） */
    private String id;

    /** 事实类型 */
    private String factType;

    /** 事实键 */
    private String factKey;

    /** 事实值（JSON 原样返回） */
    private Object value;

    /** 来源路径 */
    private String sourcePath;

    /** 采集时间（ISO-8601） */
    private String capturedAt;
}
