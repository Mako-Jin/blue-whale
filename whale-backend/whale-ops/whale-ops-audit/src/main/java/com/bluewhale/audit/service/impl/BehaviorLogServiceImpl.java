package com.bluewhale.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.audit.converter.AuditLogConverter;
import com.bluewhale.audit.dto.BehaviorLogQueryRequest;
import com.bluewhale.audit.dto.LogBatchRequest;
import com.bluewhale.audit.dto.LogItem;
import com.bluewhale.audit.service.BehaviorLogService;
import com.bluewhale.audit.vo.BehaviorLogVO;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.log.entity.BehaviorLogRecord;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.log.mapper.BehaviorLogMapper;
import com.bluewhale.common.log.sink.LogPersistSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 前端用户行为埋点服务实现：接收落库（异步）+ 分页查询（REQ-SEC-006）。
 *
 * <p>落库经 {@link LogPersistSupport#submit(Runnable, String)}（channel {@code behavior}）投递到独立线程池，
 * 控制器线程零阻塞；为规避线程池/回调线程访问请求上下文，落库实体在提交前于当前线程完成组装。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorLogServiceImpl implements BehaviorLogService {

    /** 日志落库通道标识 */
    private static final String CHANNEL = "behavior";

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 单页最小条数 */
    private static final long MIN_PAGE_SIZE = 1L;

    private final BehaviorLogMapper behaviorLogMapper;

    private final LogPersistSupport logPersistSupport;

    @Override
    public void saveBatch(LogBatchRequest request, Long userIdFromGateway) {
        List<BehaviorLogRecord> records = toRecords(request, userIdFromGateway);
        if (records.isEmpty()) {
            return;
        }
        logPersistSupport.submit(() -> persistAll(records), CHANNEL);
    }

    @Override
    public PageResult<BehaviorLogVO> page(BehaviorLogQueryRequest request) {
        LambdaQueryWrapper<BehaviorLogRecord> wrapper = buildWrapper(request);
        wrapper.orderByDesc(BehaviorLogRecord::getCreateTime);
        Page<BehaviorLogRecord> page = new Page<>(request.getCurrent(), clampPageSize(request.getSize()));
        IPage<BehaviorLogRecord> result = behaviorLogMapper.selectPage(page, wrapper);
        List<BehaviorLogVO> records = result.getRecords().stream().map(BehaviorLogServiceImpl::toVo).toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 逐条落库；单条异常仅告警不冒泡（在线程池线程内执行，不访问请求上下文）
     *
     * @param records 已在本线程组装完成的行为埋点实体列表
     */
    private void persistAll(List<BehaviorLogRecord> records) {
        for (BehaviorLogRecord record : records) {
            try {
                behaviorLogMapper.insert(record);
            } catch (Exception e) {
                log.warn("行为埋点落库失败: traceId={}", record.getTraceId(), e);
            }
        }
    }

    /**
     * 上报条目转实体列表（当前线程执行，捕获全部上下文）
     *
     * @param request 批量上报请求
     * @param userId 生效用户ID（网关头优先，缺失时请求体兜底）
     * @return 行为埋点实体列表；无有效条目时返回空列表
     */
    private static List<BehaviorLogRecord> toRecords(LogBatchRequest request, Long userId) {
        List<LogItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(item -> toRecord(request, item, userId)).toList();
    }

    /**
     * 单个上报条目转实体
     *
     * @param request 批量上报请求（提供 traceId / clientId / pageUrl）
     * @param item 上报条目
     * @param userId 生效用户ID
     * @return 行为埋点实体
     */
    private static BehaviorLogRecord toRecord(LogBatchRequest request, LogItem item, Long userId) {
        BehaviorLogRecord record = new BehaviorLogRecord();
        record.setTraceId(request.getTraceId());
        record.setUserId(userId);
        record.setTenantId(TenantContext.get());
        record.setClientId(request.getClientId());
        record.setPageUrl(request.getPageUrl());
        record.setEventType(item.getEventType());
        record.setEventName(item.getEventName());
        record.setTarget(item.getTarget());
        record.setPayload(item.getPayload());
        if (item.getTimestamp() != null) {
            record.setCreateTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(item.getTimestamp()), ZoneId.systemDefault()));
        }
        return record;
    }

    /**
     * 按查询条件构造 wrapper：等值条件 + 时间范围
     * <p>条件均带「是否提供」开关，避免生成恒真/冗余条件导致索引失效。</p>
     *
     * @param request 查询条件
     * @return MyBatis-Plus 条件构造器
     */
    private static LambdaQueryWrapper<BehaviorLogRecord> buildWrapper(BehaviorLogQueryRequest request) {
        LambdaQueryWrapper<BehaviorLogRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getTraceId()), BehaviorLogRecord::getTraceId, request.getTraceId());
        wrapper.eq(request.getUserId() != null, BehaviorLogRecord::getUserId, request.getUserId());
        wrapper.eq(StringUtils.hasText(request.getClientId()), BehaviorLogRecord::getClientId, request.getClientId());
        wrapper.eq(StringUtils.hasText(request.getEventType()),
                BehaviorLogRecord::getEventType, request.getEventType());
        wrapper.eq(StringUtils.hasText(request.getEventName()),
                BehaviorLogRecord::getEventName, request.getEventName());
        wrapper.ge(request.getStartTime() != null, BehaviorLogRecord::getCreateTime, request.getStartTime());
        wrapper.le(request.getEndTime() != null, BehaviorLogRecord::getCreateTime, request.getEndTime());
        return wrapper;
    }

    /**
     * 单页条数收敛到 [1, 200]
     *
     * @param size 请求单页条数
     * @return 收敛后的单页条数
     */
    private static long clampPageSize(long size) {
        return Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    /**
     * 行为埋点实体转视图对象
     *
     * @param record 行为埋点实体
     * @return 行为埋点视图对象
     */
    private static BehaviorLogVO toVo(BehaviorLogRecord record) {
        return AuditLogConverter.INSTANCE.toBehaviorLogVo(record);
    }
}
