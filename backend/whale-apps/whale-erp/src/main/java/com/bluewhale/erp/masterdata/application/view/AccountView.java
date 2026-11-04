package com.bluewhale.erp.masterdata.application.view;

import lombok.Data;

/**
 * 科目视图。
 *
 * @author BlueWhale
 */
@Data
public class AccountView {

    /** 主键 */
    private Long id;

    /** 科目编码 */
    private String code;

    /** 科目名称 */
    private String name;

    /** 科目类型 */
    private String accountType;

    /** 余额方向 */
    private String direction;

    /** 上级科目编码 */
    private String parentCode;

    /** 是否末级科目 */
    private Boolean leaf;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
