package com.bluewhale.crm.customer.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerPoolAppService;
import com.bluewhale.crm.customer.application.view.PoolRecycleResultView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 公海自动回收调度触发控制器（仅内网：whale-job 调度中心）。
 *
 * <p>原进程内 {@code @Scheduled}（pool-recycle-cron，{@code @ConditionalOnProperty}
 * 门控 Bean 注册）已于 2026-11-09 第十一批迁移至 whale-job（job_info 1009，经
 * httpInvokeJobHandler 服务发现直连本控制器）；回收开关改在端点内运行期判断，
 * 多租户部署可由调度平台逐租户扩展 handler_param。当前按默认租户执行。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm/internal/customer-pool")
@RequiredArgsConstructor
public class CustomerPoolInternalController {

    private final CustomerPoolAppService poolAppService;

    private final CrmProperties crmProperties;

    /**
     * 按公海规则回收客户。
     *
     * @return 回收条数；公海回收未启用时返回 -1（跳过）
     */
    @PostMapping("/recycle")
    public Result<Integer> recycle() {
        if (!crmProperties.isPoolRecycleEnabled()) {
            return Result.ok(-1);
        }
        PoolRecycleResultView result = poolAppService.recycle(systemActor());
        return Result.ok(result.getRecycledCount());
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
