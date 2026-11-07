package com.bluewhale.crm.marketing.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建渠道伙伴命令。
 *
 * @author BlueWhale
 */
@Data
public class PartnerCreateCommand {

    /** 伙伴编码 */
    @NotBlank(message = "伙伴编码不能为空")
    private String partnerCode;

    /** 伙伴名称 */
    @NotBlank(message = "伙伴名称不能为空")
    private String partnerName;

    /** 伙伴类型：AGENT / DEALER / OTHER */
    private String partnerType;

    /** 伙伴等级 */
    private String level;

    /** 所属区域 */
    private String region;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人电话 */
    private String contactPhone;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
