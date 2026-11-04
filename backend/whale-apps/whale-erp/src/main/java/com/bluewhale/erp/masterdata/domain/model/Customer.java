package com.bluewhale.erp.masterdata.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户业务主数据。
 *
 * <p>只承接交易、开票与应收所需的字段；客户关系、商机与跟进归 CRM。</p>
 *
 * @author BlueWhale
 */
@Data
public class Customer {

    /** 主键 */
    private Long id;

    /** 客户编码 */
    private String code;

    /** 客户名称 */
    private String name;

    /** 客户简称 */
    private String shortName;

    /** 纳税人识别号 */
    private String taxNo;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 信用额度（0 表示不限制） */
    private BigDecimal creditLimit;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 调整信用额度。
     *
     * @param limit 新额度，必须大于等于 0
     */
    public void changeCreditLimit(BigDecimal limit) {
        if (limit == null || limit.signum() < 0) {
            throw ErpErrorCode.exception(ErpErrorCode.AMOUNT_INVALID, "信用额度不能为负数");
        }
        this.creditLimit = limit;
    }

    /**
     * 是否启用。
     *
     * @return 启用返回 true
     */
    public boolean enabled() {
        return MasterDataStatus.ENABLED.equals(status);
    }
}
