package com.bluewhale.pdm.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 集成对账结果
 *
 * <p>按 {@code 通道 + 业务类型 + 业务编号 + 版本} 保存最近一次对账结论，
 * 对账只基于本地发布记录与同步日志，不直接改写外部系统数据。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_int_reconcile")
public class PdmIntReconcile extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 通道 */
    private String channel;

    /** 业务类型 */
    private String bizType;

    /** 业务编号 */
    private String bizNo;

    /** 业务主键 */
    private Long bizId;

    /** 版本号 */
    private String versionNo;

    /** PDM 侧状态 */
    private String localStatus;

    /** 外部侧状态 */
    private String remoteStatus;

    /** PDM 校验和 */
    private String localChecksum;

    /** 外部校验和 */
    private String remoteChecksum;

    /** 对账结论 */
    private String matchStatus;

    /** 差异说明 */
    private String diffSummary;

    /** 对账时间 */
    private LocalDateTime checkedAt;
}
