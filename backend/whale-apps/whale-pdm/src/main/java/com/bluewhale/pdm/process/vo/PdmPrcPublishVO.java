package com.bluewhale.pdm.process.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工艺路线 MES 下发状态视图。
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcPublishVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 下发记录ID */
    private Long id;

    /** 状态：SUCCESS/FAILED/WITHDRAWN */
    private String status;

    /** MES 侧路线编号 */
    private String mesRouteNo;

    /** 重试次数 */
    private Integer retryCount;

    /** 最近失败原因 */
    private String lastError;

    /** 最近下发时间 */
    private LocalDateTime pushedAt;

    /** 撤回时间 */
    private LocalDateTime withdrawnAt;
}
