package com.bluewhale.party.member.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.repository.BranchRepository;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.member.application.command.MemberSaveCommand;
import com.bluewhale.party.member.application.command.MemberTransferCommand;
import com.bluewhale.party.member.application.service.MemberAppService;
import com.bluewhale.party.member.application.view.MemberView;
import com.bluewhale.party.member.domain.model.MemberStatus;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.model.PartyMemberQuery;
import com.bluewhale.party.member.domain.model.PoliticalStatus;
import com.bluewhale.party.member.domain.repository.PartyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 党员台账应用服务实现。
 *
 * <p>建档只做「一人一档 + 支部在册」两条前置校验，其余日期先后关系与字段边界落在
 * {@link PartyMember#validate()}：这样导入历史档案（Excel 批量）与页面建档走同一套规则，
 * 不会出现「页面拦得住、批量导入能绕过」的双口径。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MemberAppServiceImpl implements MemberAppService {

    private final PartyMemberRepository partyMemberRepository;

    private final BranchRepository branchRepository;

    private final PartySequenceService sequenceService;

    private final PartyProperties properties;

    @Override
    public PageResult<MemberView> page(PartyMemberQuery query, PartyActor actor) {
        assertGate();
        PartyMemberQuery safe = query == null ? new PartyMemberQuery() : query;
        long size = normalizeSize(safe.getSize());
        long current = safe.getCurrent() == null || safe.getCurrent() < 1L ? 1L : safe.getCurrent();
        List<PartyMember> members = partyMemberRepository.page(safe, (current - 1L) * size, (int) size);
        Map<Long, String> branchNames = branchNameIndex();
        List<MemberView> views = members.stream().map(member -> {
            MemberView view = MemberView.from(member);
            view.setBranchName(branchNames.get(member.getBranchId()));
            return view;
        }).toList();
        return PageResult.of(views, partyMemberRepository.count(safe), current, size);
    }

    @Override
    public MemberView detail(Long id, PartyActor actor) {
        assertGate();
        PartyMember member = requireMember(id);
        MemberView view = MemberView.from(member);
        view.setBranchName(branchRepository.findById(member.getBranchId())
                .map(Branch::getBranchName).orElse(null));
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberView create(MemberSaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        String employeeNo = command.getEmployeeNo().trim();
        if (partyMemberRepository.findByEmployeeNo(employeeNo).isPresent()) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_DUPLICATED);
        }
        requireActiveBranch(command.getBranchId());
        PartyMember member = new PartyMember();
        member.setMemberNo(sequenceService.nextMemberNo());
        member.setStatus(MemberStatus.ACTIVE);
        apply(member, command);
        member.validate();
        partyMemberRepository.save(member);
        return withBranchName(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberView update(Long id, MemberSaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        PartyMember member = requireMember(id);
        member.assertWritable();
        if (!member.getBranchId().equals(command.getBranchId())) {
            requireActiveBranch(command.getBranchId());
        }
        apply(member, command);
        member.validate();
        partyMemberRepository.save(member);
        return withBranchName(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberView transfer(Long id, MemberTransferCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        PartyMember member = requireMember(id);
        MemberTransferCommand safe = command == null ? new MemberTransferCommand() : command;
        member.transferOut(safe.getTransferDate());
        if (StringUtils.hasText(safe.getRemark())) {
            member.setRemark(appendRemark(member.getRemark(), safe.getRemark()));
        }
        member.validate();
        partyMemberRepository.save(member);
        return withBranchName(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberView promote(Long id, LocalDate fullMemberDate, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        PartyMember member = requireMember(id);
        member.promoteToFull(fullMemberDate);
        member.validate();
        partyMemberRepository.save(member);
        return withBranchName(member);
    }

    /**
     * 应用保存命令中的可写字段。
     *
     * @param member 档案
     * @param command 保存命令
     */
    private void apply(PartyMember member, MemberSaveCommand command) {
        member.setEmployeeNo(command.getEmployeeNo());
        member.setUserId(command.getUserId());
        member.setMemberName(command.getMemberName());
        member.setBranchId(command.getBranchId());
        member.setPoliticalStatus(PoliticalStatus.parse(command.getPoliticalStatus()));
        member.setJoinPartyDate(command.getJoinPartyDate());
        member.setDuesBase(command.getDuesBase());
        member.setPhone(StringUtils.hasText(command.getPhone()) ? command.getPhone().trim() : null);
        member.setRemark(StringUtils.hasText(command.getRemark()) ? command.getRemark().trim() : null);
    }

    /**
     * 追加备注（转出说明追加在原有备注之后，不覆盖既有内容）。
     *
     * @param origin 原备注
     * @param extra 追加内容
     * @return 合并后的备注（截断到列宽）
     */
    private String appendRemark(String origin, String extra) {
        String merged = StringUtils.hasText(origin) ? origin.trim() + "；" + extra.trim() : extra.trim();
        return merged.length() > PartyMember.REMARK_MAX_LENGTH
                ? merged.substring(0, PartyMember.REMARK_MAX_LENGTH) : merged;
    }

    /**
     * 取支部名称并回填视图。
     *
     * @param member 档案
     * @return 档案视图
     */
    private MemberView withBranchName(PartyMember member) {
        MemberView view = MemberView.from(member);
        view.setBranchName(branchRepository.findById(member.getBranchId())
                .map(Branch::getBranchName).orElse(null));
        return view;
    }

    /**
     * 校验支部存在且在册。
     *
     * @param branchId 支部 ID
     * @return 支部
     */
    private Branch requireActiveBranch(Long branchId) {
        if (branchId == null || branchId <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_NOT_FOUND);
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.BRANCH_NOT_FOUND));
        if (!branch.isActive()) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_INACTIVE, "目标支部已停用，不能挂入党员");
        }
        return branch;
    }

    /**
     * 按 ID 取档案，不存在即抛错。
     *
     * @param id 档案 ID
     * @return 档案
     */
    private PartyMember requireMember(Long id) {
        if (id == null || id <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.MEMBER_NOT_FOUND);
        }
        return partyMemberRepository.findById(id)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 支部 ID → 名称索引（列表回填支部名用）。
     *
     * @return 索引
     */
    private Map<Long, String> branchNameIndex() {
        Map<Long, String> index = new HashMap<>();
        for (Branch branch : branchRepository.listAll()) {
            index.put(branch.getId(), branch.getBranchName());
        }
        return index;
    }

    /**
     * 归一每页条数。
     *
     * @param size 请求条数
     * @return 合法条数
     */
    private long normalizeSize(Long size) {
        long value = size == null || size < 1L ? 20L : size;
        return Math.min(value, properties.getMemberPageLimit());
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
