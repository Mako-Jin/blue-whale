package com.bluewhale.lms.integration.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.integration.application.view.NotifyLogView;
import com.bluewhale.lms.integration.domain.model.NotifyLogQuery;
import com.bluewhale.lms.integration.domain.model.NotifyScene;

import java.time.LocalDate;
import java.util.List;

/**
 * 通知投递应用服务。
 *
 * <p>所有投递都经本服务统一收口，目的是让「幂等键怎么拼、失败怎么留痕」
 * 只有一处实现。业务侧不允许直接调消息服务，否则换个入口就会重复发。</p>
 *
 * @author BlueWhale
 */
public interface NotifyAppService {

    /**
     * 投递通知：已发过的接收人自动跳过。
     *
     * @param recipients 接收人
     * @param scene 场景
     * @param idempotencyKey 幂等键
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @param title 标题
     * @param content 正文
     * @return 实际投递的接收人数；全部已发过返回 0
     */
    int send(List<Long> recipients, NotifyScene scene,
             String idempotencyKey, String bizType, Long bizId, String title, String content);

    /**
     * 投递通知且不向外抛异常（用于业务主流程的附带通知）。
     *
     * <p>发证成功但通知失败不应该回滚发证：证书已经开了号、落了库，
     * 回滚会让「证书编号已占用但记录不存在」，比漏一条通知严重得多。
     * 失败只留痕，由运维按 {@code FAILED} 补发。</p>
     *
     * @param recipients 接收人
     * @param scene 场景
     * @param idempotencyKey 幂等键
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @param title 标题
     * @param content 正文
     * @return 实际投递的接收人数；失败或已发过返回 0
     */
    int sendQuietly(List<Long> recipients, NotifyScene scene,
                    String idempotencyKey, String bizType, Long bizId, String title, String content);

    /**
     * 报名成功通知。
     *
     * @param courseId 课程 ID
     * @param courseName 课程名称
     * @param userId 学员
     * @return 实际投递的接收人数
     */
    int notifyEnroll(Long courseId, String courseName, Long userId);

    /**
     * 发证通知。
     *
     * @param certificateId 证书 ID
     * @param certNo 证书编号
     * @param certName 证书名称
     * @param userId 持证人
     * @return 实际投递的接收人数
     */
    int notifyCertIssue(Long certificateId, String certNo, String certName, Long userId);

    /**
     * 催学通知（按天幂等）。
     *
     * @param classId 培训班 ID
     * @param className 培训班名称
     * @param userIds 接收人
     * @param day 催办日期
     * @return 实际投递的接收人数
     */
    int notifyUrge(Long classId, String className, List<Long> userIds, LocalDate day);

    /**
     * 分页查询投递日志（仅管理员）。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<NotifyLogView> page(NotifyLogQuery query, LmsActor actor);
}
