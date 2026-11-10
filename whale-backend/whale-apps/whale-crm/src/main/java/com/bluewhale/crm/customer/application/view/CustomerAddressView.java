package com.bluewhale.crm.customer.application.view;

import com.bluewhale.crm.common.CrmMasking;
import com.bluewhale.crm.customer.domain.model.CustomerAddress;
import lombok.Data;

/**
 * 客户地址视图。
 *
 * @author BlueWhale
 */
@Data
public class CustomerAddressView {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 收件人 */
    private String contactName;

    /** 电话（按权限脱敏） */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区县 */
    private String district;

    /** 详细地址 */
    private String detail;

    /** 完整地址 */
    private String fullAddress;

    /** 是否默认收货 */
    private Boolean defaultShipping;

    /** 是否默认账单 */
    private Boolean defaultBilling;

    /** 状态 */
    private String status;

    /**
     * 由领域模型构建视图。
     *
     * @param address 地址
     * @param allowPlain 是否允许查看敏感明文
     * @return 地址视图
     */
    public static CustomerAddressView from(CustomerAddress address, boolean allowPlain) {
        CustomerAddressView view = new CustomerAddressView();
        view.setId(address.getId());
        view.setCustomerId(address.getCustomerId());
        view.setContactName(address.getContactName());
        view.setPhone(CrmMasking.phone(address.getPhone(), allowPlain));
        view.setProvince(address.getProvince());
        view.setCity(address.getCity());
        view.setDistrict(address.getDistrict());
        view.setDetail(address.getDetail());
        view.setFullAddress(address.fullAddress());
        view.setDefaultShipping(address.getDefaultShipping());
        view.setDefaultBilling(address.getDefaultBilling());
        view.setStatus(address.getStatus());
        return view;
    }
}
