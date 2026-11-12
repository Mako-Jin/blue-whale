package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.entity.ForumNotifyOutbox;
import com.bluewhale.forum.mapper.ForumNotifyOutboxMapper;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.support.ForumNotifyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知 outbox 实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumNotifyServiceImpl implements ForumNotifyService {

    /** 业务类型：被回复 */
    private static final String BIZ_REPLY = "REPLY";

    /** 业务类型：处置 */
    private static final String BIZ_MODERATION = "MODERATION";

    /** 状态：待投 */
    private static final String STATUS_PENDING = "PENDING";

    /** 重试基准间隔（秒）：第 n 次失败后等 n*60 秒 */
    private static final int RETRY_BASE_SECONDS = 60;

    private final ForumNotifyOutboxMapper outboxMapper;

    private final ForumNotifyClient notifyClient;

    private final ForumProperties properties;

    @Override
    public void enqueue(Long receiverId, String bizType, Long bizId, String idempotencyKey,
                        String title, String content) {
        if (receiverId == null) {
            return;
        }
        ForumNotifyOutbox row = new ForumNotifyOutbox();
        row.setTenantId(TenantContext.requireId());
        row.setReceiverId(receiverId);
        row.setBizType(bizType);
        row.setBizId(bizId);
        row.setIdempotencyKey(idempotencyKey);
        String safeTitle = title == null ? "论坛通知" : title;
        String safeContent = content == null ? "" : content;
        row.setTitle(safeTitle.length() > 128 ? safeTitle.substring(0, 128) : safeTitle);
        row.setContent(safeContent.length() > 2000 ? safeContent.substring(0, 2000) : safeContent);
        row.setStatus(STATUS_PENDING);
        row.setRetryCount(0);
        try {
            outboxMapper.insert(row);
        } catch (DuplicateKeyException duplicated) {
            // 同一事件已入队：不重复打扰
            log.debug("通知已存在，跳过：key={}", idempotencyKey);
            return;
        }
        deliver(row);
    }

    @Override
    public int flushPending(int limit) {
        List<ForumNotifyOutbox> pending = outboxMapper.selectPending(Math.max(1, limit));
        int success = 0;
        for (ForumNotifyOutbox row : pending) {
            if (deliver(row)) {
                success++;
            }
        }
        return success;
    }

    @Override
    public void notifyReply(Long threadId, Long postId, String threadTitle, Long receiverId,
                            String replierName) {
        String title = "你的帖子有新回复";
        String content = replierName + " 回复了《" + threadTitle + "》，点击查看。";
        enqueue(receiverId, BIZ_REPLY, threadId, "forum:reply:" + postId + ":" + receiverId, title, content);
    }

    @Override
    public void notifyModeration(Long receiverId, String targetType, Long targetId, String action,
                                 String reason, Long recordId) {
        String title = "内容处置通知";
        String content = "你的" + targetLabel(targetType) + "因「" + reason + "」被" + actionLabel(action)
                + "。如有异议可在论坛内提交申诉。";
        String key = "forum:mod:" + recordId + ":" + receiverId;
        enqueue(receiverId, BIZ_MODERATION, targetId, key, title, content);
    }

    /**
     * 投递一条 outbox 记录：成功置 SENT，失败累加重试。
     *
     * @param row 记录
     * @return 成功返回 {@code true}
     */
    private boolean deliver(ForumNotifyOutbox row) {
        boolean sent = notifyClient.sendInapp(row.getTenantId(), row.getIdempotencyKey(),
                row.getTitle(), row.getContent(), row.getReceiverId());
        if (sent) {
            outboxMapper.markSent(row.getId());
            return true;
        }
        int retried = (row.getRetryCount() == null ? 0 : row.getRetryCount()) + 1;
        boolean giveUp = retried >= properties.getNotifyMaxRetry();
        outboxMapper.markFailed(row.getId(), "投递失败（whale-message 不可达或返回非 0）",
                retried, giveUp, RETRY_BASE_SECONDS * retried);
        if (giveUp) {
            log.warn("通知重试耗尽转 FAILED：key={}, receiver={}", row.getIdempotencyKey(), row.getReceiverId());
        }
        return false;
    }

    /**
     * 对象类型中文名。
     *
     * @param targetType 类型
     * @return 中文名
     */
    private String targetLabel(String targetType) {
        if ("POST".equals(targetType)) {
            return "回复";
        }
        if ("USER".equals(targetType)) {
            return "账号";
        }
        return "帖子";
    }

    /**
     * 动作中文名。
     *
     * @param action 动作
     * @return 中文名
     */
    private String actionLabel(String action) {
        return switch (action == null ? "" : action) {
            case "AUDIT_REJECT" -> "驳回";
            case "REMOVE" -> "删除";
            case "MUTE" -> "禁言";
            case "BAN" -> "封禁";
            default -> "处理";
        };
    }
}
