package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * BOM 异步对比任务视图
 *
 * @author BlueWhale
 */
@Data
public class PdmBomDiffTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private Long id;

    /** 基线 BOM ID */
    private Long baseBomId;

    /** 目标 BOM ID */
    private Long targetBomId;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    /** 对比结果（仅 SUCCESS 时有值） */
    private PdmBomDiffVO diff;

    /** 创建时间 */
    private LocalDateTime createTime;
}
