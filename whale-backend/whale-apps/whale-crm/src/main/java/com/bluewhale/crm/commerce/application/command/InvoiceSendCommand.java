package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登记发票寄送命令。
 *
 * @author BlueWhale
 */
@Data
public class InvoiceSendCommand {

    /** 快递公司 */
    @NotBlank
    private String courier;

    /** 运单号 */
    @NotBlank
    private String trackingNo;
}
