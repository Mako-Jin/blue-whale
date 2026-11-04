package com.bluewhale.mes.trace.vo;

import lombok.Data;

/**
 * 扫码校验结果视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesScanValidateVO {

    /** 是否通过 */
    private Boolean passed;

    /** 是否被阻断 */
    private Boolean blocked;

    /** 是否重复扫码（幂等命中） */
    private Boolean duplicate;

    /** 提示信息 */
    private String message;

    /** 条码值 */
    private String barcodeValue;

    /** 批次号 */
    private String batchNo;

    /** 物料编码 */
    private String itemCode;

    /** 命中的规则编码（无规则命中时为空） */
    private String ruleCode;
}
