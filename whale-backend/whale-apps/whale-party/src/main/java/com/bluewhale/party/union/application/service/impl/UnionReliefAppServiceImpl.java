package com.bluewhale.party.union.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.application.command.ReliefPayCommand;
import com.bluewhale.party.union.application.command.ReliefSaveCommand;
import com.bluewhale.party.union.application.service.UnionReliefAppService;
import com.bluewhale.party.union.application.view.UnionReliefSubmitView;
import com.bluewhale.party.union.application.view.UnionReliefView;
import com.bluewhale.party.union.domain.gateway.ReliefApprovalGateway;
import com.bluewhale.party.union.domain.gateway.ReliefApprovalRequest;
import com.bluewhale.party.union.domain.model.ReliefStatus;
import com.bluewhale.party.union.domain.model.ReliefType;
import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionRelief;
import com.bluewhale.party.union.domain.model.UnionReliefQuery;
import com.bluewhale.party.union.domain.repository.UnionMemberRepository;
import com.bluewhale.party.union.domain.repository.UnionReliefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 慰问帮扶应用服务实现。
 *
 * <p>核心是两条「宁可保守」的口径：</p>
 * <ol>
 *   <li><b>上限在提交时校验</b>：草稿可以先存，但一旦要进审批，就必须先满足同人同年同类型的
 *       次数与金额上限，避免批下来才发现超额；</li>
 *   <li><b>审批不可用只降级、不回滚</b>：发起审批失败时不抛异常、不改状态，把提示写进
 *       {@code approvalHint} 并保存草稿。用户看到「没提交成功」可以重试，已填的数据不会丢。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UnionReliefAppServiceImpl implements UnionReliefAppService {

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 审批降级提示 */
    private static final String FALLBACK_HINT = "审批服务不可用，已保存为草稿";

    /** 审批处理中提示 */
    private static final String PENDING_HINT = "审批处理中";

    /** 工作流运行中状态快照 */
    private static final String APPROVAL_RUNNING = "RUNNING";

    /** 工作流通过状态快照 */
    private static final String APPROVAL_APPROVED = "APPROVED";

    /** 工作流退回状态快照 */
    private static final String APPROVAL_REJECTED = "REJECTED";

    /** 工作流「已通过」状态码 */
    private static final int WORKFLOW_APPROVED = 1;

    /** 工作流「已驳回」状态码 */
    private static final int WORKFLOW_REJECTED = 2;

    private final UnionReliefRepository unionReliefRepository;

    private final UnionMemberRepository unionMemberRepository;

    private final ReliefApprovalGateway approvalGateway;

    private final PartySequenceService sequenceService;

    private final PartyProperties properties;

    @Override
    public PageResult<UnionReliefView> page(UnionReliefQuery query, Long current, Long size, PartyActor actor) {
        assertGate();
        UnionReliefQuery safe = query == null ? new UnionReliefQuery() : query;
        long pageSize = normalizeSize(size);
        long pageCurrent = current == null || current < 1L ? 1L : current;
        List<UnionRelief> reliefs = unionReliefRepository.page(safe, (pageCurrent - 1L) * pageSize, (int) pageSize);
        List<UnionReliefView> views = reliefs.stream().map(UnionReliefView::from).toList();
        return PageResult.of(views, unionReliefRepository.count(safe), pageCurrent, pageSize);
    }

    @Override
    public UnionReliefView detail(Long id, PartyActor actor) {
        assertGate();
        return UnionReliefView.from(requireRelief(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionReliefView create(ReliefSaveCommand command, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        requireActiveMember(command.getUnionMemberId());
        UnionRelief relief = new UnionRelief();
        relief.setReliefNo(sequenceService.nextUnionReliefNo());
        relief.setApplicantUserId(userId);
        relief.setApplyYear(LocalDate.now().getYear());
        relief.setStatus(ReliefStatus.DRAFT);
        apply(relief, command);
        relief.validate();
        unionReliefRepository.save(relief);
        return UnionReliefView.from(relief);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionReliefView update(Long id, ReliefSaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionRelief relief = requireRelief(id);
        if (relief.getStatus() == null || !relief.getStatus().isDraft()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, "只有草稿状态的申请可以编辑");
        }
        requireActiveMember(command.getUnionMemberId());
        apply(relief, command);
        relief.validate();
        unionReliefRepository.save(relief);
        return UnionReliefView.from(relief);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionReliefSubmitView submit(Long id, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionRelief relief = requireRelief(id);
        if (relief.getStatus() == null || !relief.getStatus().isDraft()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, "只有草稿状态的申请可以提交审批");
        }
        ensureWithinLimit(relief);
        Long instanceId;
        try {
            instanceId = approvalGateway.start(new ReliefApprovalRequest(relief.getId(), relief.getReliefNo(),
                    relief.getUnionMemberId(), relief.getReliefType(), relief.getApplyYear(), relief.getAmount()));
            if (instanceId == null) {
                throw PartyErrorCode.exception(PartyErrorCode.UNION_APPROVAL_UNAVAILABLE, "审批未返回实例 ID");
            }
        } catch (Exception exception) {
            // 降级：保留草稿并写提示。外部审批失败不能把用户已填的申请回滚掉
            relief.setApprovalHint(FALLBACK_HINT);
            unionReliefRepository.save(relief);
            return UnionReliefSubmitView.of(relief, false);
        }
        relief.submit();
        relief.setSubmitTime(LocalDateTime.now());
        relief.setApprovalInstanceId(instanceId);
        relief.setApprovalStatus(APPROVAL_RUNNING);
        relief.setApprovalHint(null);
        unionReliefRepository.save(relief);
        return UnionReliefSubmitView.of(relief, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionReliefView sync(Long id, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionRelief relief = requireRelief(id);
        if (relief.getStatus() == null || !relief.getStatus().isSubmitted()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, "只有审批中的申请可以同步审批结果");
        }
        Integer workflowStatus = approvalGateway.queryStatus(relief.getApprovalInstanceId());
        applyWorkflowStatus(relief, workflowStatus);
        unionReliefRepository.save(relief);
        return UnionReliefView.from(relief);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionReliefView pay(Long id, ReliefPayCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionRelief relief = requireRelief(id);
        if (relief.getStatus() == null || relief.getStatus() != ReliefStatus.APPROVED) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_STATE_CONFLICT, "只有审批通过的申请可以登记发放");
        }
        relief.pay();
        ReliefPayCommand safe = command == null ? new ReliefPayCommand() : command;
        relief.setPaidAt(safe.getPaidAt() == null ? LocalDateTime.now() : safe.getPaidAt().atStartOfDay());
        unionReliefRepository.save(relief);
        return UnionReliefView.from(relief);
    }

    /**
     * 应用保存命令中的可写字段。
     *
     * @param relief 申请
     * @param command 保存命令
     */
    private void apply(UnionRelief relief, ReliefSaveCommand command) {
        relief.setUnionMemberId(command.getUnionMemberId());
        relief.setReliefType(ReliefType.parse(command.getReliefType()));
        relief.setAmount(command.getAmount());
        relief.setReason(command.getReason());
        relief.setRemark(StringUtils.hasText(command.getRemark()) ? command.getRemark().trim() : null);
    }

    /**
     * 校验上限：同人同年同类型的已批次数与已批金额都必须在配置范围内。
     *
     * @param relief 申请
     */
    private void ensureWithinLimit(UnionRelief relief) {
        long approvedCount = unionReliefRepository.countApproved(relief.getUnionMemberId(),
                relief.getReliefType(), relief.getApplyYear());
        if (approvedCount + 1L > properties.getReliefCountLimit()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_LIMIT_EXCEEDED,
                    "同人同年同类型慰问已达 " + properties.getReliefCountLimit() + " 次，不能再提交");
        }
        BigDecimal approvedAmount = unionReliefRepository.sumApprovedAmount(relief.getUnionMemberId(),
                relief.getReliefType(), relief.getApplyYear());
        BigDecimal total = approvedAmount.add(relief.getAmount());
        if (total.compareTo(properties.getReliefAmountLimit()) > 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_LIMIT_EXCEEDED,
                    "同人同年同类型慰问累计不得超过 " + properties.getReliefAmountLimit().toPlainString() + " 元");
        }
    }

    /**
     * 应用工作流状态码。
     *
     * @param relief 申请
     * @param workflowStatus 工作流状态码
     */
    private void applyWorkflowStatus(UnionRelief relief, Integer workflowStatus) {
        if (workflowStatus != null && workflowStatus == WORKFLOW_APPROVED) {
            relief.markApproved();
            relief.setApprovedAt(LocalDateTime.now());
            relief.setApprovalStatus(APPROVAL_APPROVED);
            relief.setApprovalHint(null);
            return;
        }
        if (workflowStatus != null && workflowStatus == WORKFLOW_REJECTED) {
            relief.markRejected();
            relief.setApprovalStatus(APPROVAL_REJECTED);
            relief.setApprovalHint(null);
            return;
        }
        relief.setApprovalHint(PENDING_HINT);
    }

    /**
     * 校验会员存在且在册。
     *
     * @param unionMemberId 会员 ID
     * @return 会员
     */
    private UnionMember requireActiveMember(Long unionMemberId) {
        if (unionMemberId == null || unionMemberId <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_NOT_FOUND);
        }
        UnionMember member = unionMemberRepository.findById(unionMemberId)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_NOT_FOUND));
        if (!member.isActive()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_STATE_CONFLICT, "已退会会员不能申请慰问帮扶");
        }
        return member;
    }

    /**
     * 按 ID 取申请，不存在即抛错。
     *
     * @param id 申请 ID
     * @return 申请
     */
    private UnionRelief requireRelief(Long id) {
        if (id == null || id <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_NOT_FOUND);
        }
        return unionReliefRepository.findById(id)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.UNION_RELIEF_NOT_FOUND));
    }

    /**
     * 归一每页条数并套用工会单页上限。
     *
     * @param size 请求条数
     * @return 合法条数
     */
    private long normalizeSize(Long size) {
        long value = size == null || size < 1L ? DEFAULT_PAGE_SIZE : size;
        return Math.min(value, properties.getUnionPageLimit());
    }

    /**
     * 需求门禁。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw PartyErrorCode.exception(PartyErrorCode.GATE_CLOSED);
        }
    }
}
