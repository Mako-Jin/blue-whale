package com.bluewhale.pdm.part.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * BOM 异步对比任务
 *
 * <p>行数超过在线对比上限时落库异步执行；执行完成后写入结果 JSON 或失败原因，
 * 前端按任务 ID 轮询状态。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_bom_diff_task")
public class PdmBomDiffTask extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 基线 BOM ID */
    private Long baseBomId;

    /** 目标 BOM ID */
    private Long targetBomId;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 对比结果 JSON（PdmBomDiffVO） */
    private String resultJson;

    /** 失败原因 */
    private String errorMessage;

    /** 创建人 */
    private Long createdBy;
}
