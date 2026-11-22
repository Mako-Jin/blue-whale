package com.bluewhale.pay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 对账差异处置请求。
 *
 * <p>{@code action} 取 {@code CONFIRM} / {@code COMPENSATE} / {@code SUSPEND}；
 * {@code COMPENSATE} 仅对「渠道 PAID、本地 PAYING」的状态不一致差异放行。</p>
 *
 * @author BlueWhale
 */
@Data
public class PayReconcileHandleRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 处置动作：CONFIRM / COMPENSATE / SUSPEND */
    @NotBlank(message = "{validation.pay.pay-reconcile-handle.action-required}")
    private String action;

    /** 处置备注 */
    @Size(max = 255, message = "{validation.pay.pay-reconcile-handle.remark-size}")
    private String remark;
}
