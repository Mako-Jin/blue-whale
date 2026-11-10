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
    @NotBlank(message = "门类编码不能为空")
    @Size(max = 32, message = "门类编码长度不能超过 32")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "门类编码仅允许字母、数字与下划线")
    private String code;

    /** 门类名称 */
    @NotBlank(message = "门类名称不能为空")
    @Size(max = 128, message = "门类名称长度不能超过 128")
    private String name;

    /** 上级门类ID（0 表示根节点） */
    @Min(value = 0, message = "上级门类ID不能为负数")
    private Long parentId = 0L;

    /** 默认保管期限（年；空表示长期） */
    @Min(value = 1, message = "保管期限必须大于 0 年")
    private Integer retentionYears;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    @Min(value = 1, message = "密级取值范围为 1-3")
    @Max(value = 3, message = "密级取值范围为 1-3")
    private Integer securityLevel = 2;

    /** 排序号 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sort = 0;

    /** 状态：1 启用 / 0 停用 */
    @Min(value = 0, message = "状态取值范围为 0-1")
    @Max(value = 1, message = "状态取值范围为 0-1")
    private Integer status = 1;
}
