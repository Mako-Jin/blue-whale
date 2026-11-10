package com.bluewhale.lms.training.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 签到记录（场次 × 学员）。
 *
 * <p>两条硬规则：</p>
 * <ol>
 *   <li><b>时间窗</b>：自助签到只能在「场次开始 ~ 结束 + 宽限」之间完成，
 *       窗口判定依赖场次时间，由应用层调用 {@link ClassSession#withinCheckinWindow} 后落库；</li>
 *   <li><b>补签留痕</b>：补签必须带操作人与备注（{@link #supplement} 强制），
 *       否则事后无法回答「谁替谁补的、为什么补」。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Data
public class Checkin {

    /** 备注上限 */
    private static final int REMARK_MAX_LENGTH = 200;

    /** 主键 */
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
    private CheckinType checkinType;

    /** 签到时间 */
    private LocalDateTime checkinAt;

    /** 操作人 ID（补签必填） */
    private Long operatorId;

    /** 备注（补签必填） */
    private String remark;

    /**
     * 构造自助签到记录。
     *
     * @param session 场次
     * @param userId 学员用户 ID
     * @param username 学员用户名
     * @param now 当前时刻
     * @return 待落库的签到记录
     */
    public static Checkin self(ClassSession session, Long userId, String username, LocalDateTime now) {
        Checkin checkin = new Checkin();
        checkin.setClassId(session.getClassId());
        checkin.setSessionId(session.getId());
        checkin.setUserId(userId);
        checkin.setUsername(username);
        checkin.setCheckinType(CheckinType.SELF);
        checkin.setCheckinAt(now);
        return checkin;
    }

    /**
     * 构造补签记录：操作人与备注为必填。
     *
     * @param session 场次
     * @param userId 学员用户 ID
     * @param username 学员用户名
     * @param operatorId 操作人 ID
     * @param remark 备注
     * @param now 当前时刻
     * @return 待落库的签到记录
     */
    public static Checkin supplement(ClassSession session, Long userId, String username,
                                     Long operatorId, String remark, LocalDateTime now) {
        if (operatorId == null || operatorId <= 0) {
            throw LmsErrorCode.exception(LmsErrorCode.CHECKIN_REMARK_REQUIRED, "补签必须记录操作人");
        }
        if (remark == null || remark.isBlank()) {
            throw LmsErrorCode.exception(LmsErrorCode.CHECKIN_REMARK_REQUIRED, "补签必须填写备注");
        }
        String trimmed = remark.trim();
        if (trimmed.length() > REMARK_MAX_LENGTH) {
            throw LmsErrorCode.exception(LmsErrorCode.CHECKIN_REMARK_REQUIRED,
                    "补签备注不得超过 " + REMARK_MAX_LENGTH + " 字");
        }
        Checkin checkin = new Checkin();
        checkin.setClassId(session.getClassId());
        checkin.setSessionId(session.getId());
        checkin.setUserId(userId);
        checkin.setUsername(username);
        checkin.setCheckinType(CheckinType.SUPPLEMENT);
        checkin.setCheckinAt(now);
        checkin.setOperatorId(operatorId);
        checkin.setRemark(trimmed);
        return checkin;
    }
}
