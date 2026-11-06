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
import com.bluewhale.party.member.application.view.MemberMetricView;
import com.bluewhale.party.member.application.view.MemberNoticeTargetView;
import com.bluewhale.party.member.application.view.MemberView;
import com.bluewhale.party.member.application.view.ProbationCandidateView;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /** 年龄分桶（顺序固定，空桶也要出现在看板与导出里） */
    private static final List<String> AGE_BUCKETS = List.of(
            "UNDER_30", "30_39", "40_49", "50_59", "60_PLUS", "UNKNOWN");

    /** 党龄分桶 */
    private static final List<String> PARTY_AGE_BUCKETS = List.of(
            "UNDER_5", "5_10", "11_20", "21_30", "31_PLUS", "UNKNOWN");

    /** 学历分桶 */
    private static final List<String> EDUCATION_BUCKETS = List.of(
            "DOCTOR", "MASTER", "BACHELOR", "COLLEGE", "SECONDARY", "OTHER", "UNKNOWN");

    /** 未知学历桶 */
    private static final String EDUCATION_UNKNOWN = "UNKNOWN";

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

    @Override
    public MemberMetricView metrics(LocalDate statDate, Long branchId) {
        assertGate();
        LocalDate date = statDate == null ? LocalDate.now() : statDate;
        List<PartyMember> members = partyMemberRepository.listActive(branchId);
        MemberMetricView view = new MemberMetricView();
        view.setTotal(members.size());
        view.setAgeBuckets(emptyBuckets(AGE_BUCKETS));
        view.setPartyAgeBuckets(emptyBuckets(PARTY_AGE_BUCKETS));
        view.setEducationBuckets(emptyBuckets(EDUCATION_BUCKETS));
        long probationDue = 0L;
        LocalDate probationDeadline = date.plusDays(properties.getProbationReminderDays());
        for (PartyMember member : members) {
            bump(view.getAgeBuckets(), ageBucket(member.ageYears(date)));
            bump(view.getPartyAgeBuckets(), partyAgeBucket(member, date));
            bump(view.getEducationBuckets(), member.getEducation() == null
                    ? EDUCATION_UNKNOWN : member.getEducation());
            if (member.isProbationaryPending()) {
                LocalDate dueDate = probationDueDate(member);
                if (dueDate != null && !dueDate.isAfter(probationDeadline)) {
                    probationDue++;
                }
            }
        }
        view.setProbationDue(probationDue);
        return view;
    }

    @Override
    public List<ProbationCandidateView> probationCandidates(LocalDate asOf) {
        assertGate();
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        LocalDate deadline = date.plusDays(properties.getProbationReminderDays());
        Map<Long, Branch> branches = new HashMap<>();
        for (Branch branch : branchRepository.listAll()) {
            branches.put(branch.getId(), branch);
        }
        List<ProbationCandidateView> candidates = new ArrayList<>();
        for (PartyMember member : partyMemberRepository.listActive(null)) {
            if (!member.isProbationaryPending()) {
                continue;
            }
            LocalDate dueDate = probationDueDate(member);
            if (dueDate == null || dueDate.isAfter(deadline)) {
                continue;
            }
            ProbationCandidateView view = new ProbationCandidateView();
            view.setMemberId(member.getId());
            view.setMemberName(member.getMemberName());
            view.setUserId(member.getUserId());
            view.setBranchId(member.getBranchId());
            Branch branch = branches.get(member.getBranchId());
            view.setBranchName(branch == null ? null : branch.getBranchName());
            view.setSecretaryUserId(branch == null ? null : branch.getSecretaryUserId());
            view.setJoinPartyDate(member.getJoinPartyDate());
            view.setDueDate(dueDate);
            candidates.add(view);
        }
        return candidates;
    }

    @Override
    public List<MemberNoticeTargetView> noticeTargets(Long branchId) {
        assertGate();
        List<MemberNoticeTargetView> targets = new ArrayList<>();
        for (PartyMember member : partyMemberRepository.listActive(branchId)) {
            if (member.getUserId() == null || member.getUserId() <= 0) {
                continue;
            }
            MemberNoticeTargetView view = new MemberNoticeTargetView();
            view.setMemberId(member.getId());
            view.setMemberName(member.getMemberName());
            view.setUserId(member.getUserId());
            targets.add(view);
        }
        return targets;
    }

    @Override
    public MemberNoticeTargetView noticeTarget(Long memberId) {
        if (memberId == null || memberId <= 0) {
            return null;
        }
        return partyMemberRepository.findById(memberId)
                .filter(member -> member.getUserId() != null && member.getUserId() > 0)
                .map(member -> {
                    MemberNoticeTargetView view = new MemberNoticeTargetView();
                    view.setMemberId(member.getId());
                    view.setMemberName(member.getMemberName());
                    view.setUserId(member.getUserId());
                    return view;
                }).orElse(null);
    }

    @Override
    public Long branchIdOf(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        for (PartyMember member : partyMemberRepository.listActive(null)) {
            if (userId.equals(member.getUserId())) {
                return member.getBranchId();
            }
        }
        return null;
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
        member.setBirthDate(command.getBirthDate());
        member.setEducation(StringUtils.hasText(command.getEducation()) ? command.getEducation() : null);
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

    /**
     * 初始化分桶（全部置 0）。
     *
     * @param keys 桶键
     * @return 有序分桶
     */
    private Map<String, Long> emptyBuckets(List<String> keys) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        for (String key : keys) {
            buckets.put(key, 0L);
        }
        return buckets;
    }

    /**
     * 分桶计数 +1。
     *
     * @param buckets 分桶
     * @param key 桶键
     */
    private void bump(Map<String, Long> buckets, String key) {
        buckets.merge(key, 1L, Long::sum);
    }

    /**
     * 年龄分桶（-1 表示出生日期为空）。
     *
     * @param age 周岁
     * @return 桶键
     */
    private String ageBucket(long age) {
        if (age < 0) {
            return "UNKNOWN";
        }
        if (age < 30) {
            return "UNDER_30";
        }
        if (age < 40) {
            return "30_39";
        }
        if (age < 50) {
            return "40_49";
        }
        if (age < 60) {
            return "50_59";
        }
        return "60_PLUS";
    }

    /**
     * 党龄分桶（入党日期为空归入未知）。
     *
     * @param member 党员档案
     * @param asOf 计算基准日
     * @return 桶键
     */
    private String partyAgeBucket(PartyMember member, LocalDate asOf) {
        if (member.getJoinPartyDate() == null) {
            return "UNKNOWN";
        }
        long years = member.partyAgeYears(asOf);
        if (years < 5) {
            return "UNDER_5";
        }
        if (years <= 10) {
            return "5_10";
        }
        if (years <= 20) {
            return "11_20";
        }
        if (years <= 30) {
            return "21_30";
        }
        return "31_PLUS";
    }

    /**
     * 应转正日期（入党日期满一年）。
     *
     * @param member 党员档案
     * @return 应转正日期；入党日期为空返回 null
     */
    private LocalDate probationDueDate(PartyMember member) {
        return member.getJoinPartyDate() == null ? null : member.getJoinPartyDate().plusYears(1);
    }
}
