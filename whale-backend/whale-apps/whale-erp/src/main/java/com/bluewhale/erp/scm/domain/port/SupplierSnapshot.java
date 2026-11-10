package com.bluewhale.erp.scm.domain.port;

import lombok.Data;

/**
 * 供应商只读快照。
 *
 * @author BlueWhale
 */
@Data
public class SupplierSnapshot {

    /** 主键 */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 状态 */
    private String status;
}
