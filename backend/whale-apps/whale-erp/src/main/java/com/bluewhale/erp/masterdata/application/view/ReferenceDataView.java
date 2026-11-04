package com.bluewhale.erp.masterdata.application.view;

import lombok.Data;

import java.util.List;

/**
 * 参考数据视图（计量单位 / 币种 / 税码）。
 *
 * @author BlueWhale
 */
@Data
public class ReferenceDataView {

    /** 计量单位 */
    private List<CodeNameView> uoms;

    /** 币种 */
    private List<CodeNameView> currencies;

    /** 税码 */
    private List<CodeNameView> taxCodes;
}
