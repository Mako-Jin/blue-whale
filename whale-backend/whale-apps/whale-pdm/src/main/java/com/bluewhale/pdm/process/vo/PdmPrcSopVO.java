package com.bluewhale.pdm.process.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 工序 SOP 视图。
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcSopVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SOP 记录ID */
    private Long id;

    /** 工序ID */
    private Long stepId;

    /** 文档ID */
    private Long documentId;

    /** 文档版本号 */
    private String documentVersionNo;

    /** 文档标题 */
    private String documentTitle;
}
