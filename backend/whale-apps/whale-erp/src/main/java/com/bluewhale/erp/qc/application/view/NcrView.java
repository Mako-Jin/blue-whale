package com.bluewhale.erp.qc.application.view;

import com.bluewhale.erp.qc.domain.model.Nonconformity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 不合格品单视图。
 *
 * @author BlueWhale
 */
@Data
public class NcrView {

    /** 主键 */
    private Long id;

    /** 不合格品单号 */
    private String ncrNo;

    /** 来源类型 */
    private String sourceType;

    /** 来源单据号 */
    private String sourceNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料名称 */
    private String itemName;

    /** 批次号 */
    private String lotNo;

    /** 不合格数量 */
    private BigDecimal quantity;

    /** 缺陷描述 */
    private String defectDesc;

    /** 处置方式 */
    private String disposition;

    /** 状态 */
    private String status;

    /** 质量成本金额 */
    private BigDecimal costAmount;

    /** 处置日期 */
    private LocalDate disposeDate;

    /** 关闭日期 */
    private LocalDate closeDate;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param nonconformity 不合格品单
     * @return 视图
     */
    public static NcrView of(Nonconformity nonconformity) {
        NcrView view = new NcrView();
        view.setId(nonconformity.getId());
        view.setNcrNo(nonconformity.getNcrNo());
        view.setSourceType(nonconformity.getSourceType());
        view.setSourceNo(nonconformity.getSourceNo());
        view.setItemId(nonconformity.getItemId());
        view.setItemName(nonconformity.getItemName());
        view.setLotNo(nonconformity.getLotNo());
        view.setQuantity(nonconformity.getQuantity());
        view.setDefectDesc(nonconformity.getDefectDesc());
        view.setDisposition(nonconformity.getDisposition());
        view.setStatus(nonconformity.getStatus());
        view.setCostAmount(nonconformity.getCostAmount());
        view.setDisposeDate(nonconformity.getDisposeDate());
        view.setCloseDate(nonconformity.getCloseDate());
        view.setRemark(nonconformity.getRemark());
        return view;
    }
}
