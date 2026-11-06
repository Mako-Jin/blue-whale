package com.bluewhale.party.activity.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.activity.application.command.ActivitySaveCommand;
import com.bluewhale.party.activity.application.command.ActivitySigninCommand;
import com.bluewhale.party.activity.application.service.ActivityAppService;
import com.bluewhale.party.activity.application.view.ActivityView;
import com.bluewhale.party.activity.domain.model.ActivitySignin;
import com.bluewhale.party.activity.domain.model.ActivityType;
import com.bluewhale.party.activity.domain.model.PartyActivity;
import com.bluewhale.party.activity.domain.model.PartyActivityQuery;
import com.bluewhale.party.activity.domain.repository.PartyActivityRepository;
import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.repository.BranchRepository;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.member.domain.model.PartyMember;
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
 * 组织生活应用服务实现。
 *
 * <p>签到只允许记录<b>本支部党员</b>：跨支部签到会让「本支部组织生活到会率」被外单位的人稀释，
 * 也会让流动党员在两个支部同时出现在签到表里。上级党组织列席属于「应到之外」的情形，
 * 记在活动纪要里，不进签到明细。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ActivityAppServiceImpl implements ActivityAppService {

    private final PartyActivityRepository partyActivityRepository;

    private final BranchRepository branchRepository;

    private final PartyMemberRepository partyMemberRepository;

    private final PartyProperties properties;

    @Override
    public PageResult<ActivityView> page(PartyActivityQuery query, PartyActor actor) {
        assertGate();
        PartyActivityQuery safe = query == null ? new PartyActivityQuery() : query;
        long size = normalizeSize(safe.getSize());
        long current = safe.getCurrent() == null || safe.getCurrent() < 1L ? 1L : safe.getCurrent();
        List<PartyActivity> activities = partyActivityRepository.page(safe, (current - 1L) * size, (int) size);
        Map<Long, String> branchNames = branchNameIndex();
        List<ActivityView> views = activities.stream().map(activity -> {
            ActivityView view = ActivityView.from(activity);
            view.setBranchName(branchNames.get(activity.getBranchId()));
            view.setPresentCount(partyActivityRepository.countPresent(activity.getId()));
            return view;
        }).toList();
        return PageResult.of(views, partyActivityRepository.count(safe), current, size);
    }

    @Override
    public ActivityView detail(Long id, PartyActor actor) {
        assertGate();
        PartyActivity activity = requireActivity(id);
        ActivityView view = ActivityView.from(activity);
        view.setBranchName(branchRepository.findById(activity.getBranchId())
                .map(Branch::getBranchName).orElse(null));
        List<ActivitySignin> signins = partyActivityRepository.listSignins(id);
        view.setSignins(signins.stream().map(ActivityView.SigninView::from).toList());
        view.setPresentCount(signins.stream().filter(ActivitySignin::isPresent).count());
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityView create(ActivitySaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        requireActiveBranch(command.getBranchId());
        PartyActivity activity = new PartyActivity();
        apply(activity, command);
        activity.validate();
        partyActivityRepository.save(activity);
        return detail(activity.getId(), actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityView update(Long id, ActivitySaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        PartyActivity activity = requireActivity(id);
        activity.assertPlanEditable();
        if (!activity.getBranchId().equals(command.getBranchId())
                && !partyActivityRepository.listSignins(id).isEmpty()) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT,
                    "已有签到记录，不能更换所属支部");
        }
        if (!activity.getBranchId().equals(command.getBranchId())) {
            requireActiveBranch(command.getBranchId());
        }
        apply(activity, command);
        activity.validate();
        partyActivityRepository.save(activity);
        return detail(id, actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityView complete(Long id, LocalDate heldDate, String summary, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        PartyActivity activity = requireActivity(id);
        activity.complete(heldDate, summary);
        activity.validate();
        partyActivityRepository.save(activity);
        return detail(id, actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityView archive(Long id, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        PartyActivity activity = requireActivity(id);
        activity.archive();
        partyActivityRepository.save(activity);
        return detail(id, actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivityView signin(Long id, ActivitySigninCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        PartyActivity activity = requireActivity(id);
        activity.assertSignable();
        for (ActivitySigninCommand.SigninItem item : command.getItems()) {
            PartyMember member = requireBranchMember(item.getMemberId(), activity.getBranchId());
            ActivitySignin signin = new ActivitySignin();
            signin.setActivityId(id);
            signin.setMemberId(member.getId());
            signin.setMemberName(member.getMemberName());
            if (Boolean.TRUE.equals(item.getPresent())) {
                signin.markPresent();
            } else {
                signin.markAbsent(item.getAbsentReason());
            }
            partyActivityRepository.saveSignin(signin);
        }
        return detail(id, actor);
    }

    /**
     * 校验党员存在且属于该支部。
     *
     * @param memberId 党员档案 ID
     * @param branchId 支部 ID
     * @return 党员档案
     */
    private PartyMember requireBranchMember(Long memberId, Long branchId) {
        PartyMember member = partyMemberRepository.findById(memberId)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.MEMBER_NOT_FOUND));
        if (!branchId.equals(member.getBranchId())) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_STATE_CONFLICT,
                    "党员「" + member.getMemberName() + "」不属于本次组织生活的支部，不能签到");
        }
        return member;
    }

    /**
     * 应用保存命令中的可写字段。
     *
     * @param activity 组织生活
     * @param command 保存命令
     */
    private void apply(PartyActivity activity, ActivitySaveCommand command) {
        activity.setBranchId(command.getBranchId());
        activity.setActivityType(ActivityType.parse(command.getActivityType()));
        activity.setTitle(command.getTitle());
        activity.setPlannedDate(command.getPlannedDate());
        activity.setLocation(StringUtils.hasText(command.getLocation()) ? command.getLocation().trim() : null);
        activity.setHostName(StringUtils.hasText(command.getHostName()) ? command.getHostName().trim() : null);
        activity.setExpectedCount(command.getExpectedCount());
        activity.setSummary(StringUtils.hasText(command.getSummary()) ? command.getSummary().trim() : null);
        activity.setRemark(StringUtils.hasText(command.getRemark()) ? command.getRemark().trim() : null);
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
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_INACTIVE, "已停用支部不能新建组织生活");
        }
        return branch;
    }

    /**
     * 按 ID 取组织生活，不存在即抛错。
     *
     * @param id 组织生活 ID
     * @return 组织生活
     */
    private PartyActivity requireActivity(Long id) {
        if (id == null || id <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.ACTIVITY_NOT_FOUND);
        }
        return partyActivityRepository.findById(id)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.ACTIVITY_NOT_FOUND));
    }

    /**
     * 支部 ID → 名称索引。
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
        return Math.min(value, properties.getActivityPageLimit());
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
