package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会议聚合根。
 *
 * <p>状态机：{@code SCHEDULED → FINISHED / CANCELLED}，终态不可回流；
 * 仅 {@code SCHEDULED} 可编辑、取消与写纪要。</p>
 *
 * @author BlueWhale
 */
@Data
public class Meeting {

    /** 主键ID */
    private Long id;

    /** 会议单号 */
    private String code;

    /** 会议主题 */
    private String title;

    /** 会议室ID */
    private Long roomId;

    /** 组织人账号 */
    private String organizer;

    /** 组织人用户ID */
    private Long organizerId;

    /** 组织部门ID */
    private Long deptId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 会议议程 */
    private String agenda;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 参会人快照 */
    private List<MeetingParticipant> participants;

    /** 会议纪要 */
    private MeetingMinute minute;

    /** 行动项 */
    private List<MeetingAction> actions;

    /**
     * 校验时间区间。
     */
    public void assertTimeRange() {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw OaErrorCode.exception(OaErrorCode.COLLAB_INVALID, "会议开始时间必须早于结束时间");
        }
    }

    /**
     * 校验是否可编辑。
     */
    public void assertEditable() {
        if (MeetingStatus.of(status) != MeetingStatus.SCHEDULED) {
            throw OaErrorCode.exception(OaErrorCode.MEETING_STATUS_CONFLICT, "仅已安排的会议可编辑");
        }
    }

    /**
     * 变更状态：终态不可回流，重复终态幂等返回 false。
     *
     * @param target 目标状态
     * @return 是否发生变更
     */
    public boolean changeStatus(MeetingStatus target) {
        MeetingStatus current = MeetingStatus.of(status);
        if (current == target) {
            return false;
        }
        if (current != MeetingStatus.SCHEDULED) {
            throw OaErrorCode.exception(OaErrorCode.MEETING_STATUS_CONFLICT, "会议已处于终态，不可再次变更");
        }
        if (target == MeetingStatus.SCHEDULED) {
            throw OaErrorCode.exception(OaErrorCode.MEETING_STATUS_CONFLICT, "会议不能回到已安排状态");
        }
        this.status = target.name();
        return true;
    }
}
