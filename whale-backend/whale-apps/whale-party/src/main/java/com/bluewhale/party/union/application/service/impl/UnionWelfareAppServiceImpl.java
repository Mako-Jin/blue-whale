package com.bluewhale.party.union.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.union.application.command.WelfareIssueCommand;
import com.bluewhale.party.union.application.command.WelfareSaveCommand;
import com.bluewhale.party.union.application.service.UnionWelfareAppService;
import com.bluewhale.party.union.application.view.UnionWelfareItemView;
import com.bluewhale.party.union.application.view.UnionWelfareView;
import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionWelfare;
import com.bluewhale.party.union.domain.model.UnionWelfareItem;
import com.bluewhale.party.union.domain.model.UnionWelfareQuery;
import com.bluewhale.party.union.domain.model.WelfareSignStatus;
import com.bluewhale.party.union.domain.model.WelfareStatus;
import com.bluewhale.party.union.domain.repository.UnionMemberRepository;
import com.bluewhale.party.union.domain.repository.UnionWelfareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节日福利应用服务实现。
 *
 * <p>三条口径：</p>
 * <ol>
 *   <li><b>发放幂等</b>：重复发放按已存在明细跳过，不追加重名行——否则同一人会被记两次福利；</li>
 *   <li><b>金额快照</b>：明细金额取发放时批次标准，后续调标不回写历史；</li>
 *   <li><b>关闭不阻断未签收</b>：关闭是对账动作，未签收数在列表里呈现差异，不强行要求先签完。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UnionWelfareAppServiceImpl implements UnionWelfareAppService {

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    private final UnionWelfareRepository unionWelfareRepository;

    private final UnionMemberRepository unionMemberRepository;

    private final PartySequenceService sequenceService;

    private final PartyProperties properties;

    @Override
    public PageResult<UnionWelfareView> page(UnionWelfareQuery query, Long current, Long size, PartyActor actor) {
        assertGate();
        UnionWelfareQuery safe = query == null ? new UnionWelfareQuery() : query;
        long pageSize = normalizeSize(size);
        long pageCurrent = current == null || current < 1L ? 1L : current;
        List<UnionWelfare> welfares = unionWelfareRepository.page(safe,
                (pageCurrent - 1L) * pageSize, (int) pageSize);
        List<UnionWelfareView> views = welfares.stream().map(UnionWelfareView::from).toList();
        return PageResult.of(views, unionWelfareRepository.count(safe), pageCurrent, pageSize);
    }

    @Override
    public UnionWelfareView detail(Long id, PartyActor actor) {
        assertGate();
        return UnionWelfareView.from(requireWelfare(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionWelfareView create(WelfareSaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionWelfare welfare = new UnionWelfare();
        welfare.setWelfareNo(sequenceService.nextUnionWelfareNo());
        welfare.setStatus(WelfareStatus.DRAFT);
        apply(welfare, command);
        welfare.validate();
        unionWelfareRepository.save(welfare);
        return UnionWelfareView.from(welfare);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionWelfareView update(Long id, WelfareSaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionWelfare welfare = requireWelfare(id);
        if (!welfare.isEditable()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_STATE_CONFLICT,
                    "只有草稿状态的福利批次可以编辑");
        }
        apply(welfare, command);
        welfare.validate();
        unionWelfareRepository.save(welfare);
        return UnionWelfareView.from(welfare);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionWelfareView issue(Long id, WelfareIssueCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionWelfare welfare = requireWelfare(id);
        if (welfare.getStatus() == null || !welfare.getStatus().isDraft()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_STATE_CONFLICT,
                    "只有草稿状态的福利批次可以发放");
        }
        List<UnionMember> candidates = resolveCandidates(command);
        Set<Long> existing = existingMemberIds(welfare.getId());
        int issueCount = 0;
        for (UnionMember member : candidates) {
            if (existing.contains(member.getId())) {
                continue;
            }
            unionWelfareRepository.saveItem(newItem(welfare, member));
            issueCount++;
        }
        welfare.issue();
        unionWelfareRepository.save(welfare);
        UnionWelfareView view = UnionWelfareView.from(welfare);
        view.setIssueCount(issueCount);
        return view;
    }

    @Override
    public PageResult<UnionWelfareItemView> items(Long id, String signStatus, Long current, Long size,
                                                  PartyActor actor) {
        assertGate();
        requireWelfare(id);
        String normalized = StringUtils.hasText(signStatus)
                ? WelfareSignStatus.parse(signStatus).name() : null;
        long pageSize = normalizeSize(size);
        long pageCurrent = current == null || current < 1L ? 1L : current;
        List<UnionWelfareItem> items = unionWelfareRepository.pageItems(id, normalized,
                (pageCurrent - 1L) * pageSize, (int) pageSize);
        List<UnionWelfareItemView> views = items.stream().map(UnionWelfareItemView::from).toList();
        return PageResult.of(views, unionWelfareRepository.countItems(id, normalized), pageCurrent, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionWelfareItemView sign(Long itemId, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        UnionWelfareItem item = unionWelfareRepository.findItemById(itemId)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_ITEM_NOT_FOUND));
        UnionWelfare welfare = requireWelfare(item.getWelfareId());
        if (welfare.getStatus() == null || !welfare.getStatus().isIssued()) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_ITEM_STATE_CONFLICT,
                    "只有已发放的福利批次可以签收");
        }
        assertSignable(item, actor);
        if (item.isSigned()) {
            return UnionWelfareItemView.from(item, welfare.getTitle());
        }
        item.sign(userId);
        unionWelfareRepository.saveItem(item);
        return UnionWelfareItemView.from(item, welfare.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnionWelfareView close(Long id, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        UnionWelfare welfare = requireWelfare(id);
        welfare.close();
        unionWelfareRepository.save(welfare);
        return UnionWelfareView.from(welfare);
    }

    @Override
    public PageResult<UnionWelfareItemView> my(Long current, Long size, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        long pageSize = normalizeSize(size);
        long pageCurrent = current == null || current < 1L ? 1L : current;
        List<UnionWelfareItem> items = unionWelfareRepository.pageMyItems(userId,
                (pageCurrent - 1L) * pageSize, (int) pageSize);
        Map<Long, String> titles = welfareTitles(items);
        List<UnionWelfareItemView> views = items.stream()
                .map(item -> UnionWelfareItemView.from(item, titles.get(item.getWelfareId()))).toList();
        return PageResult.of(views, unionWelfareRepository.countMyItems(userId), pageCurrent, pageSize);
    }

    /**
     * 应用保存命令中的可写字段。
     *
     * @param welfare 福利批次
     * @param command 保存命令
     */
    private void apply(UnionWelfare welfare, WelfareSaveCommand command) {
        welfare.setTitle(command.getTitle());
        welfare.setWelfareYear(command.getWelfareYear());
        welfare.setStandardAmount(command.getStandardAmount());
        welfare.setRemark(StringUtils.hasText(command.getRemark()) ? command.getRemark().trim() : null);
    }

    /**
     * 解析本次发放的候选会员。
     *
     * @param command 发放命令
     * @return 候选会员
     */
    private List<UnionMember> resolveCandidates(WelfareIssueCommand command) {
        if (command == null || command.getMemberIds() == null || command.getMemberIds().isEmpty()) {
            return unionMemberRepository.listActive();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : command.getMemberIds()) {
            if (id != null) {
                ids.add(id);
            }
        }
        List<UnionMember> members = new ArrayList<>();
        for (Long id : ids) {
            members.add(requireActiveMember(id));
        }
        return members;
    }

    /**
     * 校验指定会员存在且在册。
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
            throw PartyErrorCode.exception(PartyErrorCode.UNION_MEMBER_STATE_CONFLICT,
                    "已退会会员不能发放节日福利");
        }
        return member;
    }

    /**
     * 已有明细的会员 ID 集合。
     *
     * @param welfareId 批次 ID
     * @return 会员 ID 集合
     */
    private Set<Long> existingMemberIds(Long welfareId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (UnionWelfareItem item : unionWelfareRepository.listItems(welfareId)) {
            ids.add(item.getUnionMemberId());
        }
        return ids;
    }

    /**
     * 构造发放明细（金额取批次标准快照）。
     *
     * @param welfare 福利批次
     * @param member 会员
     * @return 明细
     */
    private UnionWelfareItem newItem(UnionWelfare welfare, UnionMember member) {
        UnionWelfareItem item = new UnionWelfareItem();
        item.setWelfareId(welfare.getId());
        item.setUnionMemberId(member.getId());
        item.setEmployeeNo(member.getEmployeeNo());
        item.setMemberName(member.getMemberName());
        item.setAmount(welfare.getStandardAmount());
        item.setSignStatus(WelfareSignStatus.UNSIGNED);
        return item;
    }

    /**
     * 校验签收权限：工会管理员可代签，其余人只能签本人明细。
     *
     * @param item 明细
     * @param actor 操作人
     */
    private void assertSignable(UnionWelfareItem item, PartyActor actor) {
        if (actor.canManageUnion()) {
            return;
        }
        Long memberId = unionMemberRepository.findActiveByUserId(actor.getUserId())
                .map(UnionMember::getId).orElse(null);
        if (memberId == null || !memberId.equals(item.getUnionMemberId())) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_ITEM_FORBIDDEN);
        }
    }

    /**
     * 我的福利批次标题索引。
     *
     * @param items 明细
     * @return 批次 ID → 标题
     */
    private Map<Long, String> welfareTitles(List<UnionWelfareItem> items) {
        Set<Long> ids = new LinkedHashSet<>();
        for (UnionWelfareItem item : items) {
            if (item.getWelfareId() != null) {
                ids.add(item.getWelfareId());
            }
        }
        Map<Long, String> titles = new HashMap<>();
        for (UnionWelfare welfare : unionWelfareRepository.findByIds(ids)) {
            titles.put(welfare.getId(), welfare.getTitle());
        }
        return titles;
    }

    /**
     * 按 ID 取批次，不存在即抛错。
     *
     * @param id 批次 ID
     * @return 批次
     */
    private UnionWelfare requireWelfare(Long id) {
        if (id == null || id <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_NOT_FOUND);
        }
        return unionWelfareRepository.findById(id)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.UNION_WELFARE_NOT_FOUND));
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
