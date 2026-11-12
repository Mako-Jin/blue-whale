package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 敏感词命中留痕实体，对应表 {@code fm_sensitive_hit}。
 *
 * <p>append-only，不继承 BaseEntity（表无 {@code update_time}/{@code deleted}）。
 * 留痕三要素——<b>词、位置、动作</b>——缺一不可：只记「命中过」在申诉时无法自证，
 * 也查不出是不是词库误伤。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_sensitive_hit")
public class ForumSensitiveHit implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 对象类型：THREAD / POST */
    private String targetType;

    /** 对象ID（拦截未落库时为空） */
    private Long targetId;

    /** 触发用户 */
    private Long userId;

    /** 命中词 */
    private String word;

    /** 命中位置（字符偏移） */
    private Integer position;

    /** 动作：BLOCK / REPLACE / PENDING */
    private String action;

    /** 命中时间 */
    private LocalDateTime createTime;
}
