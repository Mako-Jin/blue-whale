package com.bluewhale.crm.sales.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 线索新建 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class LeadSaveCommand {

    /** 线索名称 */
    @NotBlank(message = "{validation.crm.lead.name-required}")
    private String name;

    /** 来源 */
    private String source;

    /** 行业 */
    private String industry;

    /** 地区 */
    private String region;

    /** 联系人姓名 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 负责人（仅全量数据范围可指定他人） */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 备注（不记录敏感信息） */
    private String remark;

    /** 疑似重复确认：true 表示已确认继续保存 */
    private Boolean confirmedDuplicate;
}
