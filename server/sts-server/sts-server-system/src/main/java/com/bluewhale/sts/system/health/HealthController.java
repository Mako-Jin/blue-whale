package com.bluewhale.sts.system.health;

import com.bluewhale.sts.common.basic.model.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 *
 * <p>返回体统一套用 {@link R}，与业务接口保持同一形状 ——
 * 若健康检查自成一格，前端就得为它写一套特殊解析逻辑。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public R<HealthVO> health() {
        return R.ok(healthService.check());
    }

}
