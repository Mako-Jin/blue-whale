package com.bluewhale.party.content.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.repository.BranchRepository;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.content.application.command.ContentRejectCommand;
import com.bluewhale.party.content.application.command.ContentSaveCommand;
import com.bluewhale.party.content.application.service.ContentAppService;
import com.bluewhale.party.content.application.view.ContentAuditView;
import com.bluewhale.party.content.application.view.ContentReadView;
import com.bluewhale.party.content.application.view.ContentView;
import com.bluewhale.party.content.domain.model.AuditAction;
import com.bluewhale.party.content.domain.model.ContentAudit;
import com.bluewhale.party.content.domain.model.ContentPortalQuery;
import com.bluewhale.party.content.domain.model.ContentQuery;
import com.bluewhale.party.content.domain.model.ContentRead;
import com.bluewhale.party.content.domain.model.ContentScope;
import com.bluewhale.party.content.domain.model.ContentStatus;
import com.bluewhale.party.content.domain.model.ContentVisibility;
import com.bluewhale.party.content.domain.model.PartyContent;
import com.bluewhale.party.content.domain.repository.ContentRepository;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.repository.PartyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 党建内容应用服务实现（先审后发）。
 *
 * <p>三条硬规则：</p>
 * <ol>
 *   <li><b>未审不可见</b>：非「已发布」内容只有作者与审核人能看，
 *       支部委员即使有列表权限也看不到别人的待审稿；</li>
 *   <li><b>改必重审</b>：已发布内容一经修改即退回待审，不允许编辑直接覆盖线上口径；</li>
 *   <li><b>退回必填意见</b>：退回动作的意见为空直接拒绝，并由领域层兜底。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ContentAppServiceImpl implements ContentAppService {

    /** 默认每页条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    private final ContentRepository contentRepository;

    private final BranchRepository branchRepository;

    private final PartyMemberRepository partyMemberRepository;

    private final PartyProperties properties;

    @Override
    public PageResult<ContentView> page(ContentQuery query, PartyActor actor) {
        assertGate();
        ContentQuery safe = query == null ? new ContentQuery() : query;
        if (!actor.canManageAll()) {
            safe.setScopeAuthorId(actor.requireUserId());
        }
        long size = normalizeSize(safe.getSize());
        long current = safe.getCurrent() == null || safe.getCurrent() < 1L ? 1L : safe.getCurrent();
        List<PartyContent> contents = contentRepository.page(safe, (current - 1L) * size, (int) size);
        List<ContentView> views = contents.stream().map(ContentView::from).toList();
        return PageResult.of(views, contentRepository.count(safe), current, size);
    }

    @Override
    public ContentView detail(Long id, PartyActor actor) {
        assertGate();
        PartyContent content = requireContent(id);
        assertManageDetailVisible(content, actor);
        return detailView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView create(ContentSaveCommand command, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        PartyContent content = new PartyContent();
        content.setAuthorId(userId);
        content.setAuthorName(actor.getUsername());
        content.setStatus(ContentStatus.DRAFT);
        apply(content, command);
        content.validate();
        requireActiveBranches(content);
        contentRepository.save(content);
        return detailView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView update(Long id, ContentSaveCommand command, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        PartyContent content = requireContent(id);
        assertAuthorOrManager(content, userId, actor);
        String fromStatus = content.currentStatus().name();
        boolean resubmitted = content.prepareEdit();
        apply(content, command);
        content.validate();
        requireActiveBranches(content);
        contentRepository.save(content);
        if (resubmitted) {
            saveAudit(content.getId(), AuditAction.EDIT, fromStatus, ContentStatus.PENDING.name(),
                    "已发布内容被修改，自动退回待审", actor);
        }
        return detailView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView submit(Long id, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        PartyContent content = requireContent(id);
        assertAuthorOrManager(content, userId, actor);
        String fromStatus = content.currentStatus().name();
        content.submit();
        contentRepository.save(content);
        saveAudit(id, AuditAction.SUBMIT, fromStatus, ContentStatus.PENDING.name(), null, actor);
        return detailView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView approve(Long id, PartyActor actor) {
        assertGate();
        requireReviewer(actor);
        PartyContent content = requireContent(id);
        String fromStatus = content.currentStatus().name();
        content.approve();
        contentRepository.save(content);
        saveAudit(id, AuditAction.APPROVE, fromStatus, ContentStatus.PUBLISHED.name(), null, actor);
        return detailView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView reject(Long id, ContentRejectCommand command, PartyActor actor) {
        assertGate();
        requireReviewer(actor);
        PartyContent content = requireContent(id);
        String opinion = command == null ? null : command.getOpinion();
        String fromStatus = content.currentStatus().name();
        content.reject(opinion);
        contentRepository.save(content);
        saveAudit(id, AuditAction.REJECT, fromStatus, ContentStatus.DRAFT.name(), opinion.trim(), actor);
        return detailView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView offline(Long id, PartyActor actor) {
        assertGate();
        requireReviewer(actor);
        PartyContent content = requireContent(id);
        String fromStatus = content.currentStatus().name();
        content.offline();
        contentRepository.save(content);
        saveAudit(id, AuditAction.OFFLINE, fromStatus, ContentStatus.OFFLINE.name(), null, actor);
        return detailView(content);
    }

    @Override
    public List<ContentAuditView> audits(Long id, PartyActor actor) {
        assertGate();
        PartyContent content = requireContent(id);
        assertManageDetailVisible(content, actor);
        return contentRepository.listAudits(id).stream().map(ContentAuditView::from).toList();
    }

    @Override
    public PageResult<ContentView> portalPage(ContentPortalQuery query, PartyActor actor) {
        assertGate();
        ContentPortalQuery safe = query == null ? new ContentPortalQuery() : query;
        ContentScope scope = scopeOf(actor);
        long size = normalizeSize(safe.getSize());
        long current = safe.getCurrent() == null || safe.getCurrent() < 1L ? 1L : safe.getCurrent();
        List<PartyContent> contents = contentRepository.portalPage(
                safe, scope, (current - 1L) * size, (int) size);
        List<ContentView> views = contents.stream().map(this::portalView).toList();
        return PageResult.of(views, contentRepository.portalCount(safe, scope), current, size);
    }

    @Override
    public ContentView portalDetail(Long id, PartyActor actor) {
        assertGate();
        PartyContent content = requireContent(id);
        if (!content.isVisibleTo(scopeOf(actor))) {
            // 不区分「不存在」与「不可见」，避免读者通过报错探测未发布内容
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_NOT_FOUND);
        }
        return detailView(content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContentView read(Long id, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        PartyContent content = requireContent(id);
        if (!content.isVisibleTo(scopeOf(actor))) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_NOT_FOUND);
        }
        ContentRead read = new ContentRead();
        read.setContentId(id);
        read.setUserId(userId);
        read.setUserName(actor.getUsername());
        read.setReadAt(LocalDateTime.now());
        boolean firstRead = contentRepository.recordRead(read);
        if (firstRead) {
            contentRepository.incrementReadCount(id);
            content.markRead();
        }
        return detailView(content);
    }

    @Override
    public List<ContentReadView> reads(Long id, PartyActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        PartyContent content = requireContent(id);
        assertAuthorOrManager(content, userId, actor);
        return contentRepository.listReads(id).stream().map(ContentReadView::from).toList();
    }

    /**
     * 应用保存命令中的可写字段。
     *
     * @param content 党建内容
     * @param command 保存命令
     */
    private void apply(PartyContent content, ContentSaveCommand command) {
        content.setCategory(command.getCategory());
        content.setTitle(command.getTitle());
        content.setSummary(StringUtils.hasText(command.getSummary()) ? command.getSummary().trim() : null);
        content.setCoverFileId(command.getCoverFileId());
        content.setBody(command.getBody());
        content.setAttachmentFileIds(command.getAttachmentFileIds());
        content.setVisibility(ContentVisibility.parse(command.getVisibility()));
        content.setBranchIds(command.getBranchIds());
        content.setRemark(StringUtils.hasText(command.getRemark()) ? command.getRemark().trim() : null);
    }

    /**
     * 校验指定支部存在且在册。
     *
     * @param content 党建内容
     */
    private void requireActiveBranches(PartyContent content) {
        if (content.getVisibility() != ContentVisibility.BRANCHES) {
            return;
        }
        for (Long branchId : content.getBranchIds()) {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.CONTENT_BRANCH_INVALID));
            if (!branch.isActive()) {
                throw PartyErrorCode.exception(PartyErrorCode.CONTENT_BRANCH_INVALID,
                        "支部「" + branch.getBranchName() + "」已停用，不能作为内容的可见范围");
            }
        }
    }

    /**
     * 管理详情可见性：未发布内容仅作者与审核人可看。
     *
     * @param content 党建内容
     * @param actor 操作人
     */
    private void assertManageDetailVisible(PartyContent content, PartyActor actor) {
        if (content.currentStatus().isPublished()) {
            return;
        }
        if (!content.isAuthor(actor.getUserId()) && !actor.canManageAll()) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_FORBIDDEN);
        }
    }

    /**
     * 校验作者本人或审核人。
     *
     * @param content 党建内容
     * @param userId 用户 ID
     * @param actor 操作人
     */
    private void assertAuthorOrManager(PartyContent content, Long userId, PartyActor actor) {
        if (!content.isAuthor(userId) && !actor.canManageAll()) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_FORBIDDEN);
        }
    }

    /**
     * 校验审核人身份（党务管理员 / 租户管理员）。
     *
     * @param actor 操作人
     */
    private void requireReviewer(PartyActor actor) {
        if (!actor.canManageAll()) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_FORBIDDEN, "只有党务管理员可以审核内容");
        }
    }

    /**
     * 按 ID 取内容，不存在即抛错。
     *
     * @param id 内容 ID
     * @return 党建内容
     */
    private PartyContent requireContent(Long id) {
        if (id == null || id <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.CONTENT_NOT_FOUND);
        }
        return contentRepository.findById(id)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.CONTENT_NOT_FOUND));
    }

    /**
     * 换算当前读者的可见范围。
     *
     * <p>审核人直接拿全量范围；普通用户按党员档案判定党员身份与所属支部。
     * 已转出的党员不再具备党员身份，但「全员」内容仍可看。</p>
     *
     * @param actor 操作人
     * @return 阅读范围
     */
    private ContentScope scopeOf(PartyActor actor) {
        if (actor == null) {
            return ContentScope.none();
        }
        if (actor.canManageAll()) {
            return ContentScope.manager();
        }
        Long userId = actor.getUserId();
        if (userId == null || userId <= 0) {
            return ContentScope.none();
        }
        PartyMember member = partyMemberRepository.findByUserId(userId).orElse(null);
        if (member == null || member.getStatus() == null || member.getStatus().isReadOnly()) {
            return ContentScope.none();
        }
        List<Long> branchIds = new ArrayList<>();
        if (member.getBranchId() != null && member.getBranchId() > 0) {
            branchIds.add(member.getBranchId());
        }
        return ContentScope.of(true, branchIds);
    }

    /**
     * 写入审核轨迹。
     *
     * @param contentId 内容 ID
     * @param action 动作
     * @param fromStatus 变更前状态
     * @param toStatus 变更后状态
     * @param opinion 意见
     * @param actor 操作人
     */
    private void saveAudit(Long contentId, AuditAction action, String fromStatus, String toStatus,
                           String opinion, PartyActor actor) {
        ContentAudit audit = new ContentAudit();
        audit.setContentId(contentId);
        audit.setAction(action);
        audit.setFromStatus(fromStatus);
        audit.setToStatus(toStatus);
        audit.setOpinion(opinion);
        audit.setOperatorId(actor.getUserId());
        audit.setOperatorName(actor.getUsername());
        contentRepository.saveAudit(audit);
    }

    /**
     * 构建详情视图并回填支部名称。
     *
     * @param content 党建内容
     * @return 视图
     */
    private ContentView detailView(PartyContent content) {
        ContentView view = ContentView.from(content);
        if (!view.getBranchIds().isEmpty()) {
            Map<Long, String> branchNames = branchNameIndex();
            view.setBranchNames(view.getBranchIds().stream()
                    .map(branchNames::get).filter(name -> name != null).toList());
        }
        return view;
    }

    /**
     * 阅读列表视图（不回填支部名称，列表只展示范围标签）。
     *
     * @param content 党建内容
     * @return 视图
     */
    private ContentView portalView(PartyContent content) {
        return ContentView.from(content);
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
        long value = size == null || size < 1L ? DEFAULT_PAGE_SIZE : size;
        return Math.min(value, properties.getContentPageLimit());
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
