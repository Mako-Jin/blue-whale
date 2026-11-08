package com.bluewhale.oa.administrative.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.administrative.application.command.AdministrativeCommands;
import com.bluewhale.oa.administrative.application.view.AdministrativeViews;
import com.bluewhale.oa.administrative.domain.model.AdministrativeQuery;

/**
 * 用车应用服务。
 *
 * @author BlueWhale
 */
public interface VehicleAppService {

    /**
     * 分页查询车辆。
     *
     * @param query 查询条件
     * @return 车辆分页结果
     */
    PageResult<AdministrativeViews.VehicleView> pageVehicles(AdministrativeQuery query);

    /**
     * 查询车辆详情。
     *
     * @param id 车辆标识
     * @return 车辆视图
     */
    AdministrativeViews.VehicleView vehicleDetail(Long id);

    /**
     * 新增车辆。
     *
     * @param command 车辆保存命令
     * @return 车辆视图
     */
    AdministrativeViews.VehicleView createVehicle(AdministrativeCommands.VehicleSaveCommand command);

    /**
     * 修改车辆。
     *
     * @param id 车辆标识
     * @param command 车辆保存命令
     * @return 车辆视图
     */
    AdministrativeViews.VehicleView updateVehicle(Long id, AdministrativeCommands.VehicleSaveCommand command);

    /**
     * 删除车辆。
     *
     * @param id 车辆标识
     */
    void deleteVehicle(Long id);

    /**
     * 分页查询司机。
     *
     * @param query 查询条件
     * @return 司机分页结果
     */
    PageResult<AdministrativeViews.DriverView> pageDrivers(AdministrativeQuery query);

    /**
     * 新增司机。
     *
     * @param command 司机保存命令
     * @return 司机视图
     */
    AdministrativeViews.DriverView createDriver(AdministrativeCommands.DriverSaveCommand command);

    /**
     * 修改司机。
     *
     * @param id 司机标识
     * @param command 司机保存命令
     * @return 司机视图
     */
    AdministrativeViews.DriverView updateDriver(Long id, AdministrativeCommands.DriverSaveCommand command);

    /**
     * 删除司机。
     *
     * @param id 司机标识
     */
    void deleteDriver(Long id);

    /**
     * 分页查询用车申请。
     *
     * @param query 查询条件
     * @return 用车申请分页结果
     */
    PageResult<AdministrativeViews.BookingView> pageBookings(AdministrativeQuery query);

    /**
     * 查询用车申请详情。
     *
     * @param id 用车申请标识
     * @return 用车申请视图
     */
    AdministrativeViews.BookingView bookingDetail(Long id);

    /**
     * 新增用车申请。
     *
     * @param command 用车申请保存命令
     * @return 用车申请视图
     */
    AdministrativeViews.BookingView createBooking(AdministrativeCommands.BookingSaveCommand command);

    /**
     * 修改用车申请。
     *
     * @param id 用车申请标识
     * @param command 用车申请保存命令
     * @return 用车申请视图
     */
    AdministrativeViews.BookingView updateBooking(Long id, AdministrativeCommands.BookingSaveCommand command);

    /**
     * 删除用车申请。
     *
     * @param id 用车申请标识
     */
    void deleteBooking(Long id);

    /**
     * 更新用车申请状态。
     *
     * @param id 用车申请标识
     * @param command 状态变更命令
     */
    void updateBookingStatus(Long id, AdministrativeCommands.BookingStatusCommand command);

    /**
     * 派车。
     *
     * @param id 用车申请标识
     * @param command 派车命令
     * @return 用车申请视图
     */
    AdministrativeViews.BookingView dispatch(Long id, AdministrativeCommands.DispatchCommand command);

    /**
     * 归还车辆。
     *
     * @param id 用车申请标识
     * @param command 还车命令
     * @return 用车记录视图
     */
    AdministrativeViews.UsageView returnVehicle(Long id, AdministrativeCommands.ReturnCommand command);

    /**
     * 同步审批结果。
     *
     * @param bookingId 用车申请标识
     * @param result 审批结果
     */
    void syncApprovalResult(Long bookingId, String result);

    /**
     * 查询用车记录详情。
     *
     * @param bookingId 用车申请标识
     * @return 用车记录视图
     */
    AdministrativeViews.UsageView usageDetail(Long bookingId);
}
