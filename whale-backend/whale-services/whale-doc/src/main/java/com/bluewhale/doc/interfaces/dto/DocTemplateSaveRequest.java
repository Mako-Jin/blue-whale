package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.io.Serializable;

/**
 * 模板保存请求（M3）。
 *
 * @author BlueWhale
 */
@Data
public class DocTemplateSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 归属空间ID，0 或空表示租户级模板 */
    private Long spaceId;

    /** 模板名称 */
    @NotBlank(message = "{validation.doc.template.name-required}")
    @Size(max = 100, message = "{validation.doc.template.name-size}")
    private String name;

    /** 模板说明 */
    @Size(max = 500, message = "{validation.doc.template.description-size}")
    private String description;

    /** 节点类型：PAGE / FOLDER */
    private String pageType;

    /** 图标 */
    @Size(max = 64, message = "{validation.doc.template.icon-size}")
    private String icon;

    /** 模板正文 JSON */
    private JsonNode contentJson;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 排序 */
    private Integer sortNo;
}
