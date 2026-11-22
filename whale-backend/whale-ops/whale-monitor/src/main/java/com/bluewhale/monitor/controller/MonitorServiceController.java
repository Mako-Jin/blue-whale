package com.bluewhale.monitor.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.monitor.service.MonitorService;
import com.bluewhale.monitor.vo.ServiceInstanceVO;
import com.bluewhale.monitor.vo.ServiceSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务监控查询接口
 *
 * <p>服务内路径 {@code /monitor/service/**}；经网关按 {@code Path=/monitor/**} 规则转发，
 * 外部路径与本文档一致。全部接口返回统一响应体 {@link Result}，受
 * {@code @RequirePermission("sys:monitor:list")} 保护（由网关权限码头 {@code X-Permission-Codes}
 * + starter {@code RequirePermissionInterceptor} 判定）。数据来源为 Nacos 注册中心
 * {@code DiscoveryClient}，本服务无数据库。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/monitor/v{version}/service", version = "1")
@RequiredArgsConstructor
public class MonitorServiceController {

    private final MonitorService monitorService;

    /**
     * 查询全部已注册服务的聚合概览（服务名 + 实例数 + 健康实例数）
     *
     * @return 服务概览列表
     */
    @GetMapping("/list")
    @RequirePermission("sys:monitor:list")
    public Result<List<ServiceSummaryVO>> list() {
        return Result.ok(monitorService.listServices());
    }

    /**
     * 查询指定服务的实例列表
     *
     * @param serviceName 服务名
     * @return 实例列表；服务未注册时抛 {@code NOT_FOUND}
     */
    @GetMapping("/{serviceName}/instances")
    @RequirePermission("sys:monitor:list")
    public Result<List<ServiceInstanceVO>> instances(@PathVariable String serviceName) {
        return Result.ok(monitorService.listInstances(serviceName));
    }
}
