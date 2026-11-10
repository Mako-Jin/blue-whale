package com.bluewhale.mes.process.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MES 工艺路线接收结果。
 *
 * @author BlueWhale
 */
@Data
public class MesProcessRouteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long id;

    /** PDM 工艺路线编号 */
    private String pdmRouteNo;

    /** 版本号 */
    private String versionNo;

    /** 零件编码 */
    private String partCode;

    /** 零件名称 */
    private String partName;

    /** 状态：ACTIVE/WITHDRAWN */
    private String status;

    /** 校验和 */
    private String checksum;

    /** 工序数量 */
    private Integer stepCount;

    /** 下发时间 */
    private LocalDateTime pushedAt;

    /** 撤回时间 */
    private LocalDateTime withdrawnAt;

    /** 是否幂等命中（重复下发未重复落库） */
    private Boolean idempotent;
}
