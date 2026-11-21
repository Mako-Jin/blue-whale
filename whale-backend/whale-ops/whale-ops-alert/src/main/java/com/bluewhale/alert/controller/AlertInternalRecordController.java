package com.bluewhale.alert.controller;

import com.bluewhale.alert.dto.AlertRecordCreateRequest;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内网告警创建接口（供其他服务的规则动作调用）。
 *
 * <p>路径位于 {@code /alert/v1/internal/**}：网关对该前缀统一 403，仅允许内网直连；
 * 服务侧由 starter 的 {@code InternalTokenFilter} 校验 {@code X-Internal-Token}。
 * 与 Prometheus 入站接口的区别是本接口接收「已判定触发」的告警，不做条件评估。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@RestController
@RequestMapping("/alert/v1/internal")
@RequiredArgsConstructor
public class AlertInternalRecordController {

    private final AlertRecordService alertRecordService;

    /**
     * 创建一条触发中告警记录。
     *
     * @param request 创建请求
     * @return 落库条数（0 表示窗口内重复被去重）
     */
    @PostMapping("/record")
    public Result<Integer> create(@Valid @RequestBody AlertRecordCreateRequest request) {
        int inserted = alertRecordService.create(request);
        log.info("内网告警创建完成: source={}, ruleCode={}, inserted={}",
                request == null ? null : request.getSource(),
                request == null ? null : request.getRuleCode(), inserted);
        return Result.ok(inserted);
    }
}
