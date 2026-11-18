package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入结果：各类计数 + 失败明细。
 *
 * <p>不整批回滚：一条失败不影响其余任务与依赖的导入。</p>
 *
 * @author BlueWhale
 */
@Data
public class JobImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新建任务数 */
    private int createdCount;

    /** 覆盖更新任务数 */
    private int updatedCount;

    /** 跳过任务数（SKIP 策略下已存在） */
    private int skippedCount;

    /** 成功保存的依赖边数 */
    private int dependencyCount;

    /** 失败明细 */
    private List<JobImportFailureVO> failures = new ArrayList<>();
}
