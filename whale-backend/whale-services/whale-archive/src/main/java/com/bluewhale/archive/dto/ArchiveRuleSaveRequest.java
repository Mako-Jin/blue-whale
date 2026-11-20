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
    @NotBlank(message = "{validation.archive.rule.definition-code-required}")
    @Size(max = 64, message = "{validation.archive.rule.definition-code-size}")
    private String definitionCode;

    /** 归档门类ID */
    @NotNull(message = "{validation.archive.rule.category-id-required}")
    private Long categoryId;

    /** 保管期限（年；空表示长期） */
    @Min(value = 1, message = "{validation.archive.rule.retention-years-min}")
    private Integer retentionYears;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    @Min(value = 1, message = "{validation.archive.rule.security-level-range}")
    @Max(value = 3, message = "{validation.archive.rule.security-level-range}")
    private Integer securityLevel = 2;

    /** 是否自动归档：1 是 / 0 否 */
    @Min(value = 0, message = "{validation.archive.rule.auto-archive-range}")
    @Max(value = 1, message = "{validation.archive.rule.auto-archive-range}")
    private Integer autoArchive = 0;

    /** 是否启用：1 启用 / 0 停用 */
    @Min(value = 0, message = "{validation.archive.rule.enabled-range}")
    @Max(value = 1, message = "{validation.archive.rule.enabled-range}")
    private Integer enabled = 1;
}
