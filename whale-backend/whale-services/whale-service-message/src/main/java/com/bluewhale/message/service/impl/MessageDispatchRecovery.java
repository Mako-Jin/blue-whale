package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.message.config.MessageSendProperties;
import com.bluewhale.message.enums.MessageStatus;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.po.MessageRecordPo;
import com.bluewhale.message.service.MessageSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步投递补偿任务。
 *
 * <p>扫描长时间停留在 {@code PENDING} 的消息（进程崩溃、线程池拒绝等），以及
 * 抢占后超时未完成的 {@code DISPATCHING} 消息，按租户回填上下文后重新提交异步投递。
 * 真正投递前仍由 {@link MessageSendServiceImpl#dispatchAsync} 做条件更新抢占，
 * 因此多实例重复扫描不会导致重复投递。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageDispatchRecovery {

    /** 单次最多补偿的消息条数 */
    private static final int BATCH_SIZE = 100;

    private final MessageRecordMapper recordMapper;

    private final MessageSendService messageSendService;

    private final MessageSendProperties properties;

    /**
     * 定时补偿超时未投递的消息。
     */
    @Scheduled(fixedDelayString = "${whale.message.send.recover-interval-millis:60000}",
            initialDelayString = "${whale.message.send.recover-interval-millis:60000}")
    public void recover() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusSeconds(Math.max(1, properties.getRecoverThresholdSeconds()));
        int recovered = recordMapper.recoverStaleDispatching(deadline, now);
        if (recovered > 0) {
            log.warn("回收抢占超时的异步投递: count={}", recovered);
        }
        List<Long> tenantIds = recordMapper.selectPendingTenantIds(deadline);
        for (Long tenantId : tenantIds) {
            if (tenantId == null) {
                continue;
            }
            recoverTenant(tenantId, deadline);
        }
    }

    /**
     * 补偿单个租户的超时未投递消息。
     *
     * @param tenantId 租户ID
     * @param deadline 创建时间早于该值视为超时
     */
    private void recoverTenant(Long tenantId, LocalDateTime deadline) {
        TenantContext.set(tenantId);
        try {
            List<MessageRecordPo> pending = selectStalePending(deadline);
            if (!pending.isEmpty()) {
                log.info("补偿超时未投递消息: tenantId={}, count={}", tenantId, pending.size());
            }
            for (MessageRecordPo record : pending) {
                messageSendService.dispatchAsync(record.getId(), tenantId, record.getTraceId());
            }
        } catch (RuntimeException e) {
            log.error("异步投递补偿失败: tenantId={}, reason={}", tenantId, e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 查询当前租户超时未投递的消息。
     *
     * @param deadline 创建时间早于该值视为超时
     * @return 待补偿消息
     */
    private List<MessageRecordPo> selectStalePending(LocalDateTime deadline) {
        LambdaQueryWrapper<MessageRecordPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageRecordPo::getStatus, MessageStatus.PENDING.name())
                .lt(MessageRecordPo::getCreateTime, deadline)
                .orderByAsc(MessageRecordPo::getId)
                .last("LIMIT " + BATCH_SIZE);
        return recordMapper.selectList(wrapper);
    }
}
