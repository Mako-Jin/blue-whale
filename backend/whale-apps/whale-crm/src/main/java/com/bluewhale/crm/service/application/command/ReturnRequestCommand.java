package com.bluewhale.crm.service.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 退换货申请保存命令。
 *
 * @author BlueWhale
 */
@Data
public class ReturnRequestCommand {

    /** 申请类型：RETURN / EXCHANGE（本期仅 RETURN 可执行） */
    private String requestType;

    /** 商务订单 ID */
    @NotNull
    private Long orderId;

    /** 退货原因 */
    private String reason;

    /** 申请行 */
    @Valid
    @NotEmpty
    private List<ReturnRequestLineCommand> lines = new ArrayList<>();
}
