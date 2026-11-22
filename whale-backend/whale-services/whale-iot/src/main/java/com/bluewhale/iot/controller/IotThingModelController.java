package com.bluewhale.iot.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.iot.dto.ThingModelSaveRequest;
import com.bluewhale.iot.dto.ThingModelVO;
import com.bluewhale.iot.service.IotThingModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物模型接口：整包覆盖式保存与查询（挂在产品资源下）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/iot/v{version}/products/{productId}/thing-model", version = "1")
@RequiredArgsConstructor
public class IotThingModelController {

    private final IotThingModelService thingModelService;

    /**
     * 查询产品物模型全集。
     *
     * @param productId 产品ID
     * @return 物模型视图列表
     */
    @GetMapping
    @RequirePermission("sys:iot:product")
    public Result<List<ThingModelVO>> list(@PathVariable("productId") Long productId) {
        return Result.ok(thingModelService.listByProduct(productId));
    }

    /**
     * 整包覆盖式保存物模型（已发布产品拒绝；空列表 = 清空）。
     *
     * @param productId 产品ID
     * @param request   保存请求
     * @return 保存后的物模型视图
     */
    @PutMapping
    @RequirePermission("sys:iot:product")
    @AuditLog(operation = "保存IoT物模型", type = OperateType.UPDATE, targetType = "iot_thing_model")
    public Result<List<ThingModelVO>> replaceAll(@PathVariable("productId") Long productId,
                                                 @Valid @RequestBody ThingModelSaveRequest request) {
        return Result.ok(thingModelService.replaceAll(productId, request));
    }
}
