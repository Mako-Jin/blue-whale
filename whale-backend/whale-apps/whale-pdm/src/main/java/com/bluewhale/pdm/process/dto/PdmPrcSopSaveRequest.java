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
    @NotNull(message = "{validation.pdm.pdm-prc-sop.step-id-required}")
    private Long stepId;

    /** 文档ID */
    @NotNull(message = "{validation.pdm.pdm-prc-sop.document-id-required}")
    private Long documentId;

    /** 文档版本号 */
    @NotBlank(message = "{validation.pdm.pdm-prc-sop.document-version-no-required}")
    @Size(max = 32, message = "{validation.pdm.pdm-prc-sop.document-version-no-size}")
    private String documentVersionNo;
}
