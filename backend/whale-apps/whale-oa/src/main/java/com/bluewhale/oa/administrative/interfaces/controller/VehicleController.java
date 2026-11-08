package com.bluewhale.oa.administrative.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.administrative.application.command.AdministrativeCommands;
import com.bluewhale.oa.administrative.application.service.VehicleAppService;
import com.bluewhale.oa.administrative.application.view.AdministrativeViews;
import com.bluewhale.oa.administrative.domain.model.AdministrativeQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OA 用车管理接口。
 *
 * <p>服务内路径 {@code /oa/vehicle/**}，受 {@code sys:oa:vehicle:*} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/vehicle")
@RequiredArgsConstructor
public class VehicleController {

    private static final String TARGET_TYPE = "oa_vehicle";

    private final VehicleAppService vehicleAppService;

    /**
     * 车辆分页查询。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:oa:vehicle:list")
    public Result<PageResult<AdministrativeViews.VehicleView>> page(AdministrativeQuery query) {
        return Result.ok(vehicleAppService.pageVehicles(query));
    }

    /**
     * 车辆详情。
     *
     * @param id 车辆ID
     * @return 车辆视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:oa:vehicle:list")
    public Result<AdministrativeViews.VehicleView> detail(@PathVariable Long id) {
        return Result.ok(vehicleAppService.vehicleDetail(id));
    }

    /**
     * 新增车辆。
     *
     * @param command 保存命令
     * @return 车辆视图
     */
    @PostMapping
    @RequirePermission("sys:oa:vehicle:add")
    @AuditLog(operation = "新增车辆", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<AdministrativeViews.VehicleView> create(
            @Valid @RequestBody AdministrativeCommands.VehicleSaveCommand command) {
        return Result.ok(vehicleAppService.createVehicle(command));
    }

    /**
     * 更新车辆。
     *
     * @param id 车辆ID
     * @param command 保存命令
     * @return 车辆视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:oa:vehicle:edit")
    @AuditLog(operation = "更新车辆", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<AdministrativeViews.VehicleView> update(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.VehicleSaveCommand command) {
        return Result.ok(vehicleAppService.updateVehicle(id, command));
    }

    /**
     * 删除车辆。
     *
     * @param id 车辆ID
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:oa:vehicle:delete")
    @AuditLog(operation = "删除车辆", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        vehicleAppService.deleteVehicle(id);
        return Result.ok();
    }

    /**
     * 司机分页查询。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/drivers/page")
    @RequirePermission("sys:oa:vehicle:list")
    public Result<PageResult<AdministrativeViews.DriverView>> driverPage(AdministrativeQuery query) {
        return Result.ok(vehicleAppService.pageDrivers(query));
    }

    /**
     * 新增司机。
     *
     * @param command 保存命令
     * @return 司机视图
     */
    @PostMapping("/drivers")
    @RequirePermission("sys:oa:vehicle:add")
    @AuditLog(operation = "新增司机", type = OperateType.CREATE, targetType = "oa_driver")
    public Result<AdministrativeViews.DriverView> createDriver(
            @Valid @RequestBody AdministrativeCommands.DriverSaveCommand command) {
        return Result.ok(vehicleAppService.createDriver(command));
    }

    /**
     * 更新司机。
     *
     * @param id 司机ID
     * @param command 保存命令
     * @return 司机视图
     */
    @PutMapping("/drivers/{id}")
    @RequirePermission("sys:oa:vehicle:edit")
    @AuditLog(operation = "更新司机", type = OperateType.UPDATE, targetType = "oa_driver", targetId = "#id")
    public Result<AdministrativeViews.DriverView> updateDriver(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.DriverSaveCommand command) {
        return Result.ok(vehicleAppService.updateDriver(id, command));
    }

    /**
     * 删除司机。
     *
     * @param id 司机ID
     */
    @DeleteMapping("/drivers/{id}")
    @RequirePermission("sys:oa:vehicle:delete")
    @AuditLog(operation = "删除司机", type = OperateType.DELETE, targetType = "oa_driver", targetId = "#id")
    public Result<Void> deleteDriver(@PathVariable Long id) {
        vehicleAppService.deleteDriver(id);
        return Result.ok();
    }

    /**
     * 用车申请分页查询。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/bookings/page")
    @RequirePermission("sys:oa:vehicle:list")
    public Result<PageResult<AdministrativeViews.BookingView>> bookingPage(AdministrativeQuery query) {
        return Result.ok(vehicleAppService.pageBookings(query));
    }

    /**
     * 用车申请详情。
     *
     * @param id 用车申请ID
     * @return 用车申请视图
     */
    @GetMapping("/bookings/{id}")
    @RequirePermission("sys:oa:vehicle:list")
    public Result<AdministrativeViews.BookingView> bookingDetail(@PathVariable Long id) {
        return Result.ok(vehicleAppService.bookingDetail(id));
    }

    /**
     * 新建用车申请。
     *
     * @param command 保存命令
     * @return 用车申请视图
     */
    @PostMapping("/bookings")
    @RequirePermission("sys:oa:vehicle:booking:add")
    @AuditLog(operation = "新建用车申请", type = OperateType.CREATE, targetType = "oa_vehicle_booking")
    public Result<AdministrativeViews.BookingView> createBooking(
            @Valid @RequestBody AdministrativeCommands.BookingSaveCommand command) {
        return Result.ok(vehicleAppService.createBooking(command));
    }

    /**
     * 更新用车申请。
     *
     * @param id 用车申请ID
     * @param command 保存命令
     * @return 用车申请视图
     */
    @PutMapping("/bookings/{id}")
    @RequirePermission("sys:oa:vehicle:booking:edit")
    @AuditLog(operation = "更新用车申请", type = OperateType.UPDATE, targetType = "oa_vehicle_booking",
            targetId = "#id")
    public Result<AdministrativeViews.BookingView> updateBooking(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.BookingSaveCommand command) {
        return Result.ok(vehicleAppService.updateBooking(id, command));
    }

    /**
     * 删除用车申请。
     *
     * @param id 用车申请ID
     */
    @DeleteMapping("/bookings/{id}")
    @RequirePermission("sys:oa:vehicle:booking:edit")
    @AuditLog(operation = "删除用车申请", type = OperateType.DELETE, targetType = "oa_vehicle_booking",
            targetId = "#id")
    public Result<Void> deleteBooking(@PathVariable Long id) {
        vehicleAppService.deleteBooking(id);
        return Result.ok();
    }

    /**
     * 变更用车申请状态。
     *
     * @param id 用车申请ID
     * @param command 状态变更命令
     */
    @PutMapping("/bookings/{id}/status")
    @RequirePermission("sys:oa:vehicle:booking:edit")
    @AuditLog(operation = "变更用车状态", type = OperateType.UPDATE, targetType = "oa_vehicle_booking",
            targetId = "#id")
    public Result<Void> updateBookingStatus(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.BookingStatusCommand command) {
        vehicleAppService.updateBookingStatus(id, command);
        return Result.ok();
    }

    /**
     * 用车派车。
     *
     * @param id 用车申请ID
     * @param command 派车命令
     * @return 用车申请视图
     */
    @PostMapping("/bookings/{id}/dispatch")
    @RequirePermission("sys:oa:vehicle:dispatch")
    @AuditLog(operation = "派车", type = OperateType.UPDATE, targetType = "oa_vehicle_booking", targetId = "#id")
    public Result<AdministrativeViews.BookingView> dispatch(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.DispatchCommand command) {
        return Result.ok(vehicleAppService.dispatch(id, command));
    }

    /**
     * 回车登记。
     *
     * @param id 用车申请ID
     * @param command 回车命令
     * @return 用车记录视图
     */
    @PostMapping("/bookings/{id}/return")
    @RequirePermission("sys:oa:vehicle:return")
    @AuditLog(operation = "回车登记", type = OperateType.UPDATE, targetType = "oa_vehicle_booking",
            targetId = "#id")
    public Result<AdministrativeViews.UsageView> returnVehicle(
            @PathVariable Long id, @Valid @RequestBody AdministrativeCommands.ReturnCommand command) {
        return Result.ok(vehicleAppService.returnVehicle(id, command));
    }

    /**
     * 查询用车记录详情。
     *
     * @param id 用车申请ID
     * @return 用车记录视图
     */
    @GetMapping("/bookings/{id}/usage")
    @RequirePermission("sys:oa:vehicle:list")
    public Result<AdministrativeViews.UsageView> usage(@PathVariable Long id) {
        return Result.ok(vehicleAppService.usageDetail(id));
    }
}
