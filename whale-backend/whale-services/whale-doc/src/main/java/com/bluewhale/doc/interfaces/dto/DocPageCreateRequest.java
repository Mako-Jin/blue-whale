package com.bluewhale.doc.interfaces.dto;

import com.bluewhale.doc.domain.model.DocPageType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * 新建页面请求。
 *
 * @author BlueWhale
 */
@Data
public class DocPageCreateRequest {

    /** 空间ID */
    @NotNull(message = "{validation.doc.page.space-id-required}")
    private Long spaceId;

    /** 父页面ID，0 表示根节点 */
    @Min(value = 0, message = "{validation.doc.page.parent-id-min}")
    private Long parentId = 0L;

    /** 页面标题 */
    @NotBlank(message = "{validation.doc.page.title-required}")
    @Size(max = 200, message = "{validation.doc.page.title-size}")
    private String title;

    /** 节点类型 */
    private DocPageType pageType = DocPageType.PAGE;

    /** 自定义 slug；留空按 p-{id} 生成 */
    @Size(max = 128, message = "{validation.doc.page.slug-size}")
    private String slug;

    /** 图标标识 */
    @Size(max = 64, message = "{validation.doc.page.icon-size}")
    private String icon;

    /** 初始正文 JSON（可选） */
    private JsonNode contentJson;

    /** 同级排序；留空追加到末尾 */
    private Long sortNo;
}
