package com.bluewhale.party.activity.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 组织生活新建 / 编辑命令。
 *
 * @author BlueWhale
 */
@Data
public class ActivitySaveCommand {

    /** 所属支部 ID */
    @NotNull(message = "所属支部不能为空")
    private Long branchId;

    /** 类型：BRANCH_COMMITTEE / MEMBER_ASSEMBLY / PARTY_GROUP / PARTY_LECTURE / THEME_DAY */
    private String activityType;

    /** 活动主题 */
    @NotBlank(message = "活动主题不能为空")
    @Size(max = 200, message = "活动主题不得超过 200 字符")
    private String title;

    /** 计划日期 */
    @NotNull(message = "计划日期不能为空")
    private LocalDate plannedDate;

    /** 活动地点 */
    @Size(max = 128, message = "活动地点不得超过 128 字符")
    private String location;

    /** 主持人 */
    @Size(max = 64, message = "主持人姓名不得超过 64 字符")
    private String hostName;

    /** 应到人数 */
    private Integer expectedCount;

    /** 会议纪要 */
    @Size(max = 2000, message = "纪要不得超过 2000 字符")
    private String summary;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
