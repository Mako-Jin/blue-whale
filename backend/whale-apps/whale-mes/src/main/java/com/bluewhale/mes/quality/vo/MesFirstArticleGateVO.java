package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首件门禁结果
 *
 * @author BlueWhale
 */
@Data
public class MesFirstArticleGateVO {

    /** 工单 ID */
    private Long workOrderId;

    /** 工序编码 */
    private String operationCode;

    /** 是否放行（最近一次首件判定为 PASSED） */
    private Boolean passed;

    /** 最近一次首件检验任务号 */
    private String taskNo;

    /** 最近一次首件检验状态 */
    private String status;

    /** 判定时间 */
    private LocalDateTime judgedAt;

    /** 门禁说明 */
    private String message;
}
