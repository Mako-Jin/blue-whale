package com.bluewhale.party.union.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.application.command.UnionMemberSaveCommand;
import com.bluewhale.party.union.application.command.UnionMemberWithdrawCommand;
import com.bluewhale.party.union.application.service.UnionMemberAppService;
import com.bluewhale.party.union.application.view.UnionMemberMetricView;
import com.bluewhale.party.union.application.view.UnionMemberView;
import com.bluewhale.party.union.application.view.UnionNoticeTargetView;
import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionMemberQuery;
import com.bluewhale.party.union.domain.model.UnionMemberStatus;
import com.bluewhale.party.union.domain.repository.UnionMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 工会会员应用服务实现。
 *
 * <p>「同一员工同时只能有一份在册会籍」在应用层按 {@code employeeNo + ACTIVE} 校验：
 * 退会后可再入会是正常业务事实，数据库唯一键会把它直接拒掉。编辑与退会都以会员 ID 定位，
 * 因此再入会必然新建一条档案，旧档的历史会费与活动记录得以保留。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UnionMemberAppServiceImpl implements UnionMemberAppService {

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    private final UnionMemberRepository unionMemberRepository;

    private final PartySequenceService sequenceService;

    private final PartyProperties properties;

    @Override
    public PageResult<UnionMemberView> page(UnionMemberQuery query, Long current, Long size, PartyActor actor) {
        assertGate();
        UnionMemberQuery safe = query == null ? new UnionMemberQuery() : query;
        long pageSize = normalizeSize(size);
        long pageCurrent = current == null || current < 1L ? 1L : current;
        List<UnionMember> members = unionMemberRepository.page(safe, (pageCurrent - 1L) * pageSize, (int) pageSize);
        List<UnionMemberView> views = members.stream().map(UnionMemberView::from).toList();
        return PageResult.of(views, unionMemberRepository.count(safe), pageCurrent, pageSize);
    }

    @Override
    public UnionMemberView detail(Long id, PartyActor actor) {
        assertGate();
        return UnionMemberView.from(requireMember(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionMemberView create(UnionMemberSaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        String employeeNo = command.getEmployeeNo().trim();
        if (unionMemberRepository.findActiveByEmployeeNo(employeeNo).isPresent()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_DUPLICATED);
        }
        UnionMember member = new UnionMember();
        member.setMemberNo(sequenceService.nextUnionMemberNo());
        member.setStatus(UnionMemberStatus.ACTIVE);
        apply(member, command);
        member.validate();
        unionMemberRepository.save(member);
        return UnionMemberView.from(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionMemberView update(Long id, UnionMemberSaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionMember member = requireMember(id);
        if (!member.isActive()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_STATE_CONFLICT, "已退会会员只读");
        }
        apply(member, command);
        member.validate();
        unionMemberRepository.save(member);
        return UnionMemberView.from(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionMemberView withdraw(Long id, UnionMemberWithdrawCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionMember member = requireMember(id);
        UnionMemberWithdrawCommand safe = command == null ? new UnionMemberWithdrawCommand() : command;
        member.withdraw(safe.getWithdrawDate());
        unionMemberRepository.save(member);
        return UnionMemberView.from(member);
    }

    @Override
    public UnionMemberMetricView metrics() {
        assertGate();
        UnionMemberMetricView view = new UnionMemberMetricView();
        view.setActiveMembers(unionMemberRepository.listActive().size());
        return view;
    }

    @Override
    public List<UnionNoticeTargetView> noticeTargets() {
        assertGate();
        return unionMemberRepository.listActive().stream()
                .filter(member -> member.getUserId() != null && member.getUserId() > 0)
                .map(member -> {
                    UnionNoticeTargetView view = new UnionNoticeTargetView();
                    view.setMemberId(member.getId());
                    view.setMemberName(member.getMemberName());
                    view.setUserId(member.getUserId());
                    return view;
                }).toList();
    }

    /**
     * 应用保存命令中的可写字段。
     *
     * <p>不写 memberNo 与 status：它们只由取号器与退会动作改变，避免编辑接口绕过终态。</p>
     *
     * @param member 工会会员
     * @param command 保存命令
     */
    private void apply(UnionMember member, UnionMemberSaveCommand command) {
        member.setEmployeeNo(command.getEmployeeNo());
        member.setUserId(command.getUserId());
        member.setMemberName(command.getMemberName());
        member.setDeptName(StringUtils.hasText(command.getDeptName()) ? command.getDeptName().trim() : null);
        member.setJoinDate(command.getJoinDate());
        member.setDuesBase(command.getDuesBase());
        member.setDuesPaidTo(command.getDuesPaidTo());
        member.setRemark(StringUtils.hasText(command.getRemark()) ? command.getRemark().trim() : null);
    }

    /**
     * 按 ID 取会员，不存在即抛错。
     *
     * @param id 会员 ID
     * @return 会员
     */
    private UnionMember requireMember(Long id) {
        if (id == null || id <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_NOT_FOUND);
        }
        return unionMemberRepository.findById(id)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_NOT_FOUND));
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
