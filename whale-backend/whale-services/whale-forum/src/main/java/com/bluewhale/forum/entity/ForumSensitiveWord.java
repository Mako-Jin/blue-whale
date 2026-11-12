package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 敏感词实体，对应表 {@code fm_sensitive_word}。
 *
 * <p>{@code word} 在租户内唯一。动作三档：{@code BLOCK} 拦截、{@code REPLACE} 替换、
 * {@code PENDING} 转待审——只有 BLOCK 会直接拒绝，另两档仍允许内容落库。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fm_sensitive_word")
public class ForumSensitiveWord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 敏感词（入库前统一去空格并小写） */
    private String word;

    /** 动作：BLOCK / REPLACE / PENDING */
    private String action;

    /** 分类（AD / ABUSE / ILLEGAL 等，仅统计用） */
    private String category;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 创建人 */
    private Long creatorId;
}
