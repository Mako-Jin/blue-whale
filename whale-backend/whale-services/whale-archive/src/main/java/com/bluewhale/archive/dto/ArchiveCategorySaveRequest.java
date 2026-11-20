package com.bluewhale.archive.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 档案门类新增/修改请求。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveCategorySaveRequest {

    /** 门类编码（业务唯一） */
    @NotBlank(message = "{validation.archive.category.code-required}")
    @Size(max = 32, message = "{validation.archive.category.code-size}")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "{validation.archive.category.code-pattern}")
    private String code;

    /** 门类名称 */
    @NotBlank(message = "{validation.archive.category.name-required}")
    @Size(max = 128, message = "{validation.archive.category.name-size}")
    private String name;

    /** 上级门类ID（0 表示根节点） */
    @Min(value = 0, message = "{validation.archive.category.parent-id-min}")
    private Long parentId = 0L;

    /** 默认保管期限（年；空表示长期） */
    @Min(value = 1, message = "{validation.archive.category.retention-years-min}")
    private Integer retentionYears;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    @Min(value = 1, message = "{validation.archive.category.security-level-range}")
    @Max(value = 3, message = "{validation.archive.category.security-level-range}")
    private Integer securityLevel = 2;

    /** 排序号 */
    @Min(value = 0, message = "{validation.archive.category.sort-min}")
    private Integer sort = 0;

    /** 状态：1 启用 / 0 停用 */
    @Min(value = 0, message = "{validation.archive.category.status-range}")
    @Max(value = 1, message = "{validation.archive.category.status-range}")
    private Integer status = 1;
}
