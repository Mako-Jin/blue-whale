package com.bluewhale.erp.scm.application.view;

import com.bluewhale.erp.scm.domain.model.PriceList;
import com.bluewhale.erp.scm.domain.model.PriceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 价目表视图。
 *
 * @author BlueWhale
 */
@Data
public class PriceListView {

    /** 主键 */
    private Long id;

    /** 价格类型 */
    private PriceType priceType;

    /** 往来方主键 */
    private Long partnerId;

    /** 往来方名称 */
    private String partnerName;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 计量单位 */
    private String uom;

    /** 单价 */
    private BigDecimal price;

    /** 币种 */
    private String currency;

    /** 有效期起 */
    private LocalDate validFrom;

    /** 有效期止 */
    private LocalDate validTo;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param priceList 价目
     * @return 视图
     */
    public static PriceListView of(PriceList priceList) {
        PriceListView view = new PriceListView();
        view.setId(priceList.getId());
        view.setPriceType(priceList.getPriceType());
        view.setPartnerId(priceList.getPartnerId());
        view.setPartnerName(priceList.getPartnerName());
        view.setItemId(priceList.getItemId());
        view.setItemCode(priceList.getItemCode());
        view.setItemName(priceList.getItemName());
        view.setUom(priceList.getUom());
        view.setPrice(priceList.getPrice());
        view.setCurrency(priceList.getCurrency());
        view.setValidFrom(priceList.getValidFrom());
        view.setValidTo(priceList.getValidTo());
        view.setStatus(priceList.getStatus());
        view.setRemark(priceList.getRemark());
        return view;
    }
}
