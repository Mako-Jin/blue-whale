package com.bluewhale.pdm.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 集成同步日志
 *
 * <p>每个通道动作以 {@code channel + idempotencyKey} 幂等；失败记录保留原因，
 * 可按通道重新执行，避免重复推送或重复建单。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_int_sync_log")
public class PdmIntSyncLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

    /** 业务编号快照 */
    private String bizNo;

    /** 版本号快照 */
    private String versionNo;

    /** 幂等键 */
    private String idempotencyKey;

    /** 状态 */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 最近失败原因 */
    private String lastError;

    /** 请求内容快照 */
    private String payload;

    /** 外部引用 */
    private String externalRef;

    /** 同步内容校验和 */
    private String checksum;

    /** 发起人 */
    private Long operatorId;

    /** 发起时间 */
    private LocalDateTime requestedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;
}
