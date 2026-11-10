package com.bluewhale.erp.scm.domain.port;

import lombok.Data;

/**
 * 物料只读快照：供供应链单据引用，避免跨模块访问物料表。
 *
 * @author BlueWhale
 */
@Data
public class ItemSnapshot {

    /** 主键 */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 状态 */
    private String status;
}
