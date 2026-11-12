package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.MentionParser;
import com.bluewhale.forum.common.RichTextSanitizer;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.entity.ForumMention;
import com.bluewhale.forum.mapper.ForumMentionMapper;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.service.MentionService;
import com.bluewhale.forum.support.ForumUserDirectoryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @提及服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MentionServiceImpl implements MentionService {

    /** outbox 业务类型：提及 */
    private static final String BIZ_MENTION = "MENTION";

    private final ForumMentionMapper mentionMapper;

    private final ForumUserDirectoryClient directoryClient;

    private final ForumNotifyService notifyService;

    private final ForumProperties properties;

    @Override
    public int dispatch(Long threadId, Long postId, String source, String richTextContent,
                        String threadTitle, Long authorId) {
        Long tenantId = TenantContext.requireId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 发帖/回帖都在事务内：解析要打 auth 内网 HTTP，钉在事务里等于让连接空等网络；
            // 而且帖子还没提交就通知人，等于通知了一个可能回滚的帖子。
            // 因此推迟到 afterCommit——事务提交了才解析、落库、通知。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doDispatch(tenantId, threadId, postId, source, richTextContent, threadTitle, authorId);
                }
            });
            return 0;
        }
        return doDispatch(tenantId, threadId, postId, source, richTextContent, threadTitle, authorId);
    }

    /**
     * 实际执行解析、落库与通知。
     *
     * @param tenantId 租户ID（在提交前的上下文里取好，afterCommit 阶段不再依赖 ThreadLocal）
     * @param threadId 帖子ID
     * @param postId 楼层ID
     * @param source 来源
     * @param richTextContent 富文本正文
     * @param threadTitle 帖子标题
     * @param authorId 发起人
     * @return 实际通知人数
     */
    private int doDispatch(Long tenantId, Long threadId, Long postId, String source,
                           String richTextContent, String threadTitle, Long authorId) {
        MentionParser.Result parsed = parse(richTextContent);
        if (parsed.names().isEmpty()) {
            log.debug("@提及派发：无提及，threadId={}, postId={}", threadId, postId);
            return 0;
        }
        if (parsed.truncated()) {
            // 超限只取前 N 个：不是报错，但要留痕，否则「我明明 @ 了 30 个人只到 20 个」无法解释
            log.info("@提及超出上限已截断：threadId={}, postId={}, limit={}",
                    threadId, postId, properties.getMaxMentionsPerPost());
        }
        Map<String, Long> resolved = directoryClient.resolve(parsed.names(), tenantId);
        log.debug("@提及派发：threadId={}, postId={}, 候选={}, 解析到={}", threadId, postId,
                parsed.names(), resolved.keySet());
        if (resolved.isEmpty()) {
            return 0;
        }
        long mentionPostId = postId == null ? MAIN_POST_ID : postId;
        int notified = 0;
        for (Map.Entry<String, Long> entry : resolved.entrySet()) {
            Long userId = entry.getValue();
            if (userId == null || userId.equals(authorId)) {
                continue;
            }
            if (!record(tenantId, threadId, mentionPostId, userId, authorId, source)) {
                // 唯一键已存在：同层同人已 @ 过，不重复打扰
                continue;
            }
            notify(threadId, mentionPostId, threadTitle, userId);
            notified++;
        }
        return notified;
    }

    @Override
    public List<Long> resolveUserIds(String richTextContent) {
        MentionParser.Result parsed = parse(richTextContent);
        if (parsed.names().isEmpty()) {
            return List.of();
        }
        Map<Long, Boolean> seen = new LinkedHashMap<>();
        for (Long userId : directoryClient.resolve(parsed.names(), TenantContext.get()).values()) {
            if (userId != null) {
                seen.putIfAbsent(userId, Boolean.TRUE);
            }
        }
        return new ArrayList<>(seen.keySet());
    }

    /**
     * 富文本转纯文本后解析提及。
     *
     * @param richTextContent 富文本
     * @return 解析结果
     */
    private MentionParser.Result parse(String richTextContent) {
        if (richTextContent == null || richTextContent.isBlank()) {
            return MentionParser.Result.EMPTY;
        }
        // 用检索列的长度（4000）而不是列表摘要的 200：@ 常出现在正文末尾，按摘要截断会漏
        String plain = RichTextSanitizer.summarize(richTextContent, properties.getSearchTextLength());
        return MentionParser.parse(plain, properties.getMaxMentionsPerPost());
    }

    /**
     * 落一条提及记录。
     *
     * @param tenantId 租户ID
     * @param threadId 帖子ID
     * @param postId 楼层ID（0 主帖）
     * @param userId 被提及人
     * @param authorId 发起人
     * @param source 来源
     * @return 首次落库返回 {@code true}；重复（唯一键冲突）返回 {@code false}
     */
    private boolean record(Long tenantId, Long threadId, long postId, Long userId, Long authorId,
                           String source) {
        ForumMention mention = new ForumMention();
        mention.setTenantId(tenantId);
        mention.setThreadId(threadId);
        mention.setPostId(postId);
        mention.setUserId(userId);
        mention.setMentionedBy(authorId);
        mention.setSource(source == null ? SOURCE_POST : source);
        mention.setCreateTime(LocalDateTime.now());
        try {
            mentionMapper.insert(mention);
            return true;
        } catch (DuplicateKeyException duplicated) {
            return false;
        } catch (RuntimeException error) {
            // 提及表写失败（如迁移未执行）不影响帖子本身，如实记录后跳过
            log.warn("@提及落库失败，已跳过：threadId={}, postId={}, userId={}, reason={}",
                    threadId, postId, userId, error.getMessage());
            return false;
        }
    }

    /**
     * 投递提及通知（走 outbox，先落库再发送）。
     *
     * @param threadId 帖子ID
     * @param postId 楼层ID
     * @param threadTitle 帖子标题
     * @param receiverId 被提及人
     */
    private void notify(Long threadId, long postId, String threadTitle, Long receiverId) {
        String title = "有人在帖子里提到了你";
        String content = "有鲸友在《" + safeTitle(threadTitle) + "》中 @ 了你，点击查看。";
        // 幂等键必须带 threadId：主帖的 postId 恒为 0，只带 postId 时「第二个帖子 @ 同一个人」
        // 会撞 outbox 唯一键，通知被静默丢掉
        notifyService.enqueue(receiverId, BIZ_MENTION, threadId,
                "forum:mention:" + threadId + ":" + postId + ":" + receiverId, title, content);
    }

    /**
     * 标题兜底（通知正文里拼接标题，空标题会出现「《》」）。
     *
     * @param threadTitle 标题
     * @return 可展示标题
     */
    private String safeTitle(String threadTitle) {
        return threadTitle == null || threadTitle.isBlank() ? "某个帖子" : threadTitle;
    }
}
