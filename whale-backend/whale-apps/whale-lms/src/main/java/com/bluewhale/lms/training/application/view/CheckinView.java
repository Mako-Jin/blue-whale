package com.bluewhale.lms.training.application.view;

import com.bluewhale.lms.training.domain.model.Checkin;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 签到记录视图。
 *
 * @author BlueWhale
 */
@Data
public class CheckinView {

    /** 签到 ID */
    private Long id;

    /** 培训班 ID */
    private Long classId;

    /** 场次 ID */
    private Long sessionId;

    /** 学员用户 ID */
    private Long userId;

    /** 学员用户名 */
    private String username;

    /** 签到方式 */
    private String checkinType;

    /** 签到时间 */
    private LocalDateTime checkinAt;

    /** 操作人 ID（补签时有值） */
    private Long operatorId;

    /** 备注（补签原因） */
    private String remark;

    /**
     * 从领域模型转换。
     *
     * @param checkin 签到记录
     * @return 视图
     */
    public static CheckinView from(Checkin checkin) {
        CheckinView view = new CheckinView();
        view.setId(checkin.getId());
        view.setClassId(checkin.getClassId());
        view.setSessionId(checkin.getSessionId());
        view.setUserId(checkin.getUserId());
        view.setUsername(checkin.getUsername());
        view.setCheckinType(checkin.getCheckinType().name());
        view.setCheckinAt(checkin.getCheckinAt());
        view.setOperatorId(checkin.getOperatorId());
        view.setRemark(checkin.getRemark());
        return view;
    }
}
