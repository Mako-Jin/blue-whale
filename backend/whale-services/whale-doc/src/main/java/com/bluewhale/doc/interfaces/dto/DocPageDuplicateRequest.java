package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 页面复制请求（M3）。
 *
 * @author BlueWhale
 */
@Data
public class DocPageDuplicateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标空间ID，缺省复制到源页面所在空间 */
    private Long spaceId;

    /** 目标父页面ID，0 或空表示根 */
    private Long parentId;

    /** 新页面标题；缺省为「原标题 - 副本」 */
    @Size(max = 200, message = "页面标题不能超过 200 字")
    private String title;

    /** 是否同时复制子页面，默认 {@code true} */
    private Boolean includeChildren;
}
