package com.bluewhale.assistant.controller;

import com.bluewhale.assistant.dto.UsageVO;
import com.bluewhale.assistant.service.AsstUsageService;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用量看板接口（管理视角，助手×天聚合）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/assistant/v{version}/usage", version = "1")
@RequiredArgsConstructor
public class AsstUsageController {

    private final AsstUsageService usageService;

    /**
     * 用量分页。
     *
     * @param assistantId 助手过滤（可空）
     * @param startDate   起始日期（可空，yyyy-MM-dd）
     * @param endDate     结束日期（可空）
     * @param current     当前页
     * @param size        每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:assistant:define")
    public Result<PageResult<UsageVO>> page(
            @RequestParam(value = "assistantId", required = false) Long assistantId,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return Result.ok(usageService.page(assistantId, startDate, endDate, current, size));
    }
}
