package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 质量检验单聚合。
 *
 * <p>不变量：检验类型只能为 IQC / IPQC / FQC；送检数量为正；至少一条检验项；
 * 检验项未全部录入结论不可判定；判定后不可重复判定；判定为 FAILED 时由应用层生成 NCR。</p>
 *
 * @author BlueWhale
 */
@Data
public class InspectionOrder {

    /** 主键 */
    private Long id;

    /** 检验单号 */
    private String orderNo;

    /** 检验类型：IQC / IPQC / FQC */
    private String inspectionType;

    /** 来源类型：PURCHASE_RECEIPT / PRODUCTION_ORDER / MANUAL */
    private String sourceType;

    /** 来源单据号 */
    private String sourceNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料编码快照 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 批次号 */
    private String lotNo;

    /** 送检数量 */
    private BigDecimal quantity;

    /** 已检验数量 */
    private BigDecimal inspectedQuantity;

    /** 供应商编码（IQC） */
    private String supplierCode;

    /** 状态：DRAFT / INSPECTING / PASSED / FAILED / CANCELLED */
    private String status;

    /** 检验员 */
    private String inspector;

    /** 检验日期 */
    private LocalDate inspectDate;

    /** 量具主键 */
    private Long toolId;

    /** 量具编号快照 */
    private String toolNo;

    /** 不合格品单主键 */
    private Long ncrId;

    /** 备注 */
    private String remark;

    /** 检验项 */
    private List<InspectionItem> items;

    /**
     * 校验建单数据。
     */
    public void validateForCreate() {
        if (!QcStatus.TYPE_IQC.equals(inspectionType)
                && !QcStatus.TYPE_IPQC.equals(inspectionType)
                && !QcStatus.TYPE_FQC.equals(inspectionType)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "检验类型只能为 IQC、IPQC 或 FQC");
        }
        if (sourceNo == null || sourceNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "来源单据号不能为空");
        }
        if (itemCode == null || itemCode.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.ITEM_INVALID, "送检物料不能为空");
        }
        Quantities.requirePositive(quantity, "送检数量");
        if (inspectDate == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "检验日期不能为空");
        }
        if (items == null || items.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "检验单至少需要一条检验项");
        }
        for (InspectionItem item : items) {
            item.validate();
        }
        if (inspectedQuantity == null) {
            inspectedQuantity = BigDecimal.ZERO;
        }
    }

    /**
     * 录入检验项实测值与结论，首个录入后进入检验中状态。
     *
     * @param source 录入来源（按行号匹配）
     */
    public void record(InspectionItem source) {
        if (QcStatus.INSPECTION_PASSED.equals(status) || QcStatus.INSPECTION_FAILED.equals(status)
                || QcStatus.INSPECTION_CANCELLED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_STATE_CONFLICT, "检验单已判定或已取消，不能录入检验项");
        }
        InspectionItem target = items.stream()
                .filter(item -> item.getLineNo().equals(source.getLineNo()))
                .findFirst()
                .orElseThrow(() -> ErpErrorCode.exception(ErpErrorCode.QC_INVALID,
                        "检验单不存在检验项行 " + source.getLineNo()));
        target.record(source.getActualValue(), source.getResult(), source.getRemark());
        status = QcStatus.INSPECTION_INSPECTING;
    }

    /**
     * 判定整单：全部检验项已有结论才允许，任一 FAIL 即整单不合格。
     *
     * @return 判定为合格返回 true，否则 false
     */
    public boolean judge() {
        if (!QcStatus.INSPECTION_DRAFT.equals(status) && !QcStatus.INSPECTION_INSPECTING.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_STATE_CONFLICT, "检验单当前状态不允许判定");
        }
        if (items == null || items.isEmpty()) {
            throw ErpErrorCode.exception(ErpErrorCode.INSPECTION_ITEM_INCOMPLETE);
        }
        for (InspectionItem item : items) {
            if (!item.completed()) {
                throw ErpErrorCode.exception(ErpErrorCode.INSPECTION_ITEM_INCOMPLETE,
                        "检验项「" + item.getItemName() + "」尚未录入结论");
            }
        }
        boolean passed = items.stream().allMatch(InspectionItem::passed);
        status = passed ? QcStatus.INSPECTION_PASSED : QcStatus.INSPECTION_FAILED;
        inspectedQuantity = quantity;
        return passed;
    }

    /**
     * 回写不合格品单主键。
     *
     * @param nonconformityId 不合格品单主键
     */
    public void markNonconformity(Long nonconformityId) {
        if (!QcStatus.INSPECTION_FAILED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_STATE_CONFLICT, "仅不合格检验单可以关联不合格品单");
        }
        if (ncrId != null) {
            throw ErpErrorCode.exception(ErpErrorCode.NCR_ALREADY_EXISTS, "检验单已关联不合格品单");
        }
        ncrId = nonconformityId;
    }

    /**
     * 取消检验单：仅未判定的检验单可取消。
     */
    public void cancel() {
        if (QcStatus.INSPECTION_PASSED.equals(status) || QcStatus.INSPECTION_FAILED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_STATE_CONFLICT, "已判定的检验单不能取消");
        }
        status = QcStatus.INSPECTION_CANCELLED;
    }
}
