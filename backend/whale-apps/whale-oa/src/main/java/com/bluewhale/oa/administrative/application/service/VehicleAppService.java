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
     * @param query 分页与筛选条件
     * @return 车辆分页结果
     */
    PageResult<AdministrativeViews.VehicleView> pageVehicles(AdministrativeQuery query);

    /**
     * 查询车辆详情。
     *
     * @param id 车辆 ID
     * @return 车辆详情
     */
    AdministrativeViews.VehicleView vehicleDetail(Long id);

    /**
     * 创建车辆。
     *
     * @param command 车辆保存命令
     * @return 创建后的车辆
     */
    AdministrativeViews.VehicleView createVehicle(AdministrativeCommands.VehicleSaveCommand command);

    /**
     * 更新车辆。
     *
     * @param id 车辆 ID
     * @param command 车辆保存命令
     * @return 更新后的车辆
     */
    AdministrativeViews.VehicleView updateVehicle(Long id, AdministrativeCommands.VehicleSaveCommand command);

    /**
     * 删除车辆。
     *
     * @param id 车辆 ID
     */
    void deleteVehicle(Long id);

    /**
     * 分页查询司机。
     *
     * @param query 分页与筛选条件
     * @return 司机分页结果
     */
    PageResult<AdministrativeViews.DriverView> pageDrivers(AdministrativeQuery query);

    /**
     * 创建司机。
     *
     * @param command 司机保存命令
     * @return 创建后的司机
     */
    AdministrativeViews.DriverView createDriver(AdministrativeCommands.DriverSaveCommand command);

    /**
     * 更新司机。
     *
     * @param id 司机 ID
     * @param command 司机保存命令
     * @return 更新后的司机
     */
    AdministrativeViews.DriverView updateDriver(Long id, AdministrativeCommands.DriverSaveCommand command);

    /**
     * 删除司机。
     *
     * @param id 司机 ID
     */
    void deleteDriver(Long id);

    /**
     * 分页查询用车申请。
     *
     * @param query 分页与筛选条件
     * @return 用车申请分页结果
     */
    PageResult<AdministrativeViews.BookingView> pageBookings(AdministrativeQuery query);

    /**
     * 查询用车申请详情。
     *
     * @param id 用车申请 ID
     * @return 用车申请详情
     */
    AdministrativeViews.BookingView bookingDetail(Long id);

    /**
     * 创建用车申请。
     *
     * @param command 用车申请保存命令
     * @return 创建后的用车申请
     */
    AdministrativeViews.BookingView createBooking(AdministrativeCommands.BookingSaveCommand command);

    /**
     * 更新用车申请。
     *
     * @param id 用车申请 ID
     * @param command 用车申请保存命令
     * @return 更新后的用车申请
     */
    AdministrativeViews.BookingView updateBooking(Long id, AdministrativeCommands.BookingSaveCommand command);

    /**
     * 删除用车申请。
     *
     * @param id 用车申请 ID
     */
    void deleteBooking(Long id);

    /**
     * 更新用车申请状态。
     *
     * @param id 用车申请 ID
     * @param command 状态变更命令
     */
    void updateBookingStatus(Long id, AdministrativeCommands.BookingStatusCommand command);

    /**
     * 派车调度。
     *
     * @param id 用车申请 ID
     * @param command 派车命令
     * @return 派车后的用车申请
     */
    AdministrativeViews.BookingView dispatch(Long id, AdministrativeCommands.DispatchCommand command);

    /**
     * 归还车辆。
     *
     * @param id 用车申请 ID
     * @param command 归还命令
     * @return 用车使用记录
     */
    AdministrativeViews.UsageView returnVehicle(Long id, AdministrativeCommands.ReturnCommand command);

    /**
     * 同步外部审批结果至用车申请。
     *
     * @param bookingId 用车申请 ID
     * @param result 审批结果
     */
    void syncApprovalResult(Long bookingId, String result);

    /**
     * 查询用车使用记录详情。
     *
     * @param bookingId 用车申请 ID
     * @return 用车使用记录
     */
    AdministrativeViews.UsageView usageDetail(Long bookingId);
}
