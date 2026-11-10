package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 零件使用处（where-used）视图
 *
 * @author BlueWhale
 */
@Data
public class PdmPartWhereUsedVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** BOM 头ID */
    private Long bomId;

    /** BOM 编号 */
    private String bomNo;

    /** 版本号 */
    private String versionNo;

    /** 视图 */
    private String view;

    /** 产品编码 */
    private String productCode;

    /** BOM 状态 */
    private String status;

    /** 父件编码 */
    private String parentPartNo;
}
