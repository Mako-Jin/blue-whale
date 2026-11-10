package com.bluewhale.doc.interfaces.dto;

import com.bluewhale.common.core.query.PageQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档搜索参数。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocSearchQueryRequest extends PageQuery {

    /** 搜索关键字 */
    @NotBlank(message = "搜索关键字不能为空")
    @Size(max = 64, message = "搜索关键字不能超过 64 字")
    private String keyword;

    /** 限定空间ID；null 表示全部可访问空间 */
    private Long spaceId;

    /** 限定标签ID；null 表示不限（M2-5） */
    private Long tagId;
}
