package com.bluewhale.iot.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.iot.dto.DeviceCreateRequest;
import com.bluewhale.iot.dto.DeviceCreateVO;
import com.bluewhale.iot.dto.DeviceSecretResetVO;
import com.bluewhale.iot.dto.DeviceUpdateRequest;
import com.bluewhale.iot.dto.DeviceVO;
import com.bluewhale.iot.service.IotDeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备接口：注册（三元组签发）/更新/密钥重置/删除与分页查询。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/iot/v{version}/devices", version = "1")
@RequiredArgsConstructor
public class IotDeviceController {

    private final IotDeviceService deviceService;

    /**
     * 注册设备（secret 仅本次响应返回一次）。
     *
     * @param request 创建请求
     * @return 含一次性 secret 的视图对象
     */
    @PostMapping
    @RequirePermission("sys:iot:device")
    @AuditLog(operation = "注册IoT设备", type = OperateType.CREATE, targetType = "iot_device")
    public Result<DeviceCreateVO> create(@Valid @RequestBody DeviceCreateRequest request) {
        return Result.ok(deviceService.create(request));
    }

    /**
     * 设备分页。
     *
     * @param productId 产品过滤（可空）
     * @param keyword   名称/昵称模糊过滤（可空）
     * @param current   当前页
     * @param size      每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:iot:device")
    public Result<PageResult<DeviceVO>> page(
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return Result.ok(deviceService.page(productId, keyword, current, size));
    }

    /**
     * 设备详情（不回显 secret）。
     *
     * @param id 设备ID
     * @return 视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:iot:device")
    public Result<DeviceVO> get(@PathVariable("id") Long id) {
        return Result.ok(deviceService.get(id));
    }

    /**
     * 更新设备（昵称/状态）。
     *
     * @param id      设备ID
     * @param request 更新请求
     * @return 视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:iot:device")
    @AuditLog(operation = "更新IoT设备", type = OperateType.UPDATE, targetType = "iot_device")
    public Result<DeviceVO> update(@PathVariable("id") Long id,
                                   @Valid @RequestBody DeviceUpdateRequest request) {
        return Result.ok(deviceService.update(id, request));
    }

    /**
     * 重置设备密钥（旧 secret 立即失效；新 secret 仅本次返回）。
     *
     * @param id 设备ID
     * @return 含一次性新 secret 的视图对象
     */
    @PostMapping("/{id}/secret")
    @RequirePermission("sys:iot:device")
    @AuditLog(operation = "重置IoT设备密钥", type = OperateType.UPDATE, targetType = "iot_device")
    public Result<DeviceSecretResetVO> resetSecret(@PathVariable("id") Long id) {
        return Result.ok(deviceService.resetSecret(id));
    }

    /**
     * 删除设备（逻辑删除）。
     *
     * @param id 设备ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:iot:device")
    @AuditLog(operation = "删除IoT设备", type = OperateType.DELETE, targetType = "iot_device")
    public Result<Void> delete(@PathVariable("id") Long id) {
        deviceService.delete(id);
        return Result.ok();
    }
}
