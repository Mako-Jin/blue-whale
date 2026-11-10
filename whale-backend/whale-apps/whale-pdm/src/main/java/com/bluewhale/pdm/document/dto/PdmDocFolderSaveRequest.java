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
    @NotBlank(message = "目录名称不能为空")
    @Size(max = 128, message = "目录名称不能超过 128 个字符")
    private String name;

    /** 目录类型 */
    @Size(max = 32, message = "目录类型不能超过 32 个字符")
    private String folderType;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;
}
