package com.bluewhale.iot.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.iot.dto.CommandCreateRequest;
import com.bluewhale.iot.dto.CommandVO;
import com.bluewhale.iot.service.IotCommandService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指令接口：下发（幂等）与分页查询。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/iot")
@RequiredArgsConstructor
public class IotCommandController {

    private final IotCommandService commandService;

    /**
     * 下发指令（messageId 幂等，重复下发返回既有指令）。
     *
     * @param id      设备ID
     * @param request 指令请求
     * @param http    当前请求（取下发人）
     * @return 指令视图
     */
    @PostMapping("/devices/{id}/commands")
    @RequirePermission("sys:iot:device")
    @AuditLog(operation = "下发IoT指令", type = OperateType.CREATE, targetType = "iot_command")
    public Result<CommandVO> create(@PathVariable("id") Long id,
                                    @Valid @RequestBody CommandCreateRequest request,
                                    HttpServletRequest http) {
        return Result.ok(commandService.create(id, request, UserContext.findUserId(http)));
    }

    /**
     * 指令分页（设备/状态过滤）。
     *
     * @param deviceId 设备ID过滤（可空）
     * @param status   状态过滤（可空）
     * @param current  当前页
     * @param size     每页条数
     * @return 分页结果
     */
    @GetMapping("/commands/page")
    @RequirePermission("sys:iot:device")
    public Result<PageResult<CommandVO>> page(
            @RequestParam(value = "deviceId", required = false) Long deviceId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return Result.ok(commandService.page(deviceId, status, current, size));
    }
}
