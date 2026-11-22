package com.bluewhale.pdm.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 图文档保存请求
 *
 * @author BlueWhale
 */
@Data
public class PdmDocDocumentSaveRequest {

    /** 所属目录 */
    @NotNull(message = "{validation.pdm.pdm-doc-document.folder-id-required}")
    private Long folderId;

    /** 文档编号，为空时自动生成 */
    @Size(max = 64, message = "{validation.pdm.pdm-doc-document.doc-no-size}")
    private String docNo;

    /** 文档标题 */
    @NotBlank(message = "{validation.pdm.pdm-doc-document.title-required}")
    @Size(max = 255, message = "{validation.pdm.pdm-doc-document.title-size}")
    private String title;

    /** 文档类型：DRAWING/DOC/SPEC/REPORT */
    @NotBlank(message = "{validation.pdm.pdm-doc-document.doc-type-required}")
    @Size(max = 32, message = "{validation.pdm.pdm-doc-document.doc-type-size}")
    private String docType;

    /** 关联零件号 */
    @Size(max = 64, message = "{validation.pdm.pdm-doc-document.part-no-size}")
    private String partNo;

    /** 说明 */
    @Size(max = 500, message = "{validation.pdm.pdm-doc-document.description-size}")
    private String description;

    /** 标签，逗号分隔 */
    @Size(max = 255, message = "{validation.pdm.pdm-doc-document.tags-size}")
    private String tags;
}
