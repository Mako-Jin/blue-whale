package com.bluewhale.crm.customer.interfaces.scheduler;

import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.customer.application.service.CustomerPoolAppService;
import com.bluewhale.crm.customer.application.view.PoolRecycleResultView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 公海自动回收定时任务。
 *
 * <p>默认关闭，需显式设置 {@code whale.crm.pool-recycle-enabled=true} 才注册。
 * 当前按默认租户执行；多租户部署需由调度平台逐租户触发，或改造为按租户遍历。</p>
 *
 * @author BlueWhale
 */
@Component
@ConditionalOnProperty(prefix = "whale.crm", name = "pool-recycle-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CrmPoolRecycleJob {

    private final CustomerPoolAppService poolAppService;

    /**
     * 按公海规则回收客户。
     */
    @Scheduled(cron = "${whale.crm.pool-recycle-cron:0 0 2 * * ?}")
    public void recycle() {
        try {
            PoolRecycleResultView result = poolAppService.recycle(systemActor());
            log.info("CRM 公海自动回收完成：rule={}, recycled={}", result.getRuleName(), result.getRecycledCount());
        } catch (RuntimeException exception) {
            log.warn("CRM 公海自动回收失败：{}", exception.getMessage(), exception);
        }
    }

    /**
     * 构造系统操作人（无用户 ID，具备全量数据范围与管理员角色）。
     *
     * @return 系统操作人
     */
    private CrmActor systemActor() {
        return new CrmActor(null, "system", Set.of("crm_admin"), Set.of());
    }
}
