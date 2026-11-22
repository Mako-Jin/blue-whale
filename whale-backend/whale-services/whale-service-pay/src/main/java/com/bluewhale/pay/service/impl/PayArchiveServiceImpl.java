package com.bluewhale.pay.service.impl;

import com.bluewhale.pay.config.PayArchiveProperties;
import com.bluewhale.pay.mapper.PayArchiveMapper;
import com.bluewhale.pay.service.PayArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 支付数据归档实现。
 *
 * <p>按「先搬后删、分批执行」推进：每批 {@code INSERT IGNORE ... SELECT} 后
 * {@code DELETE ... LIMIT}，批间不持有长事务。中断（实例重启/锁超时）后重跑时，
 * 已搬迁的行被历史表主键忽略，随后照常删除，因此不丢数据也不重复。
 * 单轮受 {@code max-batches-per-run} 限制，避免大表一次归档占满数据库。</p>
 *
 * <p>{@code pay_notify_task} 只归档 {@code SUCCESS}：{@code PENDING} 尚未投递成功、
 * {@code DEAD} 需人工介入，两者都必须留在热表。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayArchiveServiceImpl implements PayArchiveService {

    private final PayArchiveMapper payArchiveMapper;

    private final PayArchiveProperties properties;

    @Override
    @Scheduled(cron = "${whale.pay.archive.cron:0 0 3 * * ?}")
    @SchedulerLock(name = "pay-archive", lockAtMostFor = "30m")
    public int archive() {
        LocalDateTime now = LocalDateTime.now();
        int transactions = 0;
        int notifyTasks = 0;
        try {
            transactions = archivePayTransactions(now.minusDays(properties.getTransactionRetentionDays()));
        } catch (RuntimeException ex) {
            log.error("支付流水归档失败，数据保留待下次重试", ex);
        }
        try {
            notifyTasks = archiveNotifyTasks(now.minusDays(properties.getNotifySuccessRetentionDays()));
        } catch (RuntimeException ex) {
            log.error("成功通知归档失败，数据保留待下次重试", ex);
        }
        if (transactions > 0 || notifyTasks > 0) {
            log.info("数据归档完成: transactions={} notifyTasks={}", transactions, notifyTasks);
        }
        return transactions + notifyTasks;
    }

    /**
     * 分批归档支付流水。
     *
     * @param before 创建时间早于该时刻
     * @return 搬迁行数
     */
    private int archivePayTransactions(LocalDateTime before) {
        int total = 0;
        for (int i = 0; i < properties.getMaxBatchesPerRun(); i++) {
            payArchiveMapper.archiveTransactions(before, properties.getBatchSize());
            int deleted = payArchiveMapper.deleteArchivedTransactions(before, properties.getBatchSize());
            total += deleted;
            if (deleted < properties.getBatchSize()) {
                break;
            }
        }
        return total;
    }

    /**
     * 分批归档成功通知任务。
     *
     * @param before 创建时间早于该时刻
     * @return 搬迁行数
     */
    private int archiveNotifyTasks(LocalDateTime before) {
        int total = 0;
        for (int i = 0; i < properties.getMaxBatchesPerRun(); i++) {
            payArchiveMapper.archiveNotifyTasks(before, properties.getBatchSize());
            int deleted = payArchiveMapper.deleteArchivedNotifyTasks(before, properties.getBatchSize());
            total += deleted;
            if (deleted < properties.getBatchSize()) {
                break;
            }
        }
        return total;
    }
}
