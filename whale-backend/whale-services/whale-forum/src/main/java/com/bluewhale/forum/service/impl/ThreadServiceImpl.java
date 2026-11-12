package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.common.RichTextSanitizer;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ThreadCreateRequest;
import com.bluewhale.forum.dto.ThreadDetailVO;
import com.bluewhale.forum.dto.ThreadListItemVO;
import com.bluewhale.forum.dto.ThreadQuery;
import com.bluewhale.forum.dto.ThreadUpdateRequest;
import com.bluewhale.forum.entity.ForumBoard;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.entity.ForumThreadEditHistory;
import com.bluewhale.forum.mapper.ForumThreadEditHistoryMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.BoardService;
import com.bluewhale.forum.service.ThreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 帖子服务实现。
 *
 * <p>几条不能省的硬规则（都在本类落地，别在 controller 复制）：</p>
 * <ul>
 *   <li>标题/正文上限来自配置，不写死在注解里；</li>
 *   <li>正文一律服务端消毒（{@link RichTextSanitizer}），前端过滤只算优化；</li>
 *   <li>审核版块发帖直接 {@code PENDING}，编辑后退回 {@code PENDING} 并留原文快照；</li>
 *   <li>软删前先改写 {@code thread_no} 释放唯一键，否则同编号帖子无法重建；</li>
 *   <li>楼层号 1 楼固定是楼主，正文存 {@code fm_thread.content}，<b>不落 fm_post 行</b>，
 *       回复从 2 楼起（{@code next_floor} 初始为 2）。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ThreadServiceImpl implements ThreadService {

    /** 已发布状态 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 待审状态 */
    private static final String STATUS_PENDING = "PENDING";

    /** 已解决状态 */
    private static final String STATUS_SOLVED = "SOLVED";

    /** 已关闭状态 */
    private static final String STATUS_CLOSED = "CLOSED";

    /** 已删除状态 */
    private static final String STATUS_DELETED = "DELETED";

    /** 帖子编号日期段格式 */
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 列表可见状态（草稿/待审/驳回不进列表） */
    private static final List<String> VISIBLE_STATUSES = List.of(STATUS_PUBLISHED, STATUS_SOLVED, STATUS_CLOSED);

    /** 排序：最新 */
    private static final String SORT_LATEST = "latest";

    private final ForumThreadMapper threadMapper;

    private final ForumThreadEditHistoryMapper editHistoryMapper;

    private final BoardService boardService;

    private final ForumProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThreadDetailVO create(ThreadCreateRequest request, ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        ForumBoard board = boardService.requirePostableBoard(request.getBoardId(), actor);
        String title = validateTitle(request.getTitle());
        String content = validateContent(request.getContent());
        assertPostRate(actor.userId());

        boolean auditRequired = board.getAuditMode() != null && board.getAuditMode() == 1;
        ForumThread thread = new ForumThread();
        thread.setId(IdWorker.getId());
        thread.setTenantId(tenantId);
        thread.setBoardId(board.getId());
        thread.setThreadNo(nextThreadNo(thread.getId()));
        thread.setThreadType(request.getThreadType() == null ? "NORMAL" : request.getThreadType());
        thread.setTitle(title);
        thread.setContent(content);
        thread.setSummary(RichTextSanitizer.summarize(content, properties.getSummaryLength()));
        thread.setTags(request.getTags());
        thread.setAuthorId(actor.userId());
        thread.setAnonymous(0);
        thread.setStatus(auditRequired ? STATUS_PENDING : STATUS_PUBLISHED);
        thread.setPinned(0);
        thread.setEssence(0);
        thread.setViewCount(0);
        thread.setReplyCount(0);
        thread.setLikeCount(0);
        thread.setCollectCount(0);
        // 1 楼固定为楼主（正文在 content），回复从 2 楼起——这样「N 楼」与前端展示一致
        thread.setNextFloor(2);
        thread.setPublishTime(auditRequired ? null : LocalDateTime.now());
        try {
            threadMapper.insert(thread);
        } catch (DuplicateKeyException e) {
            // 帖子编号撞车（同日同尾号）：换编号重试一次，仍失败按系统错误返回
            thread.setThreadNo(nextThreadNo(IdWorker.getId()));
            threadMapper.insert(thread);
        }
        return toDetailVO(thread, board, actor);
    }

    @Override
    public PageResult<ThreadListItemVO> page(ThreadQuery query, ForumActor actor) {
        TenantContext.requireId();
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = normalizeSize(query.getSize());
        List<Long> boardIds;
        if (query.getBoardId() != null) {
            ForumBoard board = boardService.requireVisibleBoard(query.getBoardId(), actor);
            boardIds = List.of(board.getId());
        } else {
            boardIds = boardService.listVisible(actor).stream().map(vo -> vo.getId()).toList();
            if (boardIds.isEmpty()) {
                return PageResult.of(List.of(), 0, current, size);
            }
        }
        boolean hot = !SORT_LATEST.equalsIgnoreCase(query.getSort()) && !"essence".equalsIgnoreCase(query.getSort());
        IPage<ForumThread> page = threadMapper.selectPage(new Page<>(Math.min(current, properties.getMaxPageNumber()), size),
                Wrappers.<ForumThread>lambdaQuery()
                        .in(ForumThread::getBoardId, boardIds)
                        .in(ForumThread::getStatus, VISIBLE_STATUSES)
                        .eq(Boolean.TRUE.equals(query.getEssenceOnly()), ForumThread::getEssence, 1)
                        .like(query.getKeyword() != null && !query.getKeyword().isBlank(),
                                ForumThread::getTitle, query.getKeyword())
                        .orderByDesc(ForumThread::getPinned)
                        .orderByDesc(hot, ForumThread::getLastReplyTime)
                        .orderByDesc(!hot, ForumThread::getPublishTime));
        List<ThreadListItemVO> records = new ArrayList<>(page.getRecords().size());
        for (ForumThread thread : page.getRecords()) {
            records.add(toListVO(thread));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ThreadDetailVO detail(Long id, ForumActor actor) {
        TenantContext.requireId();
        ForumThread thread = requireReadable(id, actor);
        ForumBoard board = boardService.requireVisibleBoard(thread.getBoardId(), actor);
        if (VISIBLE_STATUSES.contains(thread.getStatus())) {
            threadMapper.increaseViewCount(thread.getId());
            thread.setViewCount(thread.getViewCount() == null ? 1 : thread.getViewCount() + 1);
        }
        return toDetailVO(thread, board, actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThreadDetailVO update(Long id, ThreadUpdateRequest request, ForumActor actor) {
        TenantContext.requireId();
        ForumThread thread = requireReadable(id, actor);
        ForumBoard board = boardService.requireVisibleBoard(thread.getBoardId(), actor);
        boolean manager = actor.hasPermission(ForumPermissions.MANAGE_DISPOSE)
                || actor.hasPermission(ForumPermissions.MANAGE_BOARD);
        if (!manager) {
            if (!actor.userId().equals(thread.getAuthorId())) {
                throw new BizException(ForumErrorCodes.NOT_THREAD_OWNER, "只能编辑自己发布的帖子");
            }
            assertEditWindow(thread);
        }
        if (STATUS_PENDING.equals(thread.getStatus()) && !manager) {
            throw new BizException(ForumErrorCodes.THREAD_STATE_ILLEGAL, "帖子审核中，暂不可编辑");
        }
        String newTitle = request.getTitle() == null ? null : validateTitle(request.getTitle());
        String newContent = request.getContent() == null ? null : validateContent(request.getContent());
        if (newTitle == null && newContent == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标题与正文不能同时为空");
        }

        ForumThreadEditHistory history = new ForumThreadEditHistory();
        history.setTenantId(thread.getTenantId());
        history.setThreadId(thread.getId());
        history.setEditorId(actor.userId());
        history.setTitleBefore(thread.getTitle());
        history.setContentBefore(thread.getContent());
        history.setReason(request.getReason());
        history.setCreateTime(LocalDateTime.now());
        editHistoryMapper.insert(history);

        if (newTitle != null) {
            thread.setTitle(newTitle);
        }
        if (newContent != null) {
            thread.setContent(newContent);
            thread.setSummary(RichTextSanitizer.summarize(newContent, properties.getSummaryLength()));
        }
        boolean auditRequired = board.getAuditMode() != null && board.getAuditMode() == 1;
        if (auditRequired && properties.isAuditOnEdit() && !manager
                && !STATUS_PENDING.equals(thread.getStatus())) {
            // 发布态编辑必须退回待审：否则审核通过后改正文即可绕过审核
            thread.setStatus(STATUS_PENDING);
            thread.setPublishTime(null);
        }
        threadMapper.updateById(thread);
        return toDetailVO(thread, board, actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, ForumActor actor) {
        TenantContext.requireId();
        ForumThread thread = requireReadable(id, actor);
        boolean manager = actor.hasPermission(ForumPermissions.MANAGE_DISPOSE)
                || actor.hasPermission(ForumPermissions.MANAGE_BOARD);
        if (!manager && !actor.userId().equals(thread.getAuthorId())) {
            throw new BizException(ForumErrorCodes.NOT_THREAD_OWNER, "只能删除自己发布的帖子");
        }
        // 先改写编号释放唯一键，再逻辑删除（顺序反了会留下永远无法重建的编号）
        threadMapper.renameNoForDelete(thread.getId(), thread.getThreadNo() + "#D" + thread.getId());
        thread.setStatus(STATUS_DELETED);
        threadMapper.deleteById(thread.getId());
    }

    @Override
    public ForumThread requireVisibleThread(Long id, ForumActor actor) {
        TenantContext.requireId();
        return requireReadable(id, actor);
    }

    /**
     * 读取帖子并校验可见性（待审/驳回仅作者与管理者可读）。
     *
     * @param id 帖子ID
     * @param actor 当前操作者
     * @return 帖子实体
     */
    private ForumThread requireReadable(Long id, ForumActor actor) {
        ForumThread thread = id == null ? null : threadMapper.selectById(id);
        if (thread == null) {
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "帖子不存在或已删除");
        }
        boolean manager = actor.hasPermission(ForumPermissions.MANAGE_AUDIT)
                || actor.hasPermission(ForumPermissions.MANAGE_DISPOSE);
        if (!VISIBLE_STATUSES.contains(thread.getStatus()) && !manager
                && !actor.userId().equals(thread.getAuthorId())) {
            // 未发布内容对他人一律按「不存在」返回，不泄露存在性
            throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "帖子不存在或已删除");
        }
        return thread;
    }

    /**
     * 校验标题长度（上限来自配置）。
     *
     * @param raw 原始标题
     * @return 去空白后的标题
     */
    private String validateTitle(String raw) {
        String title = raw == null ? "" : raw.strip();
        if (title.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标题不能为空");
        }
        if (title.length() > properties.getMaxTitleLength()) {
            throw new BizException(ForumErrorCodes.TITLE_TOO_LONG,
                    "标题不超过 " + properties.getMaxTitleLength() + " 字符");
        }
        return title;
    }

    /**
     * 校验并消毒正文。
     *
     * @param raw 原始正文
     * @return 消毒后的正文
     */
    private String validateContent(String raw) {
        String content = RichTextSanitizer.sanitize(raw);
        if (content == null || content.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "正文不能为空");
        }
        if (content.length() > properties.getMaxContentLength()) {
            throw new BizException(ForumErrorCodes.CONTENT_TOO_LONG,
                    "正文不超过 " + properties.getMaxContentLength() + " 字符");
        }
        return content;
    }

    /**
     * 发帖频率限制：最小间隔 + 滑动窗口条数（DB 计数，多实例一致）。
     *
     * @param userId 用户ID
     */
    private void assertPostRate(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Long recent = threadMapper.selectCount(Wrappers.<ForumThread>lambdaQuery()
                .eq(ForumThread::getAuthorId, userId)
                .ge(ForumThread::getCreateTime, now.minusSeconds(properties.getPostIntervalSeconds())));
        if (recent != null && recent > 0) {
            throw new BizException(ForumErrorCodes.RATE_LIMITED,
                    "发帖过于频繁，请 " + properties.getPostIntervalSeconds() + " 秒后再试");
        }
        if (properties.getPostWindowMax() > 0) {
            Long inWindow = threadMapper.selectCount(Wrappers.<ForumThread>lambdaQuery()
                    .eq(ForumThread::getAuthorId, userId)
                    .ge(ForumThread::getCreateTime, now.minusSeconds(properties.getPostWindowSeconds())));
            if (inWindow != null && inWindow >= properties.getPostWindowMax()) {
                throw new BizException(ForumErrorCodes.RATE_LIMITED,
                        "发帖过于频繁，" + (properties.getPostWindowSeconds() / 60) + " 分钟内最多 "
                                + properties.getPostWindowMax() + " 帖");
            }
        }
    }

    /**
     * 校验作者编辑时间窗。
     *
     * @param thread 帖子实体
     */
    private void assertEditWindow(ForumThread thread) {
        LocalDateTime base = thread.getCreateTime();
        if (base != null && base.plusMinutes(properties.getEditWindowMinutes()).isBefore(LocalDateTime.now())) {
            throw new BizException(ForumErrorCodes.EDIT_WINDOW_EXPIRED,
                    "已超过 " + properties.getEditWindowMinutes() + " 分钟编辑时限，请联系版主");
        }
    }

    /**
     * 生成帖子编号：{@code FM-yyyyMMdd-XXXXX}（尾号取主键后 5 位）。
     *
     * @param id 帖子主键
     * @return 帖子编号
     */
    private String nextThreadNo(Long id) {
        String tail = String.format("%05d", Math.abs(id % 100000));
        return "FM-" + LocalDate.now().format(NO_DATE) + "-" + tail;
    }

    /**
     * 归一化分页条数（越界取上限，防止一次拉全表）。
     *
     * @param size 请求条数
     * @return 生效条数
     */
    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return properties.getPageSizeDefault();
        }
        return Math.min(size, properties.getPageSizeMax());
    }

    /**
     * 实体转列表项视图。
     *
     * @param thread 帖子实体
     * @return 列表项
     */
    private ThreadListItemVO toListVO(ForumThread thread) {
        ThreadListItemVO vo = new ThreadListItemVO();
        fillBase(vo, thread);
        return vo;
    }

    /**
     * 实体转详情视图。
     *
     * @param thread 帖子实体
     * @param board 所属版块
     * @param actor 当前操作者
     * @return 详情
     */
    private ThreadDetailVO toDetailVO(ForumThread thread, ForumBoard board, ForumActor actor) {
        ThreadDetailVO vo = new ThreadDetailVO();
        fillBase(vo, thread);
        vo.setContent(thread.getContent());
        vo.setTags(thread.getTags());
        vo.setNextFloor(thread.getNextFloor());
        vo.setAuditRemark(thread.getAuditRemark());
        vo.setCreateTime(thread.getCreateTime());
        boolean manager = actor.hasPermission(ForumPermissions.MANAGE_DISPOSE)
                || actor.hasPermission(ForumPermissions.MANAGE_BOARD);
        boolean owner = actor.userId().equals(thread.getAuthorId());
        vo.setDeletable(owner || manager);
        boolean withinWindow = thread.getCreateTime() == null
                || thread.getCreateTime().plusMinutes(properties.getEditWindowMinutes()).isAfter(LocalDateTime.now());
        vo.setEditable(manager || (owner && withinWindow));
        return vo;
    }

    /**
     * 填充列表项公共字段。
     *
     * @param vo 目标视图
     * @param thread 帖子实体
     */
    private void fillBase(ThreadListItemVO vo, ForumThread thread) {
        vo.setId(thread.getId());
        vo.setBoardId(thread.getBoardId());
        vo.setThreadNo(thread.getThreadNo());
        vo.setThreadType(thread.getThreadType());
        vo.setTitle(thread.getTitle());
        vo.setSummary(thread.getSummary());
        vo.setAuthorId(thread.getAuthorId());
        vo.setAnonymous(thread.getAnonymous());
        vo.setAnonymousNo(thread.getAnonymousNo());
        vo.setStatus(thread.getStatus());
        vo.setPinned(thread.getPinned());
        vo.setEssence(thread.getEssence());
        vo.setReplyCount(thread.getReplyCount());
        vo.setViewCount(thread.getViewCount());
        vo.setLikeCount(thread.getLikeCount());
        vo.setLastReplyTime(thread.getLastReplyTime());
        vo.setPublishTime(thread.getPublishTime());
    }
}
