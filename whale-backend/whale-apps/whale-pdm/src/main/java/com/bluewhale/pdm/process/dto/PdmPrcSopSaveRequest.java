package com.bluewhale.pdm.process.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 工序 SOP 保存请求。
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcSopSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工序ID */
    @NotNull(message = "工序不能为空")
    private Long stepId;

    /** 文档ID */
    @NotNull(message = "SOP 文档不能为空")
    private Long documentId;

    /** 文档版本号 */
    @NotBlank(message = "SOP 文档版本不能为空")
    @Size(max = 32, message = "文档版本号长度不能超过 32")
    private String documentVersionNo;
}
