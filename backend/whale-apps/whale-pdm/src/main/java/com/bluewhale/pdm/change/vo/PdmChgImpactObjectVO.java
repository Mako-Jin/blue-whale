package com.bluewhale.pdm.change.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 变更影响对象视图
 *
 * @author BlueWhale
 */
@Data
public class PdmChgImpactObjectVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对象类型：DOCUMENT/BOM/PART */
    private String objectType;

    /** 对象ID */
    private Long objectId;

    /** 对象编码 */
    private String objectCode;

    /** 对象名称 */
    private String objectName;
}
