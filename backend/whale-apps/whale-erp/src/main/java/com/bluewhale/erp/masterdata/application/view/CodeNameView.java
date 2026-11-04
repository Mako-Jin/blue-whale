package com.bluewhale.erp.masterdata.application.view;

import lombok.Data;

/**
 * 编码-名称视图（下拉选项）。
 *
 * @author BlueWhale
 */
@Data
public class CodeNameView {

    /** 主键 */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 附加说明（如精度、税率、是否本位币） */
    private String extra;
}
