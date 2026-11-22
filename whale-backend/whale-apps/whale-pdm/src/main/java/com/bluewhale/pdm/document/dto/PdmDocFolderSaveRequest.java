package com.bluewhale.pdm.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 图文档目录保存请求
 *
 * @author BlueWhale
 */
@Data
public class PdmDocFolderSaveRequest {

    /** 上级目录ID，为空按根目录处理 */
    private Long parentId;

    /** 目录名称 */
    @NotBlank(message = "{validation.pdm.pdm-doc-folder.name-required}")
    @Size(max = 128, message = "{validation.pdm.pdm-doc-folder.name-size}")
    private String name;

    /** 目录类型 */
    @Size(max = 32, message = "{validation.pdm.pdm-doc-folder.folder-type-size}")
    private String folderType;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "{validation.pdm.pdm-doc-folder.remark-size}")
    private String remark;
}
