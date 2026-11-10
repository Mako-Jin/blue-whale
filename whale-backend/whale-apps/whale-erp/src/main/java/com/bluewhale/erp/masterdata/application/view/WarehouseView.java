package com.bluewhale.erp.masterdata.application.view;

import lombok.Data;

/**
 * 仓库视图。
 *
 * @author BlueWhale
 */
@Data
public class WarehouseView {

    /** 主键 */
    private Long id;

    /** 仓库编码 */
    private String code;

    /** 仓库名称 */
    private String name;

    /** 负责人 */
    private String manager;

    /** 地址 */
    private String address;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
