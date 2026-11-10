package com.bluewhale.pdm.change.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PDM 变更任务
 *
 * <p>挂在 ECO 下，执行阶段完成任务时登记结果版本号；
 * 任务本身不修改已发布版本，只记录本次变更产生的新版本。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_chg_task")
public class PdmChgTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属变更单ID */
    private Long ecoId;

    /** 任务名称 */
    private String taskName;

    /** 任务类型 */
    private String taskType;

    /** 目标对象类型：DOCUMENT/BOM/PART */
    private String targetType;

    /** 目标对象ID */
    private Long targetId;

    /** 目标对象编码 */
    private String targetCode;

    /** 单任务执行说明 */
    private String planDesc;

    /** 负责人 */
    private Long ownerId;

    /** 状态：PENDING/COMPLETED */
    private String status;

    /** 执行结果版本号 */
    private String resultVersionNo;

    /** 排序号 */
    private Integer sortNo;

    /** 完成时间 */
    private LocalDateTime completeTime;
}
