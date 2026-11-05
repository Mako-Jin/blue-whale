package com.bluewhale.pdm.change.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 变更通知（ECN）
 *
 * <p>必须关联已审批通过的变更单；生效日期不早于 ECO 审批通过日；
 * 生效后旧版本置 {@code OBSOLETE}，通知失败不回滚生效事实。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_chg_notice")
public class PdmChgNotice extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 通知编号（租户内唯一） */
    private String noticeNo;

    /** 来源变更单ID */
    private Long ecoId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 影响对象快照 JSON */
    private String impactJson;

    /** 接收人 JSON */
    private String notifyTargets;

    /** 通知渠道，逗号分隔 */
    private String notifyChannels;

    /** 生效日期 */
    private LocalDateTime effectiveFrom;

    /** 状态：DRAFT/NOTIFIED/EFFECTIVE/CLOSED/CANCELLED */
    private String status;

    /** 通知时间 */
    private LocalDateTime notifiedTime;

    /** 实际生效时间 */
    private LocalDateTime effectiveTime;

    /** 关闭时间 */
    private LocalDateTime closeTime;
}
