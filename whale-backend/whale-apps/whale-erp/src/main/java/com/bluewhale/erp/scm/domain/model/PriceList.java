package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 价目表聚合根：按「价格类型 + 往来方 + 物料」维护唯一价格。
 *
 * <p>单价必须大于 0；有效期允许为空（表示不限），非空时止日不得早于起日；
 * 启停状态在 {@code ENABLED/DISABLED} 间切换。</p>
 *
 * @author BlueWhale
 */
@Data
public class PriceList {

    /** 主键 */
    private Long id;

    /** 价格类型 */
    private PriceType priceType;

    /** 往来方主键（客户或供应商） */
    private Long partnerId;

    /** 往来方名称快照 */
    private String partnerName;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 单价 */
    private BigDecimal price;

    /** 币种 */
    private String currency;

    /** 有效期起（空表示不限） */
    private LocalDate validFrom;

    /** 有效期止（空表示不限） */
    private LocalDate validTo;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 校验单价与有效期合法性。
     */
    public void validate() {
        if (price == null || price.signum() <= 0) {
            throw ErpErrorCode.exception(ErpErrorCode.PRICE_INVALID, "单价必须大于 0");
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "有效期止不能早于有效期起");
        }
    }

    /**
     * 切换启停状态。
     */
    public void toggle() {
        this.status = ScmStatus.ENABLED.equals(status) ? ScmStatus.DISABLED : ScmStatus.ENABLED;
    }

    /**
     * 判断指定日期是否可命中该价目。
     *
     * @param date 业务日期
     * @return 启用且有效期覆盖该日返回 true
     */
    public boolean matches(LocalDate date) {
        if (!ScmStatus.ENABLED.equals(status) || date == null) {
            return false;
        }
        if (validFrom != null && date.isBefore(validFrom)) {
            return false;
        }
        return validTo == null || !date.isAfter(validTo);
    }
}
