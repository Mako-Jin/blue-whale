package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程实例版本迁移结果视图对象。
 *
 * @author BlueWhale
 */
@Data
public class DefinitionMigrationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流程定义ID */
    private Long definitionId;

    /** 目标版本号 */
    private Integer targetVersion;

    /** 成功迁移的实例数 */
    private Integer migrated = 0;

    /** 跳过的实例数（非运行中或已在目标版本） */
    private Integer skipped = 0;

    /** 迁移失败的实例数 */
    private Integer failed = 0;

    /** 失败明细：{@code 实例ID: 原因} */
    private List<String> failures = new ArrayList<>();
}
