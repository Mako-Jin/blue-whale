package com.bluewhale.audit.controller;

import com.bluewhale.audit.mq.support.AuditDlqReplayService;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计 MQ 运维接口（AUTH-10）。
 *
 * <p>路径含 {@code internal} 段，命中内网令牌过滤器（{@code /**}{@code /internal/**}）
 * 与网关 {@code internal-deny}，外部不可达。仅在 {@code whale.audit.mq.consume-enabled=true} 时装配。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/audit/internal/mq")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.audit.mq", name = "consume-enabled", havingValue = "true")
public class AuditMqAdminController {

    private final AuditDlqReplayService replayService;

    /**
     * 重放死信队列消息（单次最多 100 条）。
     *
     * <p>先修复毒消息根因再重放；消息属性（含 messageId）原样保留，重复落库由去重表兜底。</p>
     *
     * @param limit 期望重放条数，默认 50，收敛到 {@code [1, 100]}
     * @return 实际重放条数
     */
    @PostMapping("/dlq/replay")
    public Result<Integer> replayDlq(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(replayService.replay(limit));
    }
}
