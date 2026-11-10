package com.bluewhale.pdm.integration.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 集成同步日志视图
 *
 * @author BlueWhale
 */
@Data
public class PdmIntSyncLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 通道 */
    private String channel;

    /** 动作 */
    private String action;

    /** 方向 */
    private String direction;

    /** 业务类型 */
    private String bizType;

    /** 业务主键 */
    private Long bizId;

    /** 业务编号 */
    private String bizNo;

    /** 版本号 */
    private String versionNo;

    /** 幂等键 */
    private String idempotencyKey;

    /** 状态 */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 最近失败原因 */
    private String lastError;

    /** 外部引用 */
    private String externalRef;

    /** 校验和 */
    private String checksum;

    /** 发起人 */
    private Long operatorId;

    /** 发起时间 */
    private LocalDateTime requestedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
