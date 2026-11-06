package com.bluewhale.lms.integration.application.service;

import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.integration.application.view.ScheduleConflictView;
import com.bluewhale.lms.integration.application.view.UrgeItemView;
import com.bluewhale.lms.integration.application.view.UrgeRunView;

import java.time.LocalDate;
import java.util.List;

/**
 * 未完成必修催办应用服务。
 *
 * <p>「未完成」判定完全复用培训域的完成认定（{@code TrainingAppService#completion}），
 * 不在这里另写一套口径——两套判定迟早会对不上，届时会出现「完成认定说没完成、
 * 催办说他已完成」的互斥结论。</p>
 *
 * @author BlueWhale
 */
public interface UrgeAppService {

    /**
     * 执行一轮催办（可按天补跑）。
     *
     * @param day 催办归属日期，空则取当天
     * @param actor 操作人
     * @return 结果汇总
     */
    UrgeRunView run(LocalDate day, LmsActor actor);

    /**
     * 预览单个培训班的可催办名单（不投递）。
     *
     * @param classId 培训班 ID
     * @param actor 操作人
     * @return 该班催办结果（notified 恒为 0）
     */
    UrgeItemView preview(Long classId, LmsActor actor);

    /**
     * 调度入口：按当天执行一轮；应用开关关闭时直接返回 {@code null}。
     *
     * @return 结果汇总；开关关闭返回 {@code null}
     */
    UrgeRunView runScheduled();

    /**
     * 查询培训班的场次与学员请假冲突（只读提示）。
     *
     * @param classId 培训班 ID
     * @param actor 操作人
     * @return 冲突明细；无冲突返回空列表
     */
    List<ScheduleConflictView> scheduleConflicts(Long classId, LmsActor actor);
}
