package com.bluewhale.crm.customer.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 联系人新建 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class ContactSaveCommand {

    /** 姓名 */
    @NotBlank(message = "{validation.crm.contact.name-required}")
    private String name;

    /** 电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 微信 */
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

    /** 是否已确认电话 / 邮箱疑似重复 */
    private Boolean confirmedDuplicate;
}
