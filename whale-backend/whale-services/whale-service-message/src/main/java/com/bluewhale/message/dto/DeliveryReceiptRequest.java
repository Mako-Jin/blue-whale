package com.bluewhale.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 渠道回执上报请求（服务间内网）。
 *
 * <p>渠道供应商或部署侧的回执网关在收到送达/阅读/退信事件后调用；
 * 同一批次允许混合渠道，逐条按 {@link DeliveryReceiptItem#getChannel()} 处理。</p>
 *
 * @author BlueWhale
 */
@Data
public class DeliveryReceiptRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 租户ID；内网调用必须显式传入（缺省不再从上下文兜底） */
    @NotNull(message = "{validation.message.receipt.tenant-id-required}")
    private Long tenantId;

    /** 回执明细（单次最多 200 条） */
    @NotEmpty(message = "{validation.message.receipt.receipts-required}")
    @Size(max = 200, message = "{validation.message.receipt.receipts-size}")
    private List<@Valid DeliveryReceiptItem> receipts;
}
