package com.bluewhale.mall.aftersale.application.job;

import com.bluewhale.mall.aftersale.application.service.AfterSaleAppService;
import com.bluewhale.mall.aftersale.domain.model.MallAfterSale;
import com.bluewhale.mall.config.MallProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 售后超时自动关闭任务。
 *
 * <p>用应用内 {@code @Scheduled} 而不是注册到 whale-job：关闭一张待审核售后单
 * 是售后域自己的状态机动作，只依赖本地两张表，走外部调度反而要为一个幂等的
 * 任务再维护一层远程回调。任务幂等：已关闭的售后单不再是 CREATED，重复扫描扫不到。</p>
 *
 * <p>逐单独立事务（{@link AfterSaleAppService#closeExpired}）：一单关闭失败
 * （如额度已被人动过）只跳过该单，下一轮再试，不拖垮整批。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfterSaleExpireJob {

    /** 首次执行延迟（毫秒）：等应用与连接池就绪再扫第一轮 */
    private static final long INITIAL_DELAY_MS = 60_000L;

    /** 扫描间隔（毫秒）：5 分钟 */
    private static final long SCAN_INTERVAL_MS = 300_000L;

    /** 单轮最多处理条数：避免积压时一轮占住调度线程太久 */
    private static final int SCAN_LIMIT = 200;

    private final AfterSaleAppService afterSaleAppService;

    private final MallProperties properties;

    /**
     * 定时扫描超时未审核的售后单并逐单关闭。
     */
    @Scheduled(initialDelay = INITIAL_DELAY_MS, fixedDelay = SCAN_INTERVAL_MS)
    public void closeExpired() {
        int expireHours = properties.getAfterSaleExpireHours();
        if (expireHours <= 0) {
            // 0 = 关闭开关
            return;
        }
        try {
            List<MallAfterSale> expired = afterSaleAppService.listExpiredCreated(expireHours, SCAN_LIMIT);
            if (expired.isEmpty()) {
                return;
            }
            int closed = 0;
            for (MallAfterSale afterSale : expired) {
                try {
                    afterSaleAppService.closeExpired(afterSale.getId());
                    closed++;
                } catch (RuntimeException exception) {
                    log.warn("售后超时自动关闭失败 afterSaleNo={} error={}",
                            afterSale.getAfterSaleNo(), exception.getMessage());
                }
            }
            log.debug("售后超时自动关闭：扫描 {} 单，关闭 {} 单", expired.size(), closed);
        } catch (RuntimeException exception) {
            log.warn("售后超时自动关闭任务异常终止: error={}", exception.getMessage());
        }
    }
}
