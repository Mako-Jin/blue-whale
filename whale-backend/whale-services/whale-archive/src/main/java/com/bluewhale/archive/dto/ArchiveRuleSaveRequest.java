package com.bluewhale.archive.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 归档规则新增/修改请求。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveRuleSaveRequest {

    /** 工作流定义编码（业务唯一） */
    @NotBlank(message = "流程定义编码不能为空")
    @Size(max = 64, message = "流程定义编码长度不能超过 64")
    private String definitionCode;

    /** 归档门类ID */
    @NotNull(message = "归档门类不能为空")
    private Long categoryId;

    /** 保管期限（年；空表示长期） */
    @Min(value = 1, message = "保管期限必须大于 0 年")
    private Integer retentionYears;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    @Min(value = 1, message = "密级取值范围为 1-3")
    @Max(value = 3, message = "密级取值范围为 1-3")
    private Integer securityLevel = 2;

    /** 是否自动归档：1 是 / 0 否 */
    @Min(value = 0, message = "自动归档取值范围为 0-1")
    @Max(value = 1, message = "自动归档取值范围为 0-1")
    private Integer autoArchive = 0;

    /** 是否启用：1 启用 / 0 停用 */
    @Min(value = 0, message = "启用状态取值范围为 0-1")
    @Max(value = 1, message = "启用状态取值范围为 0-1")
    private Integer enabled = 1;
}
