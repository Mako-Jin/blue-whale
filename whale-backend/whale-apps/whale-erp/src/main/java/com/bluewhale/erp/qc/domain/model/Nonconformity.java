package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.Amounts;
import com.bluewhale.erp.common.ErpErrorCode;
import com.bluewhale.erp.common.Quantities;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 不合格品处理单聚合。
 *
 * <p>不变量：缺陷描述必填；处置方式必填且只能为返工/让步/报废/退货；
 * 只有待处置单可处置；只有已处置单可关闭；处置产生费用时登记质量成本。</p>
 *
 * @author BlueWhale
 */
@Data
public class Nonconformity {

    /** 主键 */
    private Long id;

    /** 不合格品单号 */
    private String ncrNo;

    /** 来源类型：INSPECTION / MANUAL */
    private String sourceType;

    /** 来源单据号 */
    private String sourceNo;

    /** 物料主键 */
    private Long itemId;

    /** 物料名称快照 */
    private String itemName;

    /** 批次号 */
    private String lotNo;

    /** 不合格数量 */
    private BigDecimal quantity;

    /** 缺陷描述 */
    private String defectDesc;

    /** 处置方式：REWORK / CONCESSION / SCRAP / RETURN */
    private String disposition;

    /** 状态：OPEN / DISPOSED / CLOSED */
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
     * 校验建单数据。
     */
    public void validateForCreate() {
        if (sourceNo == null || sourceNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "不合格品来源单据号不能为空");
        }
        if (itemName == null || itemName.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "不合格品物料不能为空");
        }
        if (defectDesc == null || defectDesc.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "缺陷描述不能为空");
        }
        quantity = Quantities.nonNegative(quantity, "不合格数量");
        costAmount = Amounts.nonNegative(costAmount, "质量成本");
        if (status == null) {
            status = QcStatus.NCR_OPEN;
        }
    }

    /**
     * 处置不合格品：登记处置方式与质量成本。
     *
     * @param targetDisposition 处置方式
     * @param date 处置日期
     * @param cost 质量成本金额
     * @param note 备注
     */
    public void dispose(String targetDisposition, LocalDate date, BigDecimal cost, String note) {
        if (!QcStatus.NCR_OPEN.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_STATE_CONFLICT, "只有待处置的不合格品单可以处置");
        }
        if (!isValidDisposition(targetDisposition)) {
            throw ErpErrorCode.exception(ErpErrorCode.NCR_DISPOSITION_REQUIRED);
        }
        if (date == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "处置日期不能为空");
        }
        disposition = targetDisposition;
        disposeDate = date;
        costAmount = Amounts.nonNegative(cost, "质量成本");
        if (note != null) {
            remark = note;
        }
        status = QcStatus.NCR_DISPOSED;
    }

    /**
     * 关闭不合格品单。
     *
     * @param date 关闭日期
     * @param note 备注
     */
    public void close(LocalDate date, String note) {
        if (!QcStatus.NCR_DISPOSED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_STATE_CONFLICT, "只有已处置的不合格品单可以关闭");
        }
        if (date == null) {
            throw ErpErrorCode.exception(ErpErrorCode.DOC_DATE_INVALID, "关闭日期不能为空");
        }
        closeDate = date;
        if (note != null) {
            remark = note;
        }
        status = QcStatus.NCR_CLOSED;
    }

    /**
     * 处置方式对应的质量成本类型。
     *
     * @return 报废返回 SCRAP，其余处置按返工成本归集
     */
    public String costType() {
        return QcStatus.DISPOSITION_SCRAP.equals(disposition) ? QcStatus.COST_SCRAP : QcStatus.COST_REWORK;
    }

    /**
     * 判断是否需要登记质量成本。
     *
     * @return 处置产生费用返回 true
     */
    public boolean costRequired() {
        return costAmount != null && costAmount.signum() > 0;
    }

    /**
     * 校验处置方式取值。
     *
     * @param value 处置方式
     * @return 合法返回 true
     */
    private boolean isValidDisposition(String value) {
        return QcStatus.DISPOSITION_REWORK.equals(value)
                || QcStatus.DISPOSITION_CONCESSION.equals(value)
                || QcStatus.DISPOSITION_SCRAP.equals(value)
                || QcStatus.DISPOSITION_RETURN.equals(value);
    }
}
