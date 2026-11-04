package com.bluewhale.crm.customer.domain.model;

import lombok.Data;

/**
 * 客户联系人。
 *
 * <p>每客户最多一个主联系人；电话 / 邮箱保存原值，输出经脱敏工具处理。</p>
 *
 * @author BlueWhale
 */
@Data
public class Contact {

    /** 主键 */
    private Long id;

    /** 所属客户 */
    private Long customerId;

    /** 姓名 */
    private String name;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 微信号 */
    private String wechat;

    /** 职务 */
    private String title;

    /** 决策角色：DECIDER / INFLUENCER / USER / GATEKEEPER */
    private String decisionRole;

    /** 是否关键决策人 */
    private Boolean decisionMaker;

    /** 是否主联系人 */
    private Boolean primary;

    /** 状态：ACTIVE / INACTIVE */
    private String status;

    /** 归一化电话 */
    private String normalizedPhone;

    /** 归一化邮箱 */
    private String normalizedEmail;

    /** 版本号 */
    private Integer version;

    /**
     * 是否启用。
     *
     * @return 启用返回 true
     */
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
