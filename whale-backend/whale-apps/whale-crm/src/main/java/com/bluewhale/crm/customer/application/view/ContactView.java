package com.bluewhale.crm.customer.application.view;

import com.bluewhale.crm.common.CrmMasking;
import com.bluewhale.crm.customer.domain.model.Contact;
import lombok.Data;

/**
 * 联系人视图。
 *
 * @author BlueWhale
 */
@Data
public class ContactView {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 姓名 */
    private String name;

    /** 电话（按权限脱敏） */
    private String phone;

    /** 邮箱（按权限脱敏） */
    private String email;

    /** 微信（按权限脱敏） */
    private String wechat;

    /** 职务 */
    private String title;

    /** 决策角色 */
    private String decisionRole;

    /** 是否关键决策人 */
    private Boolean decisionMaker;

    /** 是否主联系人 */
    private Boolean primary;

    /** 状态 */
    private String status;

    /**
     * 由领域模型构建视图。
     *
     * @param contact 联系人
     * @param allowPlain 是否允许查看敏感明文
     * @return 联系人视图
     */
    public static ContactView from(Contact contact, boolean allowPlain) {
        ContactView view = new ContactView();
        view.setId(contact.getId());
        view.setCustomerId(contact.getCustomerId());
        view.setName(contact.getName());
        view.setPhone(CrmMasking.phone(contact.getPhone(), allowPlain));
        view.setEmail(CrmMasking.email(contact.getEmail(), allowPlain));
        view.setWechat(CrmMasking.wechat(contact.getWechat(), allowPlain));
        view.setTitle(contact.getTitle());
        view.setDecisionRole(contact.getDecisionRole());
        view.setDecisionMaker(contact.getDecisionMaker());
        view.setPrimary(contact.getPrimary());
        view.setStatus(contact.getStatus());
        return view;
    }
}
