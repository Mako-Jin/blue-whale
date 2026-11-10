package com.bluewhale.contract.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客户合并合同引用重写请求（内网）。
 *
 * @author BlueWhale
 */
@Data
public class ContractCustomerRewriteRequest {

    /** 源客户 ID */
    @NotNull(message = "源客户 ID 不能为空")
    private Long sourceId;

    /** 目标客户 ID */
    @NotNull(message = "目标客户 ID 不能为空")
    private Long targetId;
}
