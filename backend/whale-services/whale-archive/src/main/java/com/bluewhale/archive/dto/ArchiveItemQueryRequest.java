package com.bluewhale.archive.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 档案条目分页查询条件。
 *
 * <p>{@code keyword} 模糊匹配档号、标题、摘要与关键词；其余条件精确匹配。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArchiveItemQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键词（档号/标题/摘要/关键词模糊匹配） */
    private String keyword;

    /** 门类ID（精确匹配） */
    private Long categoryId;

    /** 归档年度 */
    private Integer year;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    private Integer securityLevel;

    /** 条目状态：0 待整理 / 1 已归档 / 2 已销毁 */
    private Integer itemStatus;
}
