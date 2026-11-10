package com.bluewhale.lms.integration.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.integration.application.service.NotifyAppService;
import com.bluewhale.lms.integration.application.view.NotifyLogView;
import com.bluewhale.lms.integration.domain.gateway.NotifyGateway;
import com.bluewhale.lms.integration.domain.model.NotifyKey;
import com.bluewhale.lms.integration.domain.model.NotifyLog;
import com.bluewhale.lms.integration.domain.model.NotifyLogQuery;
import com.bluewhale.lms.integration.domain.model.NotifyRequest;
import com.bluewhale.lms.integration.domain.model.NotifyScene;
import com.bluewhale.lms.integration.domain.repository.NotifyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 通知投递应用服务实现。
 *
 * <p>投递口径有两条硬规则：</p>
 * <ol>
 *   <li><b>已成功的不重发</b>：按 {@code (幂等键, 接收人)} 查留痕，命中
 *       {@code SENT} 直接跳过。失败记录不跳过，下一轮会重试；</li>
 *   <li><b>先落痕再算完</b>：投递成功后才写 {@code SENT}。反过来先写成功标记再发，
 *       一旦发送抛异常就会留下「标记为已发、实际没发」的永久漏发记录。</li>
 * </ol>
 *
 * <p>并发下两个实例可能同时通过「未发过」检查，此时唯一键会挡住第二条插入。
 * 这种情况按「已被别人记过」处理而不是抛错：消息服务侧的键去重保证只投一次，
 * 本地记录也确实存在，再报错只会让调用方误以为失败而重试整批。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyAppServiceImpl implements NotifyAppService {

    /** 正文最大长度（消息服务限制） */
    private static final int CONTENT_MAX_LENGTH = 1000;

    /** 标题最大长度（与列宽一致） */
    private static final int TITLE_MAX_LENGTH = 200;

    private final NotifyLogRepository notifyLogRepository;

    private final NotifyGateway notifyGateway;

    private final LmsProperties properties;

    @Override
    public int send(List<Long> recipients, NotifyScene scene, String idempotencyKey,
                    String bizType, Long bizId, String title, String content) {
        assertGate();
        return dispatch(build(recipients, scene, idempotencyKey, bizType, bizId, title, content));
    }

    @Override
    public int sendQuietly(List<Long> recipients, NotifyScene scene, String idempotencyKey,
                           String bizType, Long bizId, String title, String content) {
        try {
            return send(recipients, scene, idempotencyKey, bizType, bizId, title, content);
        } catch (RuntimeException exception) {
            log.warn("附带通知投递失败（主流程不回滚）: scene={}, key={}, error={}",
                    scene, idempotencyKey, exception.getMessage());
            return 0;
        }
    }

    @Override
    public int notifyEnroll(Long courseId, String courseName, Long userId) {
        return sendQuietly(List.of(userId), NotifyScene.ENROLL, NotifyKey.enroll(courseId),
                "COURSE", courseId, NotifyScene.ENROLL.getDefaultTitle(),
                "你已成功报名《" + safe(courseName) + "》，可在「我的学习」中开始学习。");
    }

    @Override
    public int notifyCertIssue(Long certificateId, String certNo, String certName, Long userId) {
        return sendQuietly(List.of(userId), NotifyScene.CERT_ISSUE, NotifyKey.certIssue(certificateId),
                "CERTIFICATE", certificateId, NotifyScene.CERT_ISSUE.getDefaultTitle(),
                "证书《" + safe(certName) + "》已发放，编号 " + safe(certNo) + "，可在「我的证书」查看。");
    }

    @Override
    public int notifyUrge(Long classId, String className, List<Long> userIds, LocalDate day) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        return send(userIds, NotifyScene.URGE, NotifyKey.urge(classId, day),
                "TRAINING_CLASS", classId, NotifyScene.URGE.getDefaultTitle(),
                "你参加的培训班《" + safe(className) + "》尚未完成，请尽快完成学习与签到。");
    }

    @Override
    public PageResult<NotifyLogView> page(NotifyLogQuery query, LmsActor actor) {
        assertGate();
        if (!actor.canManageAll()) {
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_INVALID, "无权查看通知日志");
        }
        long current = Math.max(query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize(), 1L), properties.getNotifyPageLimit());
        long total = notifyLogRepository.count(query);
        List<NotifyLogView> views = notifyLogRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(NotifyLogView::of).toList();
        return PageResult.of(views, total, current, size);
    }

    /**
     * 执行投递：过滤已成功的接收人后一次投出，并按人留痕。
     *
     * @param request 通知请求
     * @return 实际投递的接收人数
     */
    private int dispatch(NotifyRequest request) {
        Map<Long, NotifyLog> known = new HashMap<>();
        List<Long> pending = new ArrayList<>();
        // 同一批里重复出现的接收人只算一次：既有调用方可能把「部门 + 角色」展开成重叠名单
        for (Long userId : new LinkedHashSet<>(request.getRecipients())) {
            if (userId == null) {
                continue;
            }
            NotifyLog existing = notifyLogRepository.findByKeyAndUser(request.getIdempotencyKey(), userId)
                    .orElse(null);
            if (existing != null && existing.alreadySent()) {
                continue;
            }
            if (existing != null) {
                known.put(userId, existing);
            }
            pending.add(userId);
        }
        if (pending.isEmpty()) {
            return 0;
        }
        NotifyRequest narrowed = new NotifyRequest();
        narrowed.setScene(request.getScene());
        narrowed.setIdempotencyKey(request.getIdempotencyKey());
        narrowed.setBizType(request.getBizType());
        narrowed.setBizId(request.getBizId());
        narrowed.setTemplateCode(request.getTemplateCode());
        narrowed.setTitle(request.getTitle());
        narrowed.setContent(request.getContent());
        narrowed.setRecipients(pending);
        LocalDateTime now = LocalDateTime.now();
        try {
            notifyGateway.send(narrowed);
        } catch (RuntimeException exception) {
            persist(pending, known, request, false, exception.getMessage(), now);
            throw LmsErrorCode.exception(LmsErrorCode.NOTIFY_FAILED, "通知投递失败：" + exception.getMessage());
        }
        persist(pending, known, request, true, null, now);
        return pending.size();
    }

    /**
     * 批量留痕。
     *
     * @param userIds 接收人
     * @param known 已存在的日志（失败重试时复用主键）
     * @param request 通知请求
     * @param success 是否成功
     * @param reason 失败原因
     * @param now 时间
     */
    private void persist(List<Long> userIds, Map<Long, NotifyLog> known,
                         NotifyRequest request, boolean success, String reason, LocalDateTime now) {
        for (Long userId : userIds) {
            NotifyLog entry = known.getOrDefault(userId, newLog(userId, request));
            if (success) {
                entry.markSent(now);
            } else {
                entry.markFailed(reason, now);
            }
            try {
                notifyLogRepository.save(entry);
            } catch (DuplicateKeyException exception) {
                // 并发下另一实例已记过同一 (键, 接收人)：保留对方的结果，不覆盖
                log.warn("通知留痕并发冲突，按已存在处理: key={}, userId={}",
                        request.getIdempotencyKey(), userId);
            }
        }
    }

    /**
     * 构造新日志。
     *
     * @param userId 接收人
     * @param request 通知请求
     * @return 日志
     */
    private NotifyLog newLog(Long userId, NotifyRequest request) {
        NotifyLog log = new NotifyLog();
        log.setScene(request.getScene());
        log.setIdempotencyKey(request.getIdempotencyKey());
        log.setBizType(request.getBizType());
        log.setBizId(request.getBizId());
        log.setUserId(userId);
        log.setTemplateCode(request.getTemplateCode());
        log.setTitle(request.getTitle());
        log.setChannel("INAPP");
        return log;
    }

    /**
     * 组装并校验通知请求。
     *
     * @param recipients 接收人
     * @param scene 场景
     * @param idempotencyKey 幂等键
     * @param bizType 业务类型
     * @param bizId 业务 ID
     * @param title 标题
     * @param content 正文
     * @return 通知请求
     */
    private NotifyRequest build(List<Long> recipients, NotifyScene scene, String idempotencyKey,
                                String bizType, Long bizId, String title, String content) {
        NotifyRequest request = new NotifyRequest();
        request.setScene(scene);
        request.setIdempotencyKey(idempotencyKey);
        request.setBizType(bizType);
        request.setBizId(bizId);
        request.setTemplateCode(scene == null ? null : scene.getTemplateCode());
        request.setTitle(truncate(title == null ? (scene == null ? null : scene.getDefaultTitle()) : title,
                TITLE_MAX_LENGTH));
        request.setContent(truncate(content, CONTENT_MAX_LENGTH));
        request.setRecipients(recipients);
        request.validate();
        return request;
    }

    /**
     * 截断超长文本。
     *
     * @param text 文本
     * @param max 上限
     * @return 截断后的文本
     */
    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * 空值兜底为空串（拼文案用）。
     *
     * @param text 文本
     * @return 文本
     */
    private String safe(String text) {
        return text == null ? "" : text;
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }
}
