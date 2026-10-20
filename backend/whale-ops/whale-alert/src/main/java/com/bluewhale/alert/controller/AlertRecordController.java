package com.bluewhale.alert.controller;

import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警记录查询接口
 *
 * <p>服务内路径 {@code /alert/record/**}；经网关按 {@code Path=/alert/**} 规则转发，
 * 外部路径与本文档一致。接口返回统一响应体 {@link Result}，受
 * {@code @RequirePermission("sys:alert:record:list")} 保护（由网关权限码头
 * {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/alert/record")
@RequiredArgsConstructor
public class AlertRecordController {

    private final AlertRecordService alertRecordService;

    /**
     * 分页查询告警记录
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:alert:record:list")
    public Result<PageResult<AlertRecordVO>> page(AlertRecordQueryRequest request) {
        return Result.ok(alertRecordService.page(request));
    }
}
