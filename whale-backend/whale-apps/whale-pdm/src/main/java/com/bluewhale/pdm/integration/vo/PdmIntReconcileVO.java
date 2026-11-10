package com.bluewhale.pdm.integration.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 集成对账结果视图
 *
 * @author BlueWhale
 */
@Data
public class PdmIntReconcileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

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
