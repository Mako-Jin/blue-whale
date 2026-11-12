package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 计数对账轨迹，对应表 {@code fm_count_reconcile}。
 *
 * <p>append-only：不继承 {@code BaseEntity}，没有 update_time / deleted——
 * 轨迹被改写就等于对账失去可追溯性。一次核对里每一项差异各留一行，
 * 「只报告未修」也留，否则事后无法区分「没差异」和「有差异但没修」。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("fm_count_reconcile")
public class ForumCountReconcile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 帖子ID */
    private Long threadId;

    /** 计数项：REPLY / LIKE / COLLECT */
    private String metric;

    /** 核对前展示值 */
    private Integer valueBefore;

    /** 按明细表 COUNT 得到的真实值 */
    private Integer valueActual;

    /** 是否已按真实值修正：1 是 / 0 只报告未修 */
    private Integer fixed;

    /** 触发人（定时任务为 NULL） */
    private Long operatorId;

    /** 批次号 */
    private String batchNo;

    /** 创建时间 */
    private LocalDateTime createTime;
}
