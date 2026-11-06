package com.bluewhale.party.union.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.application.command.ActivitySaveCommand;
import com.bluewhale.party.union.application.service.UnionActivityAppService;
import com.bluewhale.party.union.application.view.UnionActivityView;
import com.bluewhale.party.union.application.view.UnionSignupView;
import com.bluewhale.party.union.domain.model.SignupStatus;
import com.bluewhale.party.union.domain.model.UnionActivity;
import com.bluewhale.party.union.domain.model.UnionActivityQuery;
import com.bluewhale.party.union.domain.model.UnionActivityStatus;
import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionSignup;
import com.bluewhale.party.union.domain.repository.UnionActivityRepository;
import com.bluewhale.party.union.domain.repository.UnionMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工会活动应用服务实现。
 *
 * <p>报名与取消都按「活动 + 当前用户在册会员」定位同一行记录：
 * 取消后重报复用原行，名单不会出现同一人的多条记录；名额判定只统计
 * {@code SIGNED}，取消过的人不占名额。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UnionActivityAppServiceImpl implements UnionActivityAppService {

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    private final UnionActivityRepository unionActivityRepository;

    private final UnionMemberRepository unionMemberRepository;

    private final PartySequenceService sequenceService;

    private final PartyProperties properties;

    @Override
    public PageResult<UnionActivityView> page(UnionActivityQuery query, Long current, Long size, PartyActor actor) {
        assertGate();
        UnionActivityQuery safe = query == null ? new UnionActivityQuery() : query;
        long pageSize = normalizeSize(size);
        long pageCurrent = normalizeCurrent(current);
        List<UnionActivity> activities = unionActivityRepository.page(safe,
                (pageCurrent - 1L) * pageSize, (int) pageSize);
        return PageResult.of(activities.stream().map(UnionActivityView::from).toList(),
                unionActivityRepository.count(safe), pageCurrent, pageSize);
    }

    @Override
    public UnionActivityView detail(Long id, PartyActor actor) {
        assertGate();
        UnionActivity activity = requireActivity(id);
        UnionActivityView view = UnionActivityView.from(activity);
        view.setSignupCount(unionActivityRepository.countSigned(id));
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionActivityView create(ActivitySaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionActivity activity = new UnionActivity();
        activity.setActivityNo(sequenceService.nextUnionActivityNo());
        activity.setStatus(UnionActivityStatus.DRAFT);
        apply(activity, command);
        activity.validate();
        unionActivityRepository.save(activity);
        return UnionActivityView.from(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionActivityView update(Long id, ActivitySaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionActivity activity = requireActivity(id);
        if (!activity.isEditable()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT,
                    "只有草稿状态的活动可以编辑");
        }
        apply(activity, command);
        activity.validate();
        unionActivityRepository.save(activity);
        return UnionActivityView.from(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionActivityView publish(Long id, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionActivity activity = requireActivity(id);
        activity.publish();
        unionActivityRepository.save(activity);
        return signedCountView(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionActivityView close(Long id, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionActivity activity = requireActivity(id);
        activity.close();
        unionActivityRepository.save(activity);
        return signedCountView(activity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionActivityView cancel(Long id, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionActivity activity = requireActivity(id);
        activity.cancel();
        unionActivityRepository.save(activity);
        return signedCountView(activity);
    }

    @Override
    public PageResult<UnionSignupView> signups(Long id, Long current, Long size, PartyActor actor) {
        assertGate();
        UnionActivity activity = requireActivity(id);
        long pageSize = normalizeSize(size);
        long pageCurrent = normalizeCurrent(current);
        List<UnionSignup> signups = unionActivityRepository.pageSignups(id,
                (pageCurrent - 1L) * pageSize, (int) pageSize);
        Map<Long, String> names = memberNames(signups);
        List<UnionSignupView> views = signups.stream()
                .map(signup -> UnionSignupView.from(signup, activity.getTitle(),
                        names.get(signup.getUnionMemberId())))
                .toList();
        return PageResult.of(views, unionActivityRepository.countSignups(id), pageCurrent, pageSize);
    }

    @Override
    public PageResult<UnionActivityView> portalPage(UnionActivityQuery query, Long current, Long size,
                                                    PartyActor actor) {
        assertGate();
        UnionActivityQuery safe = query == null ? new UnionActivityQuery() : query;
        long pageSize = normalizeSize(size);
        long pageCurrent = normalizeCurrent(current);
        List<UnionActivity> activities = unionActivityRepository.portalPage(safe,
                (pageCurrent - 1L) * pageSize, (int) pageSize);
        return PageResult.of(activities.stream().map(UnionActivityView::from).toList(),
                unionActivityRepository.portalCount(safe), pageCurrent, pageSize);
    }

    @Override
    public UnionActivityView portalDetail(Long id, PartyActor actor) {
        assertGate();
        UnionActivity activity = requireActivity(id);
        if (!activity.isPublished() && !actor.canManageUnion()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_NOT_FOUND);
        }
        UnionActivityView view = UnionActivityView.from(activity);
        view.setSignupCount(unionActivityRepository.countSigned(id));
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionSignupView signup(Long id, PartyActor actor) {
        assertGate();
        UnionActivity activity = requireActivity(id);
        assertOpenForSignup(activity);
        Long userId = actor.requireUserId();
        UnionMember member = requireActiveMember(userId);
        UnionSignup existing = unionActivityRepository
                .findSignup(activity.getId(), member.getId()).orElse(null);
        if (existing != null && existing.isSigned()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_SIGNUP_DUPLICATED);
        }
        assertQuotaAvailable(activity);
        UnionSignup signup = existing;
        if (signup == null) {
            signup = new UnionSignup();
            signup.setActivityId(activity.getId());
            signup.setUnionMemberId(member.getId());
            signup.setUserId(userId);
            signup.setStatus(SignupStatus.SIGNED);
            signup.setSignupAt(LocalDateTime.now());
        } else {
            // 复用过取消行：既保证唯一性，也保留原记录的创建时间。
            signup.setUserId(userId);
            signup.resign();
        }
        unionActivityRepository.saveSignup(signup);
        return UnionSignupView.from(signup, activity.getTitle(), member.getMemberName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionSignupView cancelSignup(Long id, PartyActor actor) {
        assertGate();
        UnionActivity activity = requireActivity(id);
        Long userId = actor.requireUserId();
        Long memberId = unionMemberRepository.findActiveByUserId(userId)
                .map(UnionMember::getId).orElse(null);
        UnionSignup signup = unionActivityRepository.findSignup(activity.getId(), memberId)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.UNION_SIGNUP_NOT_FOUND));
        if (signup.getStatus() == null || signup.getStatus().isCancelled()) {
            return UnionSignupView.from(signup, activity.getTitle(), memberName(signup.getUnionMemberId()));
        }
        signup.cancel();
        unionActivityRepository.saveSignup(signup);
        return UnionSignupView.from(signup, activity.getTitle(), memberName(signup.getUnionMemberId()));
    }

    @Override
    public PageResult<UnionSignupView> my(Long current, Long size, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        long pageSize = normalizeSize(size);
        long pageCurrent = normalizeCurrent(current);
        List<UnionSignup> signups = unionActivityRepository.pageMySignups(userId,
                (pageCurrent - 1L) * pageSize, (int) pageSize);
        Map<Long, String> titles = activityTitles(signups);
        Map<Long, String> names = memberNames(signups);
        List<UnionSignupView> views = signups.stream()
                .map(signup -> UnionSignupView.from(signup,
                        titles.get(signup.getActivityId()), names.get(signup.getUnionMemberId())))
                .toList();
        return PageResult.of(views, unionActivityRepository.countMySignups(userId), pageCurrent, pageSize);
    }

    /**
     * 应用保存命令中的可写字段。
     *
     * @param activity 活动
     * @param command 保存命令
     */
    private void apply(UnionActivity activity, ActivitySaveCommand command) {
        activity.setTitle(command.getTitle());
        activity.setActivityType(StringUtils.hasText(command.getActivityType())
                ? command.getActivityType().trim() : null);
        activity.setStartTime(command.getStartTime());
        activity.setEndTime(command.getEndTime());
        activity.setLocation(StringUtils.hasText(command.getLocation())
                ? command.getLocation().trim() : null);
        activity.setQuota(command.getQuota());
        activity.setRemark(StringUtils.hasText(command.getRemark()) ? command.getRemark().trim() : null);
    }

    /**
     * 校验活动处于可报名窗口。
     *
     * @param activity 活动
     */
    private void assertOpenForSignup(UnionActivity activity) {
        if (!activity.isPublished()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT,
                    "只有已发布的活动可以报名");
        }
        // 用「开始时间已到」而非「结束时间已到」作为截止点：活动开始后现场签到，线上报名不再有效。
        if (activity.getStartTime() == null || !activity.getStartTime().isAfter(LocalDateTime.now())) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_STATE_CONFLICT,
                    "活动已开始，不能再报名");
        }
    }

    /**
     * 校验名额未满（0 表示不限）。
     *
     * @param activity 活动
     */
    private void assertQuotaAvailable(UnionActivity activity) {
        Integer quota = activity.getQuota();
        if (quota == null || quota <= 0) {
            return;
        }
        if (unionActivityRepository.countSigned(activity.getId()) >= quota) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_SIGNUP_QUOTA_EXCEEDED);
        }
    }

    /**
     * 当前用户必须有在册会籍才能报名。
     *
     * @param userId 用户 ID
     * @return 会员
     */
    private UnionMember requireActiveMember(Long userId) {
        return unionMemberRepository.findActiveByUserId(userId)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_NOT_FOUND));
    }

    /**
     * 取活动并回填报名人数。
     *
     * @param activity 活动
     * @return 视图
     */
    private UnionActivityView signedCountView(UnionActivity activity) {
        UnionActivityView view = UnionActivityView.from(activity);
        view.setSignupCount(unionActivityRepository.countSigned(activity.getId()));
        return view;
    }

    /**
     * 我的报名活动标题索引（一次批量查，避免逐条 N+1）。
     *
     * @param signups 报名列表
     * @return 活动 ID → 名称
     */
    private Map<Long, String> activityTitles(List<UnionSignup> signups) {
        Set<Long> ids = new LinkedHashSet<>();
        for (UnionSignup signup : signups) {
            if (signup.getActivityId() != null) {
                ids.add(signup.getActivityId());
            }
        }
        Map<Long, String> titles = new HashMap<>();
        for (UnionActivity activity : unionActivityRepository.findByIds(ids)) {
            titles.put(activity.getId(), activity.getTitle());
        }
        return titles;
    }

    /**
     * 报名会员姓名索引。
     *
     * @param signups 报名列表
     * @return 会员 ID → 姓名
     */
    private Map<Long, String> memberNames(List<UnionSignup> signups) {
        Set<Long> ids = new LinkedHashSet<>();
        for (UnionSignup signup : signups) {
            if (signup.getUnionMemberId() != null) {
                ids.add(signup.getUnionMemberId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        for (Long id : ids) {
            names.put(id, memberName(id));
        }
        return names;
    }

    /**
     * 查询单个会员姓名。
     *
     * @param memberId 会员 ID
     * @return 姓名（不存在为 null）
     */
    private String memberName(Long memberId) {
        return unionMemberRepository.findById(memberId).map(UnionMember::getMemberName).orElse(null);
    }

    /**
     * 按 ID 取活动，不存在即抛错。
     *
     * @param id 活动 ID
     * @return 活动
     */
    private UnionActivity requireActivity(Long id) {
        if (id == null || id <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_NOT_FOUND);
        }
        return unionActivityRepository.findById(id)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.UNION_ACTIVITY_NOT_FOUND));
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
     * 归一页码。
     *
     * @param current 请求页码
     * @return 合法页码
     */
    private long normalizeCurrent(Long current) {
        return current == null || current < 1L ? 1L : current;
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
