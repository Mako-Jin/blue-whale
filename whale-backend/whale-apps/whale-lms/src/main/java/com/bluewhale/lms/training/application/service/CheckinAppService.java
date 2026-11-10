package com.bluewhale.lms.training.application.service;

import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.training.application.command.CheckinSupplementCommand;
import com.bluewhale.lms.training.application.view.CheckinView;

import java.util.List;

/**
 * 面授签到应用服务（M2）。
 *
 * @author BlueWhale
 */
public interface CheckinAppService {

    /**
     * 自助签到：仅在场次时间窗内、且已被分配该培训班时可签，重复签到幂等返回既有记录。
     *
     * @param sessionId 场次 ID
     * @param actor 操作人（学员本人）
     * @return 签到记录
     */
    CheckinView checkin(Long sessionId, LmsActor actor);

    /**
     * 补签：不受时间窗限制，但必须记录操作人与备注。
     *
     * @param sessionId 场次 ID
     * @param command 补签命令
     * @param actor 操作人（管理员）
     * @return 签到记录
     */
    CheckinView supplement(Long sessionId, CheckinSupplementCommand command, LmsActor actor);

    /**
     * 签到明细。
     *
     * @param classId 培训班 ID
     * @param sessionId 场次 ID，为空则查全班
     * @param actor 操作人
     * @return 签到记录列表
     */
    List<CheckinView> listByClass(Long classId, Long sessionId, LmsActor actor);
}
