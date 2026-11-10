package com.bluewhale.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 流程实例版本迁移请求：把运行中实例从当前锁定版本迁到目标版本。
 *
 * @author BlueWhale
 */
@Data
public class DefinitionMigrationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标版本号（必填，须存在对应快照） */
    @NotNull(message = "目标版本号不能为空")
    private Integer targetVersion;

    /** 需要迁移的实例 ID 列表；为空表示该定义下全部运行中实例 */
    private List<Long> instanceIds;
}
