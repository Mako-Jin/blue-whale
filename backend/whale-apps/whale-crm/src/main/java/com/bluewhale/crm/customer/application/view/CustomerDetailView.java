package com.bluewhale.crm.customer.application.view;

import lombok.Data;

import java.util.List;

/**
 * 客户详情（档案 + 联系人 + 地址 + 画像）。
 *
 * @author BlueWhale
 */
@Data
public class CustomerDetailView {

    /** 客户档案 */
    private CustomerView customer;

    /** 联系人 */
    private List<ContactView> contacts;

    /** 地址 */
    private List<CustomerAddressView> addresses;

    /** 画像 */
    private CustomerProfileView profile;
}
