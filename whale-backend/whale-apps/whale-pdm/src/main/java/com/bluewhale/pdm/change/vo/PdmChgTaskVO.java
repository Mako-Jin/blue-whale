package com.bluewhale.pdm.change.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 变更任务视图
 *
 * @author BlueWhale
 */
@Data
public class PdmChgTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long id;

    /** 所属变更单ID */
    private Long ecoId;

    /** 任务名称 */
    private String taskName;

    /** 任务类型 */
    private String taskType;

    /** 目标对象类型 */
    private String targetType;

    /** 目标对象ID */
    private Long targetId;

    /** 目标对象编码 */
    private String targetCode;

    /** 单任务执行说明 */
    private String planDesc;

    /** 负责人 */
    private Long ownerId;

    /** 状态 */
    private String status;

    /** 执行结果版本号 */
    private String resultVersionNo;

    /** 排序号 */
    private Integer sortNo;

    /** 完成时间 */
    private LocalDateTime completeTime;
}
