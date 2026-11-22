package com.bluewhale.crm.customer.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户地址新建 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class AddressSaveCommand {

    /** 收件人 */
    private String contactName;

    /** 联系电话 */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String district;

    /** 详细地址 */
    @NotBlank(message = "{validation.crm.address.detail-required}")
    private String detail;

    /** 是否默认收货地址 */
    private Boolean defaultShipping;

    /** 是否默认账单地址 */
    private Boolean defaultBilling;

    /** 状态 */
    private String status;
}
