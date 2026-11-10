package com.bluewhale.erp.masterdata.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会计期间。
 *
 * <p>期间编码为 {@code yyyyMM}。打开状态才允许记账；关账后只能通过红冲调整，
 * 反结账需单独权限并留审计。</p>
 *
 * @author BlueWhale
 */
@Data
public class AccountingPeriod {

    /** 主键 */
    private Long id;

    /** 期间编码 yyyyMM */
    private String periodCode;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 状态：OPEN / CLOSED */
    private String status;

    /** 关账时间 */
    private LocalDateTime closedAt;

    /** 关账人 */
    private Long closedBy;

    /** 备注 */
    private String remark;

    /**
     * 是否可记账。
     *
     * @return 打开状态返回 true
     */
    public boolean open() {
        return MasterDataStatus.PERIOD_OPEN.equals(status);
    }

    /**
     * 判断日期是否落在本期间。
     *
     * @param date 业务日期
     * @return 落在期间内返回 true
     */
    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /**
     * 关账。
     *
     * @param actorId 操作人 ID
     * @param now 关账时间
     */
    public void close(Long actorId, LocalDateTime now) {
        if (!open()) {
            throw ErpErrorCode.exception(ErpErrorCode.PERIOD_STATE_CONFLICT, "会计期间已关账");
        }
        status = MasterDataStatus.PERIOD_CLOSED;
        closedAt = now;
        closedBy = actorId;
    }

    /**
     * 反结账（受控操作，控制器层校验权限并记录审计）。
     */
    public void reopen() {
        if (open()) {
            throw ErpErrorCode.exception(ErpErrorCode.PERIOD_STATE_CONFLICT, "会计期间未关账，无需反结账");
        }
        status = MasterDataStatus.PERIOD_OPEN;
        closedAt = null;
        closedBy = null;
    }
}
