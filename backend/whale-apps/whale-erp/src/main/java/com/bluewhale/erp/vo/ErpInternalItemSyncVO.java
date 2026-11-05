package com.bluewhale.erp.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * ERP 物料内网同步结果
 *
 * @author BlueWhale
 */
@Data
public class ErpInternalItemSyncVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 物料ID */
    private Long id;

    /** 物料编码 */
    private String code;

    /** 是否新建 */
    private boolean created;
}
