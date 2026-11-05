package com.bluewhale.pdm.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 工艺路线 MES 下发记录
 *
 * <p>幂等键为 {@code routeNo + versionNo}；MES 不可用时下发失败只记录，
 * 不阻塞 PDM 侧路线发布，可通过重试端点补发。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_prc_publish")
public class PdmPrcPublish extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工艺路线ID */
    private Long routeId;

    /** 工艺路线编号快照 */
    private String routeNo;

    /** 版本号快照 */
    private String versionNo;

    /** 状态：SUCCESS/FAILED/WITHDRAWN */
    private String status;

    /** MES 侧路线编号 */
    private String mesRouteNo;

    /** 重试次数 */
    private Integer retryCount;

    /** 最近失败原因 */
    private String lastError;

    /** 幂等键（routeNo:version） */
    private String idempotencyKey;

    /** 最近下发时间 */
    private LocalDateTime pushedAt;

    /** 撤回时间 */
    private LocalDateTime withdrawnAt;
}
