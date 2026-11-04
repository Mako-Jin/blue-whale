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
    @NotNull(message = "所属目录不能为空")
    private Long folderId;

    /** 文档编号，为空时自动生成 */
    @Size(max = 64, message = "文档编号不能超过 64 个字符")
    private String docNo;

    /** 文档标题 */
    @NotBlank(message = "文档标题不能为空")
    @Size(max = 255, message = "文档标题不能超过 255 个字符")
    private String title;

    /** 文档类型：DRAWING/DOC/SPEC/REPORT */
    @NotBlank(message = "文档类型不能为空")
    @Size(max = 32, message = "文档类型不能超过 32 个字符")
    private String docType;

    /** 关联零件号 */
    @Size(max = 64, message = "零件号不能超过 64 个字符")
    private String partNo;

    /** 说明 */
    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String description;

    /** 标签，逗号分隔 */
    @Size(max = 255, message = "标签不能超过 255 个字符")
    private String tags;
}
