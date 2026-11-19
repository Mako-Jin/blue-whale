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
    @NotNull(message = "{validation.archive.item.category-id-required}")
    private Long categoryId;

    /** 档案标题 */
    @Size(max = 256, message = "{validation.archive.item.title-size}")
    private String title;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    @Min(value = 1, message = "{validation.archive.item.security-level-range}")
    @Max(value = 3, message = "{validation.archive.item.security-level-range}")
    private Integer securityLevel;

    /** 保管期限（年；不传表示长期） */
    @Min(value = 1, message = "{validation.archive.item.retention-years-min}")
    @Max(value = 100, message = "{validation.archive.item.retention-years-max}")
    private Integer retentionYears;

    /** 档案摘要 */
    @Size(max = 2000, message = "{validation.archive.item.summary-size}")
    private String summary;

    /** 关键词（逗号分隔） */
    @Size(max = 512, message = "{validation.archive.item.keywords-size}")
    private String keywords;
}
