package com.bluewhale.pdm.collab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 在线预览创建请求
 *
 * @author BlueWhale
 */
@Data
public class PdmDocPreviewRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预览类型：2D/3D */
    @NotBlank(message = "{validation.pdm.pdm-doc-preview.preview-type-required}")
    private String previewType;
}
