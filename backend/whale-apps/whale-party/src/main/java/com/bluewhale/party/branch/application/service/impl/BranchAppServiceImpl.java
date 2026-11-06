package com.bluewhale.party.branch.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.branch.application.command.BranchSaveCommand;
import com.bluewhale.party.branch.application.service.BranchAppService;
import com.bluewhale.party.branch.application.view.BranchTreeView;
import com.bluewhale.party.branch.application.view.BranchView;
import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.model.BranchQuery;
import com.bluewhale.party.branch.domain.model.BranchStatus;
import com.bluewhale.party.branch.domain.repository.BranchRepository;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.member.domain.repository.PartyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 支部台账应用服务实现。
 *
 * <p>层级维护的三条硬约束都在这里：<b>上级必须存在且在册</b>、<b>上级链不得成环</b>、
 * <b>层级不得超过配置上限</b>。三者都需要查库，因此不放领域模型，但一旦通过就写死
 * {@code branch_level}，后续所有按层级排序与统计都读这一列，不再递归计算。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class BranchAppServiceImpl implements BranchAppService {

    private final BranchRepository branchRepository;

    private final PartyMemberRepository memberRepository;

    private final PartySequenceService sequenceService;

    private final PartyProperties properties;

    @Override
    public PageResult<BranchView> page(BranchQuery query, PartyActor actor) {
        assertGate();
        BranchQuery safe = query == null ? new BranchQuery() : query;
        long size = normalizeSize(safe.getSize());
        long current = safe.getCurrent() == null || safe.getCurrent() < 1L ? 1L : safe.getCurrent();
        List<Branch> branches = branchRepository.page(safe, (current - 1L) * size, (int) size);
        Map<Long, String> nameById = nameIndex();
        List<BranchView> views = new ArrayList<>(branches.size());
        for (Branch branch : branches) {
            BranchView view = BranchView.from(branch);
            view.setParentName(nameById.get(branch.getParentId()));
            views.add(view);
        }
        return PageResult.of(views, branchRepository.count(safe), current, size);
    }

    @Override
    public List<BranchTreeView> tree(PartyActor actor) {
        assertGate();
        List<Branch> branches = branchRepository.listAll();
        Map<Long, BranchTreeView> nodes = new LinkedHashMap<>();
        for (Branch branch : branches) {
            nodes.put(branch.getId(), toTreeNode(branch));
        }
        List<BranchTreeView> roots = new ArrayList<>();
        for (Branch branch : branches) {
            BranchTreeView node = nodes.get(branch.getId());
            BranchTreeView parent = branch.getParentId() == null ? null : nodes.get(branch.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public BranchView detail(Long id, PartyActor actor) {
        assertGate();
        Branch branch = requireBranch(id);
        BranchView view = BranchView.from(branch);
        if (branch.getParentId() != null && branch.getParentId() > Branch.ROOT_PARENT_ID) {
            view.setParentName(branchRepository.findById(branch.getParentId())
                    .map(Branch::getBranchName).orElse(null));
        }
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchView create(BranchSaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        Branch branch = new Branch();
        branch.setBranchCode(sequenceService.nextBranchCode());
        branch.setBranchLevel(resolveLevel(command.getParentId(), null));
        branch.setStatus(BranchStatus.ACTIVE);
        applyCommand(branch, command);
        branch.validate();
        if (branchRepository.findByCode(branch.getBranchCode()).isPresent()) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_CODE_CONFLICT);
        }
        branchRepository.save(branch);
        return BranchView.from(branch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchView update(Long id, BranchSaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        Branch branch = requireBranch(id);
        Long newParentId = command.getParentId() == null ? Branch.ROOT_PARENT_ID : command.getParentId();
        Long oldParentId = branch.getParentId() == null ? Branch.ROOT_PARENT_ID : branch.getParentId();
        int oldLevel = branch.getBranchLevel() == null ? 1 : branch.getBranchLevel();
        int newLevel = oldLevel;
        if (!oldParentId.equals(newParentId)) {
            assertNoCycle(branch.getId(), newParentId);
            newLevel = resolveLevel(newParentId, branch.getId());
            branch.moveTo(newParentId, newLevel);
        }
        applyCommand(branch, command);
        branch.validate();
        branchRepository.save(branch);
        if (newLevel != oldLevel) {
            shiftDescendants(branch.getId(), newLevel - oldLevel, 0);
        }
        return BranchView.from(branch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BranchView changeStatus(Long id, boolean active, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        Branch branch = requireBranch(id);
        if (active) {
            branch.activate();
        } else {
            assertSuspendable(branch);
            branch.suspend();
        }
        branchRepository.save(branch);
        return BranchView.from(branch);
    }

    /**
     * 停用前置校验：不得有下级支部、不得有在册党员。
     *
     * @param branch 支部
     */
    private void assertSuspendable(Branch branch) {
        if (branchRepository.countByParent(branch.getId()) > 0) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_STATE_CONFLICT,
                    "该支部下还有下级支部，请先处理下级支部");
        }
        if (memberRepository.countActiveByBranch(branch.getId()) > 0) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_STATE_CONFLICT,
                    "该支部下还有在册党员，请先办理组织关系转出");
        }
    }

    /**
     * 解析上级支部并计算层级。
     *
     * @param parentId 上级支部 ID
     * @param selfId 自身 ID（新建时为 null）
     * @return 层级
     */
    private int resolveLevel(Long parentId, Long selfId) {
        long parent = parentId == null ? Branch.ROOT_PARENT_ID : parentId;
        if (parent == Branch.ROOT_PARENT_ID) {
            return 1;
        }
        if (selfId != null && selfId.equals(parent)) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_PARENT_CYCLE, "上级支部不能是自身");
        }
        Branch parentBranch = branchRepository.findById(parent)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.BRANCH_NOT_FOUND, "上级支部不存在"));
        if (!parentBranch.isActive()) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_INACTIVE, "上级支部已停用，不能在其下新建或迁移支部");
        }
        int level = (parentBranch.getBranchLevel() == null ? 1 : parentBranch.getBranchLevel()) + 1;
        if (level > properties.getBranchMaxDepth()) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_PARENT_CYCLE,
                    "支部层级超过上限 " + properties.getBranchMaxDepth());
        }
        return level;
    }

    /**
     * 校验上级链不成环：从新上级向上追溯，遇到自身即视为成环。
     *
     * @param selfId 自身支部 ID
     * @param newParentId 新上级支部 ID
     */
    private void assertNoCycle(Long selfId, Long newParentId) {
        long cursor = newParentId == null ? Branch.ROOT_PARENT_ID : newParentId;
        int guard = 0;
        while (cursor != Branch.ROOT_PARENT_ID && guard++ <= properties.getBranchMaxDepth()) {
            if (cursor == selfId.longValue()) {
                throw PartyErrorCode.exception(PartyErrorCode.BRANCH_PARENT_CYCLE,
                        "上级支部不能是自身或其下级支部");
            }
            Optional<Branch> parent = branchRepository.findById(cursor);
            if (parent.isEmpty()) {
                break;
            }
            Long next = parent.get().getParentId();
            cursor = next == null ? Branch.ROOT_PARENT_ID : next;
        }
    }

    /**
     * 级联调整下级支部层级。
     *
     * <p>只改 {@code branch_level} 这一列，不动上级关系；层级是派生值，
     * 与「谁的上级是谁」不一致时以关系为准，因此必须跟着关系一起改。</p>
     *
     * @param parentId 上级支部 ID
     * @param delta 层级增量
     * @param depth 递归深度（防止脏数据成环时无限递归）
     */
    private void shiftDescendants(Long parentId, int delta, int depth) {
        if (delta == 0 || depth > properties.getBranchMaxDepth()) {
            return;
        }
        for (Branch child : branchRepository.listByParent(parentId)) {
            int level = (child.getBranchLevel() == null ? 1 : child.getBranchLevel()) + delta;
            child.setBranchLevel(Math.max(level, 1));
            child.validate();
            branchRepository.save(child);
            shiftDescendants(child.getId(), delta, depth + 1);
        }
    }

    /**
     * 应用保存命令中的可写字段。
     *
     * @param branch 支部
     * @param command 保存命令
     */
    private void applyCommand(Branch branch, BranchSaveCommand command) {
        branch.setBranchName(command.getBranchName());
        branch.setParentId(command.getParentId() == null ? Branch.ROOT_PARENT_ID : command.getParentId());
        branch.setOrgCode(StringUtils.hasText(command.getOrgCode()) ? command.getOrgCode().trim() : null);
        branch.setSecretaryName(StringUtils.hasText(command.getSecretaryName())
                ? command.getSecretaryName().trim() : null);
        branch.setSecretaryUserId(command.getSecretaryUserId());
        branch.setEstablishedDate(command.getEstablishedDate());
        branch.setSortNo(command.getSortNo() == null ? 0 : command.getSortNo());
        branch.setRemark(StringUtils.hasText(command.getRemark()) ? command.getRemark().trim() : null);
    }

    /**
     * 领域模型转树节点。
     *
     * @param branch 支部
     * @return 树节点
     */
    private BranchTreeView toTreeNode(Branch branch) {
        BranchTreeView node = new BranchTreeView();
        node.setId(branch.getId());
        node.setBranchCode(branch.getBranchCode());
        node.setBranchName(branch.getBranchName());
        node.setParentId(branch.getParentId());
        node.setBranchLevel(branch.getBranchLevel());
        node.setOrgCode(branch.getOrgCode());
        node.setSecretaryName(branch.getSecretaryName());
        BranchStatus status = branch.getStatus() == null ? BranchStatus.SUSPENDED : branch.getStatus();
        node.setStatus(status.name());
        node.setStatusLabel(status.getLabel());
        node.setSortNo(branch.getSortNo());
        return node;
    }

    /**
     * 支部 ID → 名称索引（列表里回填上级名称用）。
     *
     * @return 索引
     */
    private Map<Long, String> nameIndex() {
        Map<Long, String> index = new HashMap<>();
        for (Branch branch : branchRepository.listAll()) {
            index.put(branch.getId(), branch.getBranchName());
        }
        return index;
    }

    /**
     * 按 ID 取支部，不存在即抛错。
     *
     * @param id 支部 ID
     * @return 支部
     */
    private Branch requireBranch(Long id) {
        if (id == null || id <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.BRANCH_NOT_FOUND);
        }
        return branchRepository.findById(id)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.BRANCH_NOT_FOUND));
    }

    /**
     * 归一每页条数。
     *
     * @param size 请求条数
     * @return 合法条数
     */
    private long normalizeSize(Long size) {
        long value = size == null || size < 1L ? 20L : size;
        return Math.min(value, properties.getBranchPageLimit());
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
