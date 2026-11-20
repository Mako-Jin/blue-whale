package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 移动页面请求。
 *
 * @author BlueWhale
 */
@Data
public class DocPageMoveRequest {

    /** 目标父页面ID，0 表示根节点 */
    @NotNull(message = "{validation.doc.page.target-parent-id-required}")
    @Min(value = 0, message = "{validation.doc.page.target-parent-id-min}")
    private Long parentId;

    /** 目标排序；null 表示保持原值 */
    private Long sortNo;
}
