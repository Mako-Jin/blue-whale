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
    @NotNull(message = "{validation.party.activity.branch-id-required}")
    private Long branchId;

    /** 类型：BRANCH_COMMITTEE / MEMBER_ASSEMBLY / PARTY_GROUP / PARTY_LECTURE / THEME_DAY */
    private String activityType;

    /** 活动主题 */
    @NotBlank(message = "{validation.party.activity.title-required}")
    @Size(max = 200, message = "{validation.party.activity.title-size}")
    private String title;

    /** 计划日期 */
    @NotNull(message = "{validation.party.activity.planned-date-required}")
    private LocalDate plannedDate;

    /** 活动地点 */
    @Size(max = 128, message = "{validation.party.activity.location-size}")
    private String location;

    /** 主持人 */
    @Size(max = 64, message = "{validation.party.activity.host-name-size}")
    private String hostName;

    /** 应到人数 */
    private Integer expectedCount;

    /** 会议纪要 */
    @Size(max = 2000, message = "{validation.party.activity.summary-size}")
    private String summary;

    /** 备注 */
    @Size(max = 255, message = "{validation.party.activity.remark-size}")
    private String remark;
}
