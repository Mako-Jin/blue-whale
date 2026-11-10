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
 * 公海回收内网运维入口（仅内网：内部令牌 + 网关 internal-deny）。
 *
 * <p><b>定时调度不经过本控制器</b>：公海自动回收的调度行是
 * {@code job_code=crm.pool.recycle} + {@code httpJobHandler}，由调度中心按执行器寻址后调
 * {@code POST /internal/job/{code}/run}，进入 {@code CrmPoolRecycleJob} 的
 * {@code @ScheduledJob} 方法并直接调用应用服务（开关
 * {@code whale.crm.pool-recycle-enabled} 在方法内判断，关闭返回 skipped）。
 * 本控制器只是同一应用服务的手工触发入口（排查 / 补跑）；早期 job_info 1009 走
 * {@code httpInvokeJobHandler} 直连此处，该通道已于 2026-11-09 合并收口
 * （见 {@code V13__disable_legacy_http_invoke_jobs.sql}）。</p>
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
