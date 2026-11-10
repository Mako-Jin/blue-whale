package com.bluewhale.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 助手用量日聚合实体，对应表 {@code asst_usage}。
 *
 * <p>按 租户×助手×天 聚合；whale-ai 当前返回单一 token 估算值，记入 {@code completionTokens}；
 * 用户级明细从 {@code asst_message} 聚合。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asst_usage")
public class AsstUsage extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属助手ID（asst_definition.id） */
    private Long assistantId;

    /** 统计日期（Asia/Shanghai） */
    private LocalDate statDate;

    /** 新增会话数 */
    private Integer sessionCount;

    /** 消息数（user + assistant） */
    private Integer messageCount;

    /** prompt token 累计（预留，当前恒 0） */
    private Long promptTokens;

    /** completion token 累计 */
    private Long completionTokens;
}
