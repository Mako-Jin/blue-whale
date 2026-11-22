package com.bluewhale.pay.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pay.channel.PayChannelGateway;
import com.bluewhale.pay.config.PayCloseProperties;
import com.bluewhale.pay.enums.PayChannelFeature;
import com.bluewhale.pay.enums.PayCloseReason;
import com.bluewhale.pay.enums.PayOrderStatus;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.mapper.stat.PayStaleOrderPo;
import com.bluewhale.pay.po.PayOrderPo;
import com.bluewhale.pay.service.PayCloseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付超时自动关单实现。
 *
 * <p><b>只关 {@code CREATED}</b>：{@code PAYING} 已发往渠道，结果未定，交给停留单对账；
 * 直接关闭会出现「渠道已扣款、本地已取消」。</p>
 *
 * <p><b>渠道关单失败不阻塞扫描</b>：失败单记录 {@code close_attempts} 与
 * {@code next_close_at}，按指数退避等下一轮，本轮继续处理后续支付单。</p>
 *
 * <p><b>租户上下文</b>：定时线程没有租户，扫描跨租户取 ID + tenant_id 后逐单还原，
 * 否则条件更新会落错租户或跳过租户插件。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayCloseServiceImpl implements PayCloseService {

    /** 退避指数上限，避免移位溢出 */
    private static final int MAX_BACKOFF_SHIFT = 8;

    private final PayOrderMapper payOrderMapper;

    private final PayChannelGateway payChannelGateway;

    private final PayCloseProperties properties;

    @Override
    @Scheduled(fixedDelayString = "${whale.pay.close.interval-ms:300000}")
    public int closeTimeoutOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime before = now.minusMinutes(properties.getTimeoutMinutes());
        List<PayStaleOrderPo> stale =
                payOrderMapper.selectTimeoutCreated(before, now, properties.getBatchSize());
        int closed = 0;
        for (PayStaleOrderPo item : stale) {
            TenantContext.set(item.getTenantId());
            try {
                if (closeOne(item.getId())) {
                    closed++;
                }
            } catch (RuntimeException ex) {
                // 单笔异常不中断整轮：下一轮继续尝试，避免一笔坏单拖住全部超时单
                log.error("超时关单异常: id={} tenantId={}", item.getId(), item.getTenantId(), ex);
            } finally {
                TenantContext.clear();
            }
        }
        if (!stale.isEmpty() && log.isInfoEnabled()) {
            log.info("超时关单轮次完成: scanned={} closed={}", stale.size(), closed);
        }
        return closed;
    }

    /**
     * 关闭单笔超时支付单。
     *
     * @param id 支付单 ID
     * @return 是否本次关闭
     */
    private boolean closeOne(Long id) {
        PayOrderPo order = payOrderMapper.selectById(id);
        if (order == null || !PayOrderStatus.CREATED.code().equals(order.getStatus())) {
            // 扫描后已被支付/取消，跳过
            return false;
        }
        if (payChannelGateway.supports(order.getChannel(), PayChannelFeature.CLOSE)) {
            try {
                payChannelGateway.close(order);
            } catch (RuntimeException ex) {
                scheduleRetry(order, ex);
                return false;
            }
        }
        if (payOrderMapper.closeIfCreated(id, PayCloseReason.TIMEOUT.code()) == 0) {
            log.info("超时关单跳过：支付单已被并发推进 orderNo={} status={}", order.getOrderNo(),
                    order.getStatus());
            return false;
        }
        if (log.isInfoEnabled()) {
            log.info("超时自动关单成功: orderNo={} channel={}", order.getOrderNo(), order.getChannel());
        }
        return true;
    }

    /**
     * 渠道关单失败：按指数退避安排下次重试。
     *
     * @param order 支付单
     * @param ex    失败原因
     */
    private void scheduleRetry(PayOrderPo order, RuntimeException ex) {
        int attempts = order.getCloseAttempts() == null ? 0 : order.getCloseAttempts();
        long seconds = Math.min(properties.getBackoffSeconds() << Math.min(attempts, MAX_BACKOFF_SHIFT),
                properties.getMaxBackoffSeconds());
        try {
            payOrderMapper.markCloseFailure(order.getId(), LocalDateTime.now().plusSeconds(seconds));
        } catch (RuntimeException dbEx) {
            log.error("关单失败后写重试时间失败，下一轮仍会重扫: orderNo={}", order.getOrderNo(), dbEx);
        }
        log.warn("渠道关单失败，已安排重试: orderNo={} channel={} attempts={} backoff={}s error={}",
                order.getOrderNo(), order.getChannel(), attempts + 1, seconds, ex.getMessage());
    }
}
