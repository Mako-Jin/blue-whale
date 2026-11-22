package com.bluewhale.pdm.change.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.change.client.PdmChgMessageClient;
import com.bluewhale.pdm.change.dto.PdmChgNoticeQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgNoticeSaveRequest;
import com.bluewhale.pdm.change.entity.PdmChgNotice;
import com.bluewhale.pdm.change.entity.PdmChgNotifyLog;
import com.bluewhale.pdm.change.entity.PdmChgOrder;
import com.bluewhale.pdm.change.entity.PdmChgTask;
import com.bluewhale.pdm.change.mapper.PdmChgNoticeMapper;
import com.bluewhale.pdm.change.mapper.PdmChgNotifyLogMapper;
import com.bluewhale.pdm.change.mapper.PdmChgOrderMapper;
import com.bluewhale.pdm.change.mapper.PdmChgTaskMapper;
import com.bluewhale.pdm.change.service.PdmChgNoticeService;
import com.bluewhale.pdm.change.support.PdmChgBizType;
import com.bluewhale.pdm.change.support.PdmChgNoticeStatus;
import com.bluewhale.pdm.change.support.PdmChgObjectType;
import com.bluewhale.pdm.change.support.PdmChgOrderStatus;
import com.bluewhale.pdm.change.support.PdmChgTaskStatus;
import com.bluewhale.pdm.change.support.PdmChgTraceAction;
import com.bluewhale.pdm.change.support.PdmChgTraceRecorder;
import com.bluewhale.pdm.change.vo.PdmChgImpactObjectVO;
import com.bluewhale.pdm.change.vo.PdmChgNoticeVO;
import com.bluewhale.pdm.change.vo.PdmChgNotifyLogVO;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.mapper.PdmDocVersionMapper;
import com.bluewhale.pdm.part.entity.PdmBom;
import com.bluewhale.pdm.part.mapper.PdmBomMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 变更通知（ECN）服务实现
 *
 * <p>ECN 必须由已审批通过的 ECO 生成；生效日期不早于 ECO 审批通过日；
 * 生效时把同对象更低版本置为作废，通知失败只落投递日志、不回滚生效事实。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmChgNoticeServiceImpl implements PdmChgNoticeService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 定时任务单次扫描上限 */
    private static final int DUE_SCAN_LIMIT = 100;

    /** 通知编号前缀 */
    private static final String NOTICE_NO_PREFIX = "CN-";

    /** 通知编号时间格式 */
    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 编号随机后缀上界 */
    private static final int RANDOM_SUFFIX_BOUND = 10_000;

    /** 失败原因最大长度 */
    private static final int MAX_ERROR_LENGTH = 500;

    /** 默认通知渠道 */
    private static final String DEFAULT_CHANNEL = "INBOX";

    /** 可创建 ECN 的 ECO 状态 */
    private static final Set<String> ECO_READY_STATUSES = Set.of(PdmChgOrderStatus.APPROVED,
            PdmChgOrderStatus.EXECUTING, PdmChgOrderStatus.COMPLETED, PdmChgOrderStatus.CLOSED);

    /** 文档版本已发布状态 */
    private static final String DOC_RELEASED = "RELEASED";

    /** 文档版本作废状态 */
    private static final String DOC_OBSOLETE = "OBSOLETE";

    /** BOM 已发布状态 */
    private static final String BOM_RELEASED = "RELEASED";

    /** BOM 作废状态 */
    private static final String BOM_OBSOLETE = "OBSOLETE";

    /** 投递成功状态 */
    private static final String NOTIFY_SUCCESS = "SUCCESS";

    /** 投递失败状态 */
    private static final String NOTIFY_FAILED = "FAILED";

    /** 事件类型：已通知 */
    private static final String EVENT_NOTIFIED = "ECN_NOTIFIED";

    /** 事件类型：已生效 */
    private static final String EVENT_EFFECTIVE = "ECN_EFFECTIVE";

    /** JSON 序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PdmChgNoticeMapper noticeMapper;

    private final PdmChgOrderMapper chgOrderMapper;

    private final PdmChgTaskMapper chgTaskMapper;

    private final PdmChgNotifyLogMapper notifyLogMapper;

    private final PdmDocVersionMapper docVersionMapper;

    private final PdmBomMapper bomMapper;

    private final PdmChgMessageClient messageClient;

    private final PdmChgTraceRecorder traceRecorder;

    @Override
    public PageResult<PdmChgNoticeVO> page(PdmChgNoticeQueryRequest request) {
        LambdaQueryWrapper<PdmChgNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getNoticeNo()), PdmChgNotice::getNoticeNo,
                request.getNoticeNo());
        wrapper.eq(StringUtils.hasText(request.getStatus()), PdmChgNotice::getStatus, request.getStatus());
        wrapper.eq(request.getEcoId() != null, PdmChgNotice::getEcoId, request.getEcoId());
        wrapper.orderByDesc(PdmChgNotice::getCreateTime).orderByDesc(PdmChgNotice::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmChgNotice> page = noticeMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        Map<Long, String> ecoNos = ecoNoMap(page.getRecords().stream()
                .map(PdmChgNotice::getEcoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<PdmChgNoticeVO> records = page.getRecords().stream()
                .map(entity -> toVo(entity, ecoNos.get(entity.getEcoId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PdmChgNoticeVO detail(Long id) {
        PdmChgNotice entity = requireNotice(id);
        PdmChgOrder order = chgOrderMapper.selectById(entity.getEcoId());
        return toVo(entity, order == null ? null : order.getEcoNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgNoticeVO create(PdmChgNoticeSaveRequest request, Long userId) {
        PdmChgOrder order = requireEcoReady(request.getEcoId());
        requireEffectiveFrom(request.getEffectiveFrom(), order);
        long exists = noticeMapper.selectCount(new LambdaQueryWrapper<PdmChgNotice>()
                .eq(PdmChgNotice::getEcoId, order.getId()));
        if (exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.change-notification-already-exists-change-order", "该变更单已存在变更通知");
        }
        PdmChgNotice entity = new PdmChgNotice();
        entity.setNoticeNo(newNoticeNo());
        entity.setEcoId(order.getId());
        entity.setImpactJson(order.getImpactJson());
        applyRequest(entity, request);
        entity.setStatus(PdmChgNoticeStatus.DRAFT);
        noticeMapper.insert(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECN, entity.getId(), entity.getNoticeNo(),
                PdmChgTraceAction.CREATE, userId, "创建变更通知");
        return toVo(entity, order.getEcoNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgNoticeVO update(Long id, PdmChgNoticeSaveRequest request, Long userId) {
        PdmChgNotice entity = requireStatus(id, PdmChgNoticeStatus.DRAFT);
        if (!entity.getEcoId().equals(request.getEcoId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.allowed-replace-source-change-order", "不允许更换来源变更单");
        }
        PdmChgOrder order = requireEcoReady(entity.getEcoId());
        requireEffectiveFrom(request.getEffectiveFrom(), order);
        applyRequest(entity, request);
        noticeMapper.updateById(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECN, entity.getId(), entity.getNoticeNo(),
                PdmChgTraceAction.UPDATE, userId, "修改变更通知");
        return toVo(entity, order.getEcoNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireStatus(id, PdmChgNoticeStatus.DRAFT);
        noticeMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgNoticeVO notify(Long id, Long userId) {
        PdmChgNotice entity = requireStatus(id, PdmChgNoticeStatus.DRAFT);
        entity.setStatus(PdmChgNoticeStatus.NOTIFIED);
        entity.setNotifiedTime(LocalDateTime.now());
        noticeMapper.updateById(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECN, entity.getId(), entity.getNoticeNo(),
                PdmChgTraceAction.NOTIFY, userId, "发送变更通知");
        emitMessage(entity, EVENT_NOTIFIED);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgNoticeVO effective(Long id, Long userId) {
        PdmChgNotice entity = requireNotice(id);
        if (PdmChgNoticeStatus.EFFECTIVE.equals(entity.getStatus())) {
            return detail(id);
        }
        if (!PdmChgNoticeStatus.NOTIFIED.equals(entity.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.notification-change-notification-can-take-effect", "仅已通知的变更通知可生效");
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getEffectiveFrom().isAfter(now)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.effective-date-reach-cannot-in-advance", "生效日期未到，不能提前生效");
        }
        PdmChgOrder order = requireEcoReady(entity.getEcoId());
        applyObsolete(entity, order, userId);
        entity.setStatus(PdmChgNoticeStatus.EFFECTIVE);
        entity.setEffectiveTime(now);
        noticeMapper.updateById(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECN, entity.getId(), entity.getNoticeNo(),
                PdmChgTraceAction.EFFECTIVE, userId, "变更通知生效");
        emitMessage(entity, EVENT_EFFECTIVE);
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgNoticeVO close(Long id, Long userId) {
        PdmChgNotice entity = requireStatus(id, PdmChgNoticeStatus.EFFECTIVE);
        entity.setStatus(PdmChgNoticeStatus.CLOSED);
        entity.setCloseTime(LocalDateTime.now());
        noticeMapper.updateById(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECN, entity.getId(), entity.getNoticeNo(),
                PdmChgTraceAction.CLOSE, userId, "关闭变更通知");
        return detail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PdmChgNoticeVO cancel(Long id, Long userId) {
        PdmChgNotice entity = requireStatus(id, PdmChgNoticeStatus.DRAFT);
        entity.setStatus(PdmChgNoticeStatus.CANCELLED);
        noticeMapper.updateById(entity);
        traceRecorder.recordBiz(PdmChgBizType.ECN, entity.getId(), entity.getNoticeNo(),
                PdmChgTraceAction.CANCEL, userId, "取消变更通知");
        return detail(id);
    }

    @Override
    public PdmChgNoticeVO retryNotify(Long id, Long userId) {
        requireNotice(id);
        PdmChgNotifyLog failed = latestFailedLog(id);
        if (failed == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.failed-notification-available-retry", "没有可重试的失败通知");
        }
        PdmChgNotice notice = requireNotice(id);
        failed.setRetryCount(failed.getRetryCount() == null ? 1 : failed.getRetryCount() + 1);
        try {
            messageClient.send(TenantContext.get(), PdmChgBizType.ECN, String.valueOf(notice.getId()),
                    failed.getIdempotencyKey(), failed.getTitle(), failed.getContent(),
                    parseChannels(notice.getNotifyChannels()), parseTargets(notice.getNotifyTargets()));
            failed.setStatus(NOTIFY_SUCCESS);
            failed.setLastError(null);
            notifyLogMapper.updateById(failed);
        } catch (Exception ex) {
            failed.setStatus(NOTIFY_FAILED);
            failed.setLastError(truncate(ex.getMessage()));
            notifyLogMapper.updateById(failed);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "biz.pdm.change.failed-change-notification-retry", "变更通知重试失败：{0}",
                          truncate(ex.getMessage()));
        }
        return detail(id);
    }

    @Override
    public PageResult<PdmChgNotifyLogVO> notifyLogPage(Long noticeId, Long current, Long size) {
        if (noticeId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.change-notification-id-empty", "变更通知ID不能为空");
        }
        long limit = Math.min(Math.max(size == null ? 20L : size, 1L), MAX_PAGE_SIZE);
        long pageNo = current == null || current < 1L ? 1L : current;
        Page<PdmChgNotifyLog> page = notifyLogMapper.selectPage(new Page<>(pageNo, limit),
                new LambdaQueryWrapper<PdmChgNotifyLog>()
                        .eq(PdmChgNotifyLog::getNoticeId, noticeId)
                        .orderByDesc(PdmChgNotifyLog::getCreateTime)
                        .orderByDesc(PdmChgNotifyLog::getId));
        List<PdmChgNotifyLogVO> records = page.getRecords().stream()
                .map(PdmChgNoticeServiceImpl::toLogVo)
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<PdmChgNotice> dueNotices() {
        return noticeMapper.selectList(new LambdaQueryWrapper<PdmChgNotice>()
                .eq(PdmChgNotice::getStatus, PdmChgNoticeStatus.NOTIFIED)
                .le(PdmChgNotice::getEffectiveFrom, LocalDateTime.now())
                .orderByAsc(PdmChgNotice::getEffectiveFrom)
                .last("LIMIT " + DUE_SCAN_LIMIT));
    }

    /**
     * 生效并把目标对象更低版本置为作废。
     *
     * @param notice   变更通知
     * @param order    来源变更单
     * @param operator 操作人
     */
    private void applyObsolete(PdmChgNotice notice, PdmChgOrder order, Long operator) {
        List<PdmChgTask> tasks = chgTaskMapper.selectList(new LambdaQueryWrapper<PdmChgTask>()
                .eq(PdmChgTask::getEcoId, order.getId())
                .eq(PdmChgTask::getStatus, PdmChgTaskStatus.COMPLETED));
        for (PdmChgTask task : tasks) {
            if (task.getTargetId() == null || !StringUtils.hasText(task.getResultVersionNo())) {
                continue;
            }
            if (PdmChgObjectType.DOCUMENT.equals(task.getTargetType())) {
                obsoleteDocument(notice, task, operator);
            } else if (PdmChgObjectType.BOM.equals(task.getTargetType())) {
                obsoleteBom(notice, task, operator);
            }
        }
    }

    /**
     * 文档旧版本置作废。
     */
    private void obsoleteDocument(PdmChgNotice notice, PdmChgTask task, Long operator) {
        PdmDocVersion target = docVersionMapper.selectOne(new LambdaQueryWrapper<PdmDocVersion>()
                .eq(PdmDocVersion::getDocumentId, task.getTargetId())
                .eq(PdmDocVersion::getVersionNo, task.getResultVersionNo())
                .eq(PdmDocVersion::getStatus, DOC_RELEASED));
        if (target == null || target.getVersionSeq() == null) {
            log.warn("变更任务目标文档版本不存在，跳过作废旧版本: noticeNo={}, documentId={}, version={}",
                    notice.getNoticeNo(), task.getTargetId(), task.getResultVersionNo());
            return;
        }
        List<PdmDocVersion> older = docVersionMapper.selectList(new LambdaQueryWrapper<PdmDocVersion>()
                .eq(PdmDocVersion::getDocumentId, task.getTargetId())
                .eq(PdmDocVersion::getStatus, DOC_RELEASED)
                .lt(PdmDocVersion::getVersionSeq, target.getVersionSeq()));
        for (PdmDocVersion version : older) {
            version.setStatus(DOC_OBSOLETE);
            docVersionMapper.updateById(version);
            traceRecorder.recordObject(PdmChgBizType.ECN, notice.getId(), notice.getNoticeNo(),
                    PdmChgObjectType.DOCUMENT, task.getTargetId(), task.getTargetCode(),
                    PdmChgTraceAction.OBSOLETE, version.getVersionNo(), target.getVersionNo(),
                    operator, "变更生效，旧版本作废");
        }
    }

    /**
     * BOM 旧版本置作废。
     */
    private void obsoleteBom(PdmChgNotice notice, PdmChgTask task, Long operator) {
        PdmBom target = bomMapper.selectById(task.getTargetId());
        if (target == null || !BOM_RELEASED.equals(target.getStatus()) || target.getVersionSeq() == null) {
            log.warn("变更任务目标BOM版本不存在，跳过作废旧版本: noticeNo={}, bomId={}, version={}",
                    notice.getNoticeNo(), task.getTargetId(), task.getResultVersionNo());
            return;
        }
        List<PdmBom> older = bomMapper.selectList(new LambdaQueryWrapper<PdmBom>()
                .eq(PdmBom::getBomNo, target.getBomNo())
                .eq(PdmBom::getStatus, BOM_RELEASED)
                .lt(PdmBom::getVersionSeq, target.getVersionSeq()));
        for (PdmBom bom : older) {
            bom.setStatus(BOM_OBSOLETE);
            bomMapper.updateById(bom);
            traceRecorder.recordObject(PdmChgBizType.ECN, notice.getId(), notice.getNoticeNo(),
                    PdmChgObjectType.BOM, bom.getId(), bom.getBomNo(),
                    PdmChgTraceAction.OBSOLETE, bom.getVersionNo(), target.getVersionNo(),
                    operator, "变更生效，旧版本作废");
        }
    }

    /**
     * 发送通知并记录投递日志；任何异常都不向上抛出，保证生效事实已提交。
     *
     * @param notice    变更通知
     * @param eventType 事件类型
     */
    private void emitMessage(PdmChgNotice notice, String eventType) {
        String title = StringUtils.hasText(notice.getTitle()) ? notice.getTitle() : notice.getNoticeNo();
        String content = StringUtils.hasText(notice.getContent()) ? notice.getContent() : title;
        List<Long> targets = parseTargets(notice.getNotifyTargets());
        List<String> channels = parseChannels(notice.getNotifyChannels());
        PdmChgNotifyLog notifyLog = new PdmChgNotifyLog();
        notifyLog.setNoticeId(notice.getId());
        notifyLog.setEventType(eventType);
        notifyLog.setRecipients(notice.getNotifyTargets());
        notifyLog.setTitle(title);
        notifyLog.setContent(content);
        notifyLog.setRetryCount(0);
        notifyLog.setIdempotencyKey("ECN:" + notice.getId() + ":" + eventType);
        try {
            messageClient.send(TenantContext.get(), PdmChgBizType.ECN, String.valueOf(notice.getId()),
                    notifyLog.getIdempotencyKey(), title, content, channels, targets);
            notifyLog.setStatus(NOTIFY_SUCCESS);
        } catch (Exception ex) {
            notifyLog.setStatus(NOTIFY_FAILED);
            notifyLog.setLastError(truncate(ex.getMessage()));
            log.warn("变更通知投递失败: noticeNo={}, event={}, reason={}",
                    notice.getNoticeNo(), eventType, ex.getMessage());
        }
        try {
            notifyLogMapper.insert(notifyLog);
        } catch (Exception ex) {
            log.error("变更通知投递日志写入失败: noticeNo={}, event={}", notice.getNoticeNo(), eventType, ex);
        }
    }

    /**
     * 查询最近一次失败投递日志。
     *
     * @param noticeId 通知ID
     * @return 日志，不存在返回 null
     */
    private PdmChgNotifyLog latestFailedLog(Long noticeId) {
        List<PdmChgNotifyLog> logs = notifyLogMapper.selectList(new LambdaQueryWrapper<PdmChgNotifyLog>()
                .eq(PdmChgNotifyLog::getNoticeId, noticeId)
                .eq(PdmChgNotifyLog::getStatus, NOTIFY_FAILED)
                .orderByDesc(PdmChgNotifyLog::getId)
                .last("LIMIT 1"));
        return logs.isEmpty() ? null : logs.get(0);
    }

    /**
     * 批量解析变更单编号。
     *
     * @param ecoIds 变更单ID集合
     * @return ID → 编号
     */
    private Map<Long, String> ecoNoMap(Collection<Long> ecoIds) {
        if (ecoIds.isEmpty()) {
            return Map.of();
        }
        return chgOrderMapper.selectBatchIds(ecoIds).stream()
                .collect(Collectors.toMap(PdmChgOrder::getId, PdmChgOrder::getEcoNo, (left, right) -> left));
    }

    /**
     * 查询变更通知，不存在报参数错误。
     */
    private PdmChgNotice requireNotice(Long id) {
        PdmChgNotice entity = noticeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.change-notification-found", "变更通知不存在");
        }
        return entity;
    }

    /**
     * 查询指定状态的变更通知。
     */
    private PdmChgNotice requireStatus(Long id, String... allowed) {
        PdmChgNotice entity = requireNotice(id);
        for (String status : allowed) {
            if (status.equals(entity.getStatus())) {
                return entity;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.status-change-notification-allow-operation", "变更通知当前状态不允许该操作：{0}",
                      entity.getStatus());
    }

    /**
     * 校验来源变更单已审批通过。
     */
    private PdmChgOrder requireEcoReady(Long ecoId) {
        if (ecoId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.source-change-order-empty", "来源变更单不能为空");
        }
        PdmChgOrder order = chgOrderMapper.selectById(ecoId);
        if (order == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.source-change-order-found", "来源变更单不存在");
        }
        if (!ECO_READY_STATUSES.contains(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.change-order-approval-pass-cannot-creation", "变更单未审批通过，不能创建变更通知");
        }
        return order;
    }

    /**
     * 生效日期不得早于审批通过日。
     */
    private static void requireEffectiveFrom(LocalDateTime effectiveFrom, PdmChgOrder order) {
        if (effectiveFrom == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.effective-date-empty", "生效日期不能为空");
        }
        if (order.getApproveTime() != null && effectiveFrom.isBefore(order.getApproveTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.effective-date-earlier-than-approval-pass", "生效日期不能早于审批通过日");
        }
    }

    /**
     * 请求写入实体。
     */
    private static void applyRequest(PdmChgNotice entity, PdmChgNoticeSaveRequest request) {
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent());
        entity.setNotifyTargets(toJson(request.getNotifyTargets()));
        entity.setNotifyChannels(toChannelText(request.getNotifyChannels()));
        entity.setEffectiveFrom(request.getEffectiveFrom());
    }

    /**
     * 接收人转 JSON。
     */
    private static String toJson(List<Long> targets) {
        try {
            return OBJECT_MAPPER.writeValueAsString(targets == null ? List.of() : targets);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.recipient-invalid-format", "接收人格式不正确");
        }
    }

    /**
     * 渠道列表转逗号文本。
     */
    private static String toChannelText(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return DEFAULT_CHANNEL;
        }
        return channels.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 解析接收人 JSON。
     */
    private static List<Long> parseTargets(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception ex) {
            log.warn("变更通知接收人解析失败", ex);
            return List.of();
        }
    }

    /**
     * 解析通知渠道。
     */
    private static List<String> parseChannels(String channels) {
        if (!StringUtils.hasText(channels)) {
            return List.of(DEFAULT_CHANNEL);
        }
        return Arrays.stream(channels.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 解析影响对象快照。
     */
    private static List<PdmChgImpactObjectVO> parseImpact(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class,
                            PdmChgImpactObjectVO.class));
        } catch (Exception ex) {
            log.warn("变更通知影响对象解析失败", ex);
            return List.of();
        }
    }

    /**
     * 截断失败原因。
     */
    private static String truncate(String message) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    /**
     * 实体转视图。
     */
    private static PdmChgNoticeVO toVo(PdmChgNotice entity, String ecoNo) {
        PdmChgNoticeVO vo = new PdmChgNoticeVO();
        vo.setId(entity.getId());
        vo.setNoticeNo(entity.getNoticeNo());
        vo.setEcoId(entity.getEcoId());
        vo.setEcoNo(ecoNo);
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setImpactObjects(parseImpact(entity.getImpactJson()));
        vo.setNotifyTargets(parseTargets(entity.getNotifyTargets()));
        vo.setNotifyChannels(parseChannels(entity.getNotifyChannels()));
        vo.setEffectiveFrom(entity.getEffectiveFrom());
        vo.setStatus(entity.getStatus());
        vo.setNotifiedTime(entity.getNotifiedTime());
        vo.setEffectiveTime(entity.getEffectiveTime());
        vo.setCloseTime(entity.getCloseTime());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 日志实体转视图。
     */
    private static PdmChgNotifyLogVO toLogVo(PdmChgNotifyLog entity) {
        PdmChgNotifyLogVO vo = new PdmChgNotifyLogVO();
        vo.setId(entity.getId());
        vo.setNoticeId(entity.getNoticeId());
        vo.setEventType(entity.getEventType());
        vo.setTitle(entity.getTitle());
        vo.setStatus(entity.getStatus());
        vo.setRetryCount(entity.getRetryCount());
        vo.setLastError(entity.getLastError());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 生成通知编号：CN-时间戳-4 位随机数。
     */
    private static String newNoticeNo() {
        return NOTICE_NO_PREFIX + LocalDateTime.now().format(NO_TIME_FORMATTER) + "-"
                + ThreadLocalRandom.current().nextInt(1000, RANDOM_SUFFIX_BOUND);
    }
}
