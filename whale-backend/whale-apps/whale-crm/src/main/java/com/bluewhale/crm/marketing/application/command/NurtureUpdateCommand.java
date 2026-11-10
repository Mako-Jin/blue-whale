package com.bluewhale.crm.marketing.application.command;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 编辑客户培育任务命令。
 *
 * @author BlueWhale
 */
@Data
public class NurtureUpdateCommand {

    /** 关联线索 ID */
    private Long leadId;

    /** 内容文档 ID */
    private Long contentDocId;

    /** 内容文档版本 */
    private Integer contentDocVersion;

    /** 触达渠道 */
    private String channel;

    /** 计划触达日期 */
    private LocalDate planDate;

    /** 执行人 ID */
    private Long assigneeId;

    /** 执行人姓名 */
    private String assigneeName;

    /** 备注 */
    @Size(max = 500, message = "备注最多 500 字")
    private String note;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
