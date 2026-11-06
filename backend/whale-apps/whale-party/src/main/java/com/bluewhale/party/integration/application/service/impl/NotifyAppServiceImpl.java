package com.bluewhale.party.integration.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.activity.application.service.ActivityAppService;
import com.bluewhale.party.activity.application.view.ActivityNoticeView;
import com.bluewhale.party.branch.application.service.BranchAppService;
import com.bluewhale.party.branch.application.view.BranchTermDueView;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.fee.application.service.FeeAppService;
import com.bluewhale.party.fee.application.view.FeeBillNoticeView;
import com.bluewhale.party.integration.application.service.NotifyAppService;
import com.bluewhale.party.integration.application.view.NoticeScanResultView;
import com.bluewhale.party.integration.application.view.NotifyLogView;
import com.bluewhale.party.integration.domain.gateway.NotifyGateway;
import com.bluewhale.party.integration.domain.model.NotifyKey;
import com.bluewhale.party.integration.domain.model.NotifyLog;
import com.bluewhale.party.integration.domain.model.NotifyLogQuery;
import com.bluewhale.party.integration.domain.model.NotifyRequest;
import com.bluewhale.party.integration.domain.model.NotifyScene;
import com.bluewhale.party.integration.domain.repository.NotifyLogRepository;
import com.bluewhale.party.member.application.service.MemberAppService;
import com.bluewhale.party.member.application.view.MemberNoticeTargetView;
import com.bluewhale.party.member.application.view.ProbationCandidateView;
import com.bluewhale.party.union.application.service.UnionActivityAppService;
import com.bluewhale.party.union.application.service.UnionMemberAppService;
import com.bluewhale.party.union.application.view.UnionActivityNoticeView;
import com.bluewhale.party.union.application.view.UnionNoticeTargetView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 通知投递应用服务实现。
 *
 * <p>幂等靠「先查留痕、已发即跳过」：同一幂等键 + 同一接收人只要成功过一次就不再投递。
 * 先查后发之间存在极窄的并发窗口，最终由 {@code pty_notify_log} 的唯一键兜底，
 * 重复插入会失败而不是发出第二条通知。</p>
 *
 * <p>失败留痕不抛错（扫描场景）：一次下游抖动不应该让其余三类提醒全部不执行，
 * 失败的记录保留为 FAILED，下一轮扫描会重新尝试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyAppServiceImpl implements NotifyAppService {

    /** 月份格式 */
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /** 业务类型：党员 */
    private static final String BIZ_MEMBER = "MEMBER";

    /** 业务类型：党费账单 */
    private static final String BIZ_FEE_BILL = "FEE_BILL";

    /** 业务类型：组织生活 */
    private static final String BIZ_PARTY_ACTIVITY = "PARTY_ACTIVITY";

    /** 业务类型：支部 */
    private static final String BIZ_BRANCH = "BRANCH";

    /** 业务类型：工会活动 */
    private static final String BIZ_UNION_ACTIVITY = "UNION_ACTIVITY";

    /** 业务类型：提案 */
    private static final String BIZ_PROPOSAL = "PROPOSAL";

    private final NotifyLogRepository notifyLogRepository;

    private final NotifyGateway notifyGateway;

    private final MemberAppService memberAppService;

    private final FeeAppService feeAppService;

    private final ActivityAppService activityAppService;

    private final BranchAppService branchAppService;

    private final UnionActivityAppService unionActivityAppService;

    private final UnionMemberAppService unionMemberAppService;

    private final PartyProperties properties;

    @Override
    public int send(NotifyScene scene, String idempotencyKey, String bizType, Long bizId,
                    List<Long> recipients, String title, String content) {
        assertGate();
        DispatchResult result = dispatch(scene, idempotencyKey, bizType, bizId, recipients, title, content);
        if (result.sent == 0 && result.failed > 0) {
            throw PartyErrorCode.exception(PartyErrorCode.NOTICE_SEND_FAILED);
        }
        return result.sent;
    }

    @Override
    public int sendQuietly(NotifyScene scene, String idempotencyKey, String bizType, Long bizId,
                           List<Long> recipients, String title, String content) {
        try {
            return send(scene, idempotencyKey, bizType, bizId, recipients, title, content);
        } catch (RuntimeException exception) {
            log.warn("通知投递失败（主流程继续）: scene={}, key={}, error={}",
                    scene, idempotencyKey, exception.getMessage());
            return 0;
        }
    }

    @Override
    public int notifyProposalFeedback(Long proposalId, Long submitterUserId, String proposalNo, String feedback) {
        if (submitterUserId == null || submitterUserId <= 0) {
            return 0;
        }
        String content = "您提交的提案 " + (proposalNo == null ? "" : proposalNo)
                + " 已反馈：" + (feedback == null ? "请到提案详情查看办理结果" : feedback);
        return sendQuietly(NotifyScene.UNION_PROPOSAL_FEEDBACK,
                NotifyKey.proposalFeedback(proposalId), BIZ_PROPOSAL, proposalId,
                List.of(submitterUserId), "提案办理反馈", content);
    }

    @Override
    public PageResult<NotifyLogView> page(NotifyLogQuery query, PartyActor actor) {
        assertGate();
        if (actor == null || actor.getUserId() == null || actor.getUserId() <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTOR_REQUIRED);
        }
        NotifyLogQuery safe = query == null ? new NotifyLogQuery() : query;
        long current = safe.getCurrent() == null || safe.getCurrent() < 1 ? 1L : safe.getCurrent();
        long size = safe.getSize() == null || safe.getSize() < 1
                ? 20L : Math.min(safe.getSize(), properties.getNoticePageLimit());
        long offset = (current - 1) * size;
        List<NotifyLogView> records = notifyLogRepository.page(safe, offset, (int) size)
                .stream().map(NotifyLogView::from).toList();
        return PageResult.of(records, notifyLogRepository.count(safe), current, size);
    }

    @Override
    public NoticeScanResultView scanParty() {
        assertGate();
        LocalDate today = LocalDate.now();
        NoticeScanResultView result = new NoticeScanResultView();
        int probation = scanProbation(today);
        int fee = scanFee(today);
        int activity = scanPartyActivity(today);
        int term = scanBranchTerm();
        result.getSentByScene().put(NotifyScene.PARTY_PROBATION.name(), probation);
        result.getSentByScene().put(NotifyScene.PARTY_FEE.name(), fee);
        result.getSentByScene().put(NotifyScene.PARTY_ACTIVITY.name(), activity);
        result.getSentByScene().put(NotifyScene.PARTY_BRANCH_TERM.name(), term);
        result.setTotalSent(probation + fee + activity + term);
        return result;
    }

    @Override
    public NoticeScanResultView scanUnion() {
        assertGate();
        LocalDate today = LocalDate.now();
        NoticeScanResultView result = new NoticeScanResultView();
        int activity = scanUnionActivity(today);
        result.getSentByScene().put(NotifyScene.UNION_ACTIVITY.name(), activity);
        result.setTotalSent(activity);
        return result;
    }

    /**
     * 预备党员转正提醒（党员本人 + 支部书记）。
     *
     * @param today 基准日
     * @return 实际发出条数
     */
    private int scanProbation(LocalDate today) {
        int sent = 0;
        for (ProbationCandidateView candidate : memberAppService.probationCandidates(today)) {
            Set<Long> recipients = new LinkedHashSet<>();
            addRecipient(recipients, candidate.getUserId());
            addRecipient(recipients, candidate.getSecretaryUserId());
            String content = "党员 " + candidate.getMemberName() + " 的预备期将于 "
                    + candidate.getDueDate() + " 满一年，请及时研究转正事宜。";
            sent += tryDispatch(NotifyScene.PARTY_PROBATION,
                    NotifyKey.probation(candidate.getMemberId(), today), BIZ_MEMBER,
                    candidate.getMemberId(), List.copyOf(recipients), "预备党员转正提醒", content);
        }
        return sent;
    }

    /**
     * 党费催缴（欠缴账单指向的党员）。
     *
     * @param today 基准日
     * @return 实际发出条数
     */
    private int scanFee(LocalDate today) {
        int sent = 0;
        String periodMonth = MONTH.format(today);
        for (FeeBillNoticeView bill : feeAppService.outstandingBills(periodMonth)) {
            MemberNoticeTargetView target = memberAppService.noticeTarget(bill.getMemberId());
            if (target == null) {
                continue;
            }
            String content = "您 " + bill.getPeriodMonth() + " 月党费尚欠 "
                    + bill.getOutstandingAmount().toPlainString() + " 元，请及时缴纳。";
            sent += tryDispatch(NotifyScene.PARTY_FEE, NotifyKey.fee(bill.getBillId()),
                    BIZ_FEE_BILL, bill.getBillId(), List.of(target.getUserId()), "党费缴纳提醒", content);
        }
        return sent;
    }

    /**
     * 组织生活通知（支部全体有账号的党员）。
     *
     * @param today 基准日
     * @return 实际发出条数
     */
    private int scanPartyActivity(LocalDate today) {
        int sent = 0;
        for (ActivityNoticeView activity : activityAppService.upcoming(today)) {
            List<Long> recipients = memberAppService.noticeTargets(activity.getBranchId()).stream()
                    .map(MemberNoticeTargetView::getUserId)
                    .filter(userId -> userId != null && userId > 0)
                    .toList();
            if (recipients.isEmpty()) {
                continue;
            }
            String content = "支部活动「" + activity.getTitle() + "」计划于 "
                    + activity.getPlannedDate() + " 开展，请准时参加。";
            sent += tryDispatch(NotifyScene.PARTY_ACTIVITY, NotifyKey.partyActivity(activity.getActivityId()),
                    BIZ_PARTY_ACTIVITY, activity.getActivityId(), recipients, "组织生活通知", content);
        }
        return sent;
    }

    /**
     * 支部换届提醒（支部书记）。
     *
     * @return 实际发出条数
     */
    private int scanBranchTerm() {
        int sent = 0;
        for (BranchTermDueView branch : branchAppService.termDue(null, null)) {
            if (branch.getSecretaryUserId() == null || branch.getSecretaryUserId() <= 0) {
                continue;
            }
            String content = "支部「" + branch.getBranchName() + "」应于 " + branch.getTermDate()
                    + " 换届，剩余 " + branch.getRemainingDays() + " 天，请提前筹备。";
            sent += tryDispatch(NotifyScene.PARTY_BRANCH_TERM,
                    NotifyKey.branchTerm(branch.getBranchId(), branch.getTermDate().getYear()),
                    BIZ_BRANCH, branch.getBranchId(), List.of(branch.getSecretaryUserId()),
                    "支部换届提醒", content);
        }
        return sent;
    }

    /**
     * 工会活动通知（全体在册且开通账号的会员）。
     *
     * @param today 基准日
     * @return 实际发出条数
     */
    private int scanUnionActivity(LocalDate today) {
        int sent = 0;
        List<Long> recipients = unionMemberAppService.noticeTargets().stream()
                .map(UnionNoticeTargetView::getUserId)
                .filter(userId -> userId != null && userId > 0)
                .toList();
        if (recipients.isEmpty()) {
            return 0;
        }
        for (UnionActivityNoticeView activity : unionActivityAppService.upcoming(today)) {
            String content = "工会活动「" + activity.getTitle() + "」将于 "
                    + activity.getStartTime() + " 开始"
                    + (activity.getLocation() == null ? "。" : "，地点：" + activity.getLocation() + "。");
            sent += tryDispatch(NotifyScene.UNION_ACTIVITY,
                    NotifyKey.unionActivity(activity.getActivityId()), BIZ_UNION_ACTIVITY,
                    activity.getActivityId(), recipients, "工会活动通知", content);
        }
        return sent;
    }

    /**
     * 扫描场景容错投递（下游失败不影响其余场景）。
     *
     * @return 实际发出条数
     */
    private int tryDispatch(NotifyScene scene, String key, String bizType, Long bizId,
                            List<Long> recipients, String title, String content) {
        try {
            return dispatch(scene, key, bizType, bizId, recipients, title, content).sent;
        } catch (RuntimeException exception) {
            log.warn("提醒扫描投递失败: scene={}, key={}, error={}", scene, key, exception.getMessage());
            return 0;
        }
    }

    /**
     * 逐接收人投递并留痕。
     *
     * @param scene 场景
     * @param idempotencyKey 幂等键
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @param recipients 接收人
     * @param title 标题
     * @param content 正文
     * @return 投递结果
     */
    private DispatchResult dispatch(NotifyScene scene, String idempotencyKey, String bizType, Long bizId,
                                   List<Long> recipients, String title, String content) {
        if (recipients == null || recipients.isEmpty()) {
            return new DispatchResult(0, 0);
        }
        DispatchResult result = new DispatchResult(0, 0);
        for (Long userId : new LinkedHashSet<>(recipients)) {
            if (userId == null || userId <= 0) {
                continue;
            }
            NotifyLog log = notifyLogRepository.findByKeyAndUser(idempotencyKey, userId)
                    .orElseGet(() -> newLog(scene, idempotencyKey, bizType, bizId, userId, title));
            if (log.alreadySent()) {
                continue;
            }
            log.setTitle(title);
            log.setTemplateCode(scene.getTemplateCode());
            LocalDateTime now = LocalDateTime.now();
            try {
                notifyGateway.send(requestOf(scene, idempotencyKey, bizType, bizId, userId, title, content));
                log.markSent(now);
                result.sent++;
            } catch (RuntimeException exception) {
                log.markFailed(exception.getMessage(), now);
                result.failed++;
            }
            notifyLogRepository.save(log);
        }
        return result;
    }

    /**
     * 构造新留痕。
     *
     * @param scene 场景
     * @param idempotencyKey 幂等键
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @param userId 接收人
     * @param title 标题
     * @return 留痕
     */
    private NotifyLog newLog(NotifyScene scene, String idempotencyKey, String bizType,
                             Long bizId, Long userId, String title) {
        NotifyLog log = new NotifyLog();
        log.setScene(scene);
        log.setIdempotencyKey(idempotencyKey);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setUserId(userId);
        log.setTemplateCode(scene.getTemplateCode());
        log.setTitle(title);
        log.setChannel("INAPP");
        return log;
    }

    /**
     * 构造投递请求（单接收人）。
     *
     * @param scene 场景
     * @param idempotencyKey 幂等键
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @param userId 接收人
     * @param title 标题
     * @param content 正文
     * @return 请求
     */
    private NotifyRequest requestOf(NotifyScene scene, String idempotencyKey, String bizType,
                                    Long bizId, Long userId, String title, String content) {
        NotifyRequest request = new NotifyRequest();
        request.setScene(scene);
        request.setIdempotencyKey(idempotencyKey);
        request.setBizType(bizType);
        request.setBizId(bizId);
        request.setTemplateCode(scene.getTemplateCode());
        request.setTitle(title);
        request.setContent(content);
        request.setRecipients(List.of(userId));
        return request;
    }

    /**
     * 接收人去重并忽略空值。
     *
     * @param recipients 接收人集合
     * @param userId 用户 ID
     */
    private void addRecipient(Set<Long> recipients, Long userId) {
        if (userId != null && userId > 0) {
            recipients.add(userId);
        }
    }

    /**
     * 需求门禁。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw PartyErrorCode.exception(PartyErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 投递结果（成功 / 失败计数，逐接收人累加）。
     */
    private static final class DispatchResult {

        /** 成功条数 */
        private int sent;

        /** 失败条数 */
        private int failed;

        /**
         * 构造投递结果。
         *
         * @param sent 成功条数
         * @param failed 失败条数
         */
        private DispatchResult(int sent, int failed) {
            this.sent = sent;
            this.failed = failed;
        }
    }
}
