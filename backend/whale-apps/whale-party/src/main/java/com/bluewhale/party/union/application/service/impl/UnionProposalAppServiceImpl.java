package com.bluewhale.party.union.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.application.command.ProposalFeedbackCommand;
import com.bluewhale.party.union.application.command.ProposalHandleCommand;
import com.bluewhale.party.union.application.command.ProposalRateCommand;
import com.bluewhale.party.union.application.command.ProposalSaveCommand;
import com.bluewhale.party.union.application.service.UnionProposalAppService;
import com.bluewhale.party.union.application.view.UnionProposalView;
import com.bluewhale.party.union.domain.model.ProposalStatus;
import com.bluewhale.party.union.domain.model.UnionProposal;
import com.bluewhale.party.union.domain.model.UnionProposalQuery;
import com.bluewhale.party.union.domain.repository.UnionProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 职代会提案应用服务实现。
 *
 * <p>三条边界：</p>
 * <ol>
 *   <li><b>园地只看本人</b>：{@code mineUserId} 在服务层强制覆盖为当前用户，
 *       即使请求里带了别人的 ID 也不会生效；</li>
 *   <li><b>办理类动作需管理权限</b>：立案、办理、反馈都改变提案的官方结论，
 *       不能用普通浏览权限调用；</li>
 *   <li><b>评价只归提交人</b>：满意度是提交人的主观评价，管理员不能代评。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UnionProposalAppServiceImpl implements UnionProposalAppService {

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    private final UnionProposalRepository unionProposalRepository;

    private final PartySequenceService sequenceService;

    private final PartyProperties properties;

    @Override
    public PageResult<UnionProposalView> page(UnionProposalQuery query, Long current, Long size,
                                              PartyActor actor) {
        assertGate();
        UnionProposalQuery safe = query == null ? new UnionProposalQuery() : query;
        return pageResult(safe, current, size);
    }

    @Override
    public UnionProposalView detail(Long id, PartyActor actor) {
        assertGate();
        return UnionProposalView.from(requireProposal(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionProposalView create(ProposalSaveCommand command, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        UnionProposal proposal = new UnionProposal();
        proposal.setProposalNo(sequenceService.nextUnionProposalNo());
        proposal.setTitle(command.getTitle());
        proposal.setContent(command.getContent());
        proposal.setSubmitterUserId(userId);
        // 姓名取当前登录名快照；组织调整后仍能还原当时的提交人显示名。
        proposal.setSubmitterName(actor.getUsername());
        proposal.setStatus(ProposalStatus.SUBMITTED);
        proposal.validate();
        unionProposalRepository.save(proposal);
        return UnionProposalView.from(proposal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionProposalView file(Long id, ProposalHandleCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        assertManage(actor);
        UnionProposal proposal = requireProposal(id);
        proposal.file(command == null ? null : command.getHandler());
        unionProposalRepository.save(proposal);
        return UnionProposalView.from(proposal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionProposalView handle(Long id, ProposalHandleCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        assertManage(actor);
        UnionProposal proposal = requireProposal(id);
        proposal.handle(command.getHandler());
        unionProposalRepository.save(proposal);
        return UnionProposalView.from(proposal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionProposalView feedback(Long id, ProposalFeedbackCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        assertManage(actor);
        UnionProposal proposal = requireProposal(id);
        proposal.feedback(command.getFeedback());
        unionProposalRepository.save(proposal);
        return UnionProposalView.from(proposal);
    }

    @Override
    public PageResult<UnionProposalView> portalPage(UnionProposalQuery query, Long current, Long size,
                                                    PartyActor actor) {
        assertGate();
        UnionProposalQuery safe = new UnionProposalQuery();
        if (query != null) {
            safe.setKeyword(query.getKeyword());
            safe.setStatus(query.getStatus());
        }
        // 强制只看本人：请求参数里的 mineUserId 一律丢弃。
        safe.setMineUserId(actor.requireUserId());
        return pageResult(safe, current, size);
    }

    @Override
    public UnionProposalView portalDetail(Long id, PartyActor actor) {
        assertGate();
        UnionProposal proposal = requireProposal(id);
        Long userId = actor.requireUserId();
        if (!userId.equals(proposal.getSubmitterUserId()) && !actor.canManageUnion()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_NOT_FOUND);
        }
        return UnionProposalView.from(proposal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionProposalView rate(Long id, ProposalRateCommand command, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        UnionProposal proposal = requireProposal(id);
        if (!userId.equals(proposal.getSubmitterUserId())) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_FORBIDDEN,
                    "只有提案提交人可以评价");
        }
        proposal.rate(command.getRating());
        unionProposalRepository.save(proposal);
        return UnionProposalView.from(proposal);
    }

    /**
     * 统一分页组装。
     *
     * @param query 查询条件
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    private PageResult<UnionProposalView> pageResult(UnionProposalQuery query, Long current, Long size) {
        long pageSize = normalizeSize(size);
        long pageCurrent = current == null || current < 1L ? 1L : current;
        List<UnionProposal> proposals = unionProposalRepository.page(query,
                (pageCurrent - 1L) * pageSize, (int) pageSize);
        return PageResult.of(proposals.stream().map(UnionProposalView::from).toList(),
                unionProposalRepository.count(query), pageCurrent, pageSize);
    }

    /**
     * 管理权限校验。
     *
     * @param actor 操作人
     */
    private void assertManage(PartyActor actor) {
        if (!actor.canManageUnion()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_FORBIDDEN,
                    "只有工会管理员可以办理提案");
        }
    }

    /**
     * 按 ID 取提案，不存在即抛错。
     *
     * @param id 提案 ID
     * @return 提案
     */
    private UnionProposal requireProposal(Long id) {
        if (id == null || id <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_NOT_FOUND);
        }
        return unionProposalRepository.findById(id)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.UNION_PROPOSAL_NOT_FOUND));
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
