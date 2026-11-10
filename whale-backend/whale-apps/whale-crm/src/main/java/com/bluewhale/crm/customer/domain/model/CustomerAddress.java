package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

/**
 * 客户地址。
 *
 * @author BlueWhale
 */
@Data
public class CustomerAddress {

    /** 主键 */
    private Long id;

    /** 所属客户 */
    private Long customerId;

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
    private String detail;

    /** 是否默认收货地址 */
    private Boolean defaultShipping;

    /** 是否默认账单地址 */
    private Boolean defaultBilling;

    /** 状态：ACTIVE / INACTIVE */
    private String status;

    /**
     * 拼接完整地址。
     *
     * @return 省市区 + 详细地址
     */
    public String fullAddress() {
        StringBuilder builder = new StringBuilder();
        append(builder, province);
        append(builder, city);
        append(builder, district);
        append(builder, detail);
        return builder.toString();
    }

    /**
     * 追加非空片段。
     *
     * @param builder 构建器
     * @param part 片段
     */
    private void append(StringBuilder builder, String part) {
        if (part != null && !part.isBlank()) {
            builder.append(part.trim());
        }
    }
}
