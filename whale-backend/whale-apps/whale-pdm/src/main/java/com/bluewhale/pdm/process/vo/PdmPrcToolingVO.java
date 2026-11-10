package com.bluewhale.pdm.process.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 工艺工装视图。
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcToolingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工装ID */
    private Long id;

    /** 工序ID */
    private Long stepId;

    /** 类型 */
    private String toolingType;

    /** 工装编码 */
    private String toolingCode;

    /** 规格 */
    private String spec;

    /** 数量 */
    private Integer quantity;

    /** 备注 */
    private String remark;
}
