package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 论坛经验流水实体，对应表 {@code fm_exp_ledger}。
 *
 * <p>append-only，不继承 BaseEntity。经验只增不扣：取消投花不回滚已发经验
 * （幂等靠唯一键 {@code (tenant_id, user_id, event, ref_thread_id)}——同帖同事件
 * 只发一次，取消再投不会重复加分）。可兑换积分归 {@code whale-mall}，本表是
 * 论坛经验自己的账本，不出域、不与商城对账。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_exp_ledger")
public class ForumExpLedger implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 获得经验的用户ID */
    private Long userId;

    /** 事件：THREAD_PUBLISH / LIKE_GIVEN / ESSENCE_AWARD / HOT_TOP1 */
    private String event;

    /** 经验值（正数） */
    private Integer amount;

    /** 关联帖子ID（事件锚点，幂等键组成部分） */
    private Long refThreadId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
