package com.bluewhale.party.integration.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.integration.application.view.NoticeScanResultView;
import com.bluewhale.party.integration.application.view.NotifyLogView;
import com.bluewhale.party.integration.domain.model.NotifyLogQuery;
import com.bluewhale.party.integration.domain.model.NotifyScene;

import java.util.List;

/**
 * 通知投递应用服务（M-P4）。
 *
 * @author BlueWhale
 */
public interface NotifyAppService {

    /**
     * 投递一条通知（失败抛错）。
     *
     * @param scene 场景
     * @param idempotencyKey 幂等键
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @param recipients 接收人
     * @param title 标题
     * @param content 正文
     * @return 实际投递人数（已发过的接收人不计）
     */
    int send(NotifyScene scene, String idempotencyKey, String bizType, Long bizId,
             List<Long> recipients, String title, String content);

    /**
     * 投递一条通知（失败只记日志，不回滚主流程）。
     *
     * @param scene 场景
     * @param idempotencyKey 幂等键
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @param recipients 接收人
     * @param title 标题
     * @param content 正文
     * @return 实际投递人数
     */
    int sendQuietly(NotifyScene scene, String idempotencyKey, String bizType, Long bizId,
                    List<Long> recipients, String title, String content);

    /**
     * 提案反馈通知（提交人本人）。
     *
     * @param proposalId 提案 ID
     * @param submitterUserId 提交人
     * @param proposalNo 提案编号
     * @param feedback 反馈内容
     * @return 实际投递人数
     */
    int notifyProposalFeedback(Long proposalId, Long submitterUserId, String proposalNo, String feedback);

    /**
     * 通知留痕分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<NotifyLogView> page(NotifyLogQuery query, PartyActor actor);

    /**
     * 党务提醒扫描（转正 / 党费 / 组织生活 / 换届）。
     *
     * @return 扫描结果
     */
    NoticeScanResultView scanParty();

    /**
     * 工会活动通知扫描。
     *
     * @return 扫描结果
     */
    NoticeScanResultView scanUnion();
}
