package com.bluewhale.sts.app.health;

import com.bluewhale.sts.common.basic.model.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 *
 * <p>返回体统一套用 {@link R}，与业务接口保持同一形状 ——
 * 若健康检查自成一格，前端就得为它写一套特殊解析逻辑。
 *
 * <p><b>注意：{@code code=0} 只表示"这次探测成功执行"，不等于"服务健康"。</b>
 * 依赖不可用时，{@code code} 仍为 0，但 {@code data.status} 为 {@code DOWN}。
 * 这两层语义必须分开：把依赖故障表达成业务错误码，会让调用方以为是自己请求错了。
 */
@RestController
@RequestMapping("/api")
@Tag(name = "系统", description = "服务自身状态")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    @Operation(summary = "服务健康检查", description = "逐依赖探测并聚合；依赖不可用时 data.status 为 DOWN")
    public R<HealthVO> health() {
        return R.ok(healthService.check());
    }

}
