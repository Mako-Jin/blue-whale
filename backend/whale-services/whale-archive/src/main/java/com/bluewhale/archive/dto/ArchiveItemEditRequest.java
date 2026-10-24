package com.bluewhale.archive.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 档案条目整理请求：仅待整理条目可修改。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveItemEditRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 归档门类ID（必填，须为启用门类） */
    @NotNull(message = "归档门类不能为空")
    private Long categoryId;

    /** 档案标题 */
    @Size(max = 256, message = "档案标题不能超过 256 字")
    private String title;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    @Min(value = 1, message = "密级取值 1-3")
    @Max(value = 3, message = "密级取值 1-3")
    private Integer securityLevel;

    /** 保管期限（年；不传表示长期） */
    @Min(value = 1, message = "保管期限至少 1 年")
    @Max(value = 100, message = "保管期限不能超过 100 年")
    private Integer retentionYears;

    /** 档案摘要 */
    @Size(max = 2000, message = "档案摘要不能超过 2000 字")
    private String summary;

    /** 关键词（逗号分隔） */
    @Size(max = 512, message = "关键词不能超过 512 字")
    private String keywords;
}
