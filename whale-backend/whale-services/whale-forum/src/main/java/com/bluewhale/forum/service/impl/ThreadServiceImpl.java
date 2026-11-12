package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.bluewhale.forum.common.enums.ReactionType;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ThreadCreateRequest;
import com.bluewhale.forum.dto.ThreadDetailVO;
import com.bluewhale.forum.dto.ThreadListItemVO;
import com.bluewhale.forum.dto.ThreadQuery;
import com.bluewhale.forum.dto.ThreadUpdateRequest;
import com.bluewhale.forum.entity.ForumBoard;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.entity.ForumThreadEditHistory;
import com.bluewhale.forum.entity.ForumThreadTag;
import com.bluewhale.forum.mapper.ForumThreadEditHistoryMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.mapper.ForumThreadTagMapper;
import com.bluewhale.forum.service.BanService;
import com.bluewhale.forum.service.BoardService;
import com.bluewhale.forum.service.ExpService;
import com.bluewhale.forum.service.MentionService;
import com.bluewhale.forum.service.SearchService;
import com.bluewhale.forum.service.SensitiveWordService;
import com.bluewhale.forum.service.ThreadService;
import com.bluewhale.forum.service.ViewCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    private final ForumThreadTagMapper threadTagMapper;

    private final ForumThreadEditHistoryMapper editHistoryMapper;

    private final BoardService boardService;

    private final ForumProperties properties;

    private final SensitiveWordService sensitiveWordService;

    private final BanService banService;

    private final ViewCountService viewCountService;

    private final ReactionStateReader reactionStateReader;

    private final SearchService searchService;

    private final MentionService mentionService;

    private final ExpService expService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThreadDetailVO create(ThreadCreateRequest request, ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        ForumBoard board = boardService.requirePostableBoard(request.getBoardId(), actor);
        banService.assertNotMuted(actor.userId());
        String title = validateTitle(request.getTitle());
        String content = validateContent(request.getContent());
        // 标签先归一化并校验上限：超长标签会撞列宽报 500，必须在落库前拦成 400
        List<String> tags = normalizeTags(request.getTags());
        assertPostRate(actor.userId());

        // 敏感词：BLOCK 直接拒绝（留痕后抛 30031），REPLACE 打码，PENDING 转待审
        SensitiveWordService.ScanResult titleScan = sensitiveWordService.scan(title);
        SensitiveWordService.ScanResult contentScan = sensitiveWordService.scan(content);
        if (titleScan.hit()) {
            sensitiveWordService.recordHit("THREAD", null, actor.userId(), titleScan);
        }
        if (contentScan.hit()) {
            sensitiveWordService.recordHit("THREAD", null, actor.userId(), contentScan);
        }
        if (SensitiveWordService.ScanResult.BLOCK.equals(titleScan.action())
                || SensitiveWordService.ScanResult.BLOCK.equals(contentScan.action())) {
            throw new BizException(ForumErrorCodes.SENSITIVE_BLOCKED, "内容包含不允许发布的词，请修改后重试");
        }
        title = titleScan.text();
        content = contentScan.text();

        boolean auditRequired = board.getAuditMode() != null && board.getAuditMode() == 1
                || SensitiveWordService.ScanResult.PENDING.equals(titleScan.action())
                || SensitiveWordService.ScanResult.PENDING.equals(contentScan.action())
                || isNewUser(actor);
        ForumThread thread = new ForumThread();
        thread.setId(IdWorker.getId());
        thread.setTenantId(tenantId);
        thread.setBoardId(board.getId());
        thread.setThreadNo(nextThreadNo(thread.getId()));
        thread.setThreadType(request.getThreadType() == null ? "NORMAL" : request.getThreadType());
        thread.setTitle(title);
        thread.setContent(content);
        // 检索用纯文本：与摘要同源（去标签 + 压缩空白），只是保留更长，供全文索引使用
        thread.setContentText(RichTextSanitizer.summarize(content, properties.getSearchTextLength()));
        thread.setSummary(RichTextSanitizer.summarize(content, properties.getSummaryLength()));
        thread.setTags(tags.isEmpty() ? null : String.join(",", tags));
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
        // 标签行与帖子同事务写入：标签页的数据源不能靠异步补，否则发帖后立刻点标签是空的
        syncTags(thread.getId(), tenantId, tags);
        if (STATUS_PUBLISHED.equals(thread.getStatus())) {
            // 待审帖不派发 @：帖子还没发布，被 @ 的人点进来是「看不到的内容」
            mentionService.dispatch(thread.getId(), MentionService.MAIN_POST_ID,
                    MentionService.SOURCE_THREAD, content, title, actor.userId());
            // 发帖经验（F-07-04）：只在发布时计，待审帖转发布由审核通过路径补计；
            // 匿名帖不计经验。幂等靠唯一键（同人同帖同事件只发一次）
            if (thread.getAnonymous() == null || thread.getAnonymous() != 1) {
                expService.award(actor.userId(), ExpService.EVENT_THREAD_PUBLISH,
                        properties.getExpThreadPublish(), thread.getId());
            }
        }
        return toDetailVO(thread, board, actor);
    }

    @Override
    public PageResult<ThreadListItemVO> page(ThreadQuery query, ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            // 关键词走检索通道：ngram 全文（标题命中优先）或单字 LIKE 回退，口径只在 search 里维护
            return search(query, actor);
        }
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = normalizeSize(query.getSize());
        List<Long> boardIds = visibleBoardIds(query, actor);
        if (boardIds.isEmpty()) {
            return PageResult.of(List.of(), 0, current, size);
        }
        boolean hot = !SORT_LATEST.equalsIgnoreCase(query.getSort()) && !"essence".equalsIgnoreCase(query.getSort());
        LocalDateTime from = startOfDay(query.getFrom());
        LocalDateTime toExclusive = startOfNextDay(query.getTo());
        String tag = normalizeTag(query.getTag());
        LambdaQueryWrapper<ForumThread> wrapper = Wrappers.<ForumThread>lambdaQuery()
                .in(ForumThread::getBoardId, boardIds)
                .in(ForumThread::getStatus, VISIBLE_STATUSES)
                .eq(Boolean.TRUE.equals(query.getEssenceOnly()), ForumThread::getEssence, 1)
                .eq(query.getAuthorId() != null, ForumThread::getAuthorId, query.getAuthorId())
                .ge(from != null, ForumThread::getPublishTime, from)
                .lt(toExclusive != null, ForumThread::getPublishTime, toExclusive)
                .orderByDesc(ForumThread::getPinned)
                .orderByDesc(hot, ForumThread::getLastReplyTime)
                .orderByDesc(!hot, ForumThread::getPublishTime);
        if (tag != null) {
            // 标签用 EXISTS 下推，不 LIKE 逗号串：「后端」不能命中「前端后端」
            wrapper.apply("EXISTS (SELECT 1 FROM fm_thread_tag tt WHERE tt.tenant_id = {0} "
                    + "AND tt.thread_id = fm_thread.id AND tt.tag = {1})", tenantId, tag);
        }
        IPage<ForumThread> page = threadMapper.selectPage(
                new Page<>(Math.min(current, properties.getMaxPageNumber()), size), wrapper);
        List<ThreadListItemVO> records = new ArrayList<>(page.getRecords().size());
        for (ForumThread thread : page.getRecords()) {
            records.add(toListVO(thread));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<ThreadListItemVO> search(ThreadQuery query, ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().strip();
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = normalizeSize(query.getSize());
        long pageNo = Math.min(current, properties.getMaxPageNumber());
        if (keyword.isEmpty()) {
            return PageResult.of(List.of(), 0, current, size);
        }
        List<Long> boardIds = visibleBoardIds(query, actor);
        if (boardIds.isEmpty()) {
            return PageResult.of(List.of(), 0, current, size);
        }
        boolean essenceOnly = Boolean.TRUE.equals(query.getEssenceOnly());
        boolean fulltext = searchService.useFulltext(keyword);
        LocalDateTime from = startOfDay(query.getFrom());
        LocalDateTime toExclusive = startOfNextDay(query.getTo());
        String tag = normalizeTag(query.getTag());
        long total = threadMapper.countSearchRows(tenantId, boardIds, keyword, essenceOnly,
                query.getAuthorId(), from, toExclusive, tag, fulltext);
        if (total == 0) {
            return PageResult.of(List.of(), 0, current, size);
        }
        List<ForumThread> rows = threadMapper.selectSearchRows(tenantId, boardIds, keyword,
                essenceOnly, query.getAuthorId(), from, toExclusive, tag, fulltext,
                (pageNo - 1) * size, size);
        List<ThreadListItemVO> records = new ArrayList<>(rows.size());
        for (ForumThread thread : rows) {
            records.add(toListVO(thread));
        }
        return PageResult.of(records, total, pageNo, size);
    }

    /**
     * 解析当前操作者可见的版块范围（单版块或全部可见版块）。
     *
     * @param query 查询条件
     * @param actor 当前操作者
     * @return 版块ID列表，空表示无可见版块
     */
    private List<Long> visibleBoardIds(ThreadQuery query, ForumActor actor) {
        if (query.getBoardId() != null) {
            ForumBoard board = boardService.requireVisibleBoard(query.getBoardId(), actor);
            return List.of(board.getId());
        }
        return boardService.listVisible(actor).stream().map(vo -> vo.getId()).toList();
    }

    @Override
    public ThreadDetailVO detail(Long id, ForumActor actor) {
        TenantContext.requireId();
        ForumThread thread = requireReadable(id, actor);
        ForumBoard board = boardService.requireVisibleBoard(thread.getBoardId(), actor);
        // 浏览 +1 交去重服务判定：同人同帖 24h 内只计一次，刷新页面刷不出浏览量
        if (VISIBLE_STATUSES.contains(thread.getStatus())
                && viewCountService.touch(thread.getId(), actor.userId())) {
            threadMapper.increaseViewCount(thread.getId());
            thread.setViewCount(thread.getViewCount() == null ? 1 : thread.getViewCount() + 1);
        }
        ThreadDetailVO vo = toDetailVO(thread, board, actor);
        // 按钮态只在详情里查：发帖/编辑的响应不需要，多一次查询就多一份进列表页的开销
        Set<String> mine = reactionStateReader.typesOf(thread.getId(), actor.userId());
        vo.setLiked(mine.contains(ReactionType.LIKE.name()));
        vo.setCollected(mine.contains(ReactionType.COLLECT.name()));
        return vo;
    }

    @Override
    public PageResult<ThreadListItemVO> collections(long current, long size, ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        long pageNo = current < 1 ? 1 : Math.min(current, properties.getMaxPageNumber());
        Page<ForumThread> page = new Page<>(pageNo, normalizeSize(size));
        IPage<ForumThread> result = threadMapper.selectCollectedPage(page, tenantId, actor.userId(),
                ReactionType.COLLECT.name());
        List<ThreadListItemVO> records = new ArrayList<>(result.getRecords().size());
        for (ForumThread thread : result.getRecords()) {
            records.add(toListVO(thread));
        }
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
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
        boolean tagsProvided = request.getTags() != null;
        List<String> newTags = tagsProvided ? normalizeTags(request.getTags()) : null;
        if (newTitle == null && newContent == null && !tagsProvided) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标题、正文与标签不能同时为空");
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
            thread.setContentText(RichTextSanitizer.summarize(newContent, properties.getSearchTextLength()));
            thread.setSummary(RichTextSanitizer.summarize(newContent, properties.getSummaryLength()));
        }
        if (tagsProvided) {
            thread.setTags(newTags.isEmpty() ? null : String.join(",", newTags));
        }
        boolean auditRequired = board.getAuditMode() != null && board.getAuditMode() == 1;
        if (auditRequired && properties.isAuditOnEdit() && !manager
                && !STATUS_PENDING.equals(thread.getStatus())) {
            // 发布态编辑必须退回待审：否则审核通过后改正文即可绕过审核
            thread.setStatus(STATUS_PENDING);
            thread.setPublishTime(null);
        }
        threadMapper.updateById(thread);
        if (tagsProvided) {
            // 整体重建而不是差量比对：并发编辑下差量会漏行，单帖标签最多十来个
            syncTags(thread.getId(), thread.getTenantId(), newTags);
        }
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
     * 归一化标签串：逗号拆分、去首尾空白、去重保序，并校验数量与单标签长度。
     *
     * <p>超限直接 400：标签长于列宽会以 1406 变成 500，数量不设上限则能靠标签把列表页刷屏。</p>
     *
     * @param raw 原始标签串（逗号分隔）
     * @return 归一化后的标签（可能为空列表）
     */
    private List<String> normalizeTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String tag = part.strip();
            if (tag.isEmpty()) {
                continue;
            }
            if (tag.length() > properties.getMaxTagLength()) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "单个标签不超过 " + properties.getMaxTagLength() + " 字符");
            }
            tags.add(tag);
        }
        if (tags.size() > properties.getMaxTagsPerThread()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "最多选择 " + properties.getMaxTagsPerThread() + " 个标签");
        }
        return List.copyOf(tags);
    }

    /**
     * 归一化单个筛选标签（空串视为不筛选）。
     *
     * @param raw 原始标签
     * @return 去空白后的标签或 {@code null}
     */
    private String normalizeTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.strip();
    }

    /**
     * 整体重建帖子的标签行（先删后插，幂等）。
     *
     * @param threadId 帖子ID
     * @param tenantId 租户ID
     * @param tags 归一化后的标签
     */
    private void syncTags(Long threadId, Long tenantId, List<String> tags) {
        threadTagMapper.delete(Wrappers.<ForumThreadTag>lambdaQuery()
                .eq(ForumThreadTag::getThreadId, threadId));
        if (tags == null || tags.isEmpty()) {
            return;
        }
        List<ForumThreadTag> rows = new ArrayList<>(tags.size());
        for (String tag : tags) {
            ForumThreadTag row = new ForumThreadTag();
            // 自定义 INSERT 不会走 TableId 自动填充，主键必须在这里给
            row.setId(IdWorker.getId());
            row.setTenantId(tenantId);
            row.setThreadId(threadId);
            row.setTag(tag);
            rows.add(row);
        }
        threadTagMapper.insertIgnoreBatch(rows);
    }

    /**
     * 起始日期转当日 00:00（空返回 {@code null}）。
     *
     * @param date 起始日期
     * @return 起始时间
     */
    private LocalDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    /**
     * 截止日期转次日 00:00（查询用左闭右开，保证「含截止日」）。
     *
     * @param date 截止日期
     * @return 次日零点
     */
    private LocalDateTime startOfNextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
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
     * 是否「新用户」（已发布帖数少于阈值，前 N 帖必审）。
     *
     * <p>管理者豁免：版主/管理员刚被授予权限时不该被自己的首帖卡在待审队列里，
     * 否则审核台没人能审。</p>
     *
     * @param actor 当前操作者
     * @return 需要强制送审返回 {@code true}
     */
    private boolean isNewUser(ForumActor actor) {
        int threshold = properties.getNewUserAuditThreads();
        if (threshold <= 0) {
            return false;
        }
        if (actor.hasPermission(ForumPermissions.MANAGE_AUDIT)
                || actor.hasPermission(ForumPermissions.MANAGE_BOARD)) {
            return false;
        }
        // 用字符串列名而非 lambda：lambda 依赖 TableInfo 缓存，纯单测（无 Spring 上下文）
        // 会直接抛 "can not find lambda cache for this entity"，让业务规则没法被单测覆盖
        Long published = threadMapper.selectCount(new QueryWrapper<ForumThread>()
                .eq("author_id", actor.userId())
                .in("status", VISIBLE_STATUSES));
        return published == null || published < threshold;
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
        // 默认未互动：详情接口会按实际查询覆盖，发帖/编辑响应保持 false 而不是 null
        vo.setLiked(Boolean.FALSE);
        vo.setCollected(Boolean.FALSE);
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
        vo.setCollectCount(thread.getCollectCount());
        vo.setLastReplyTime(thread.getLastReplyTime());
        vo.setPublishTime(thread.getPublishTime());
    }
}
