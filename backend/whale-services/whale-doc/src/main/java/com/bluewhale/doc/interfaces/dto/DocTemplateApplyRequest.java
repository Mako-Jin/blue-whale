package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 从模板创建页面请求（M3）。
 *
 * @author BlueWhale
 */
@Data
public class DocTemplateApplyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标空间ID */
    @NotNull(message = "目标空间不能为空")
    private Long spaceId;

    /** 目标父页面ID，0 表示根 */
    private Long parentId;

    /** 页面标题；缺省使用模板名称 */
    @Size(max = 200, message = "页面标题不能超过 200 字")
    private String title;

    /** 页面 slug，缺省按新页面ID生成 */
    @Size(max = 128, message = "页面路径不能超过 128 字")
    private String slug;
}
