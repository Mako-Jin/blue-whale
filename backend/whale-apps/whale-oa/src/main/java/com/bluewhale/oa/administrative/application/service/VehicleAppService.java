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

    PageResult<AdministrativeViews.VehicleView> pageVehicles(AdministrativeQuery query);

    AdministrativeViews.VehicleView vehicleDetail(Long id);

    AdministrativeViews.VehicleView createVehicle(AdministrativeCommands.VehicleSaveCommand command);

    AdministrativeViews.VehicleView updateVehicle(Long id, AdministrativeCommands.VehicleSaveCommand command);

    void deleteVehicle(Long id);

    PageResult<AdministrativeViews.DriverView> pageDrivers(AdministrativeQuery query);

    AdministrativeViews.DriverView createDriver(AdministrativeCommands.DriverSaveCommand command);

    AdministrativeViews.DriverView updateDriver(Long id, AdministrativeCommands.DriverSaveCommand command);

    void deleteDriver(Long id);

    PageResult<AdministrativeViews.BookingView> pageBookings(AdministrativeQuery query);

    AdministrativeViews.BookingView bookingDetail(Long id);

    AdministrativeViews.BookingView createBooking(AdministrativeCommands.BookingSaveCommand command);

    AdministrativeViews.BookingView updateBooking(Long id, AdministrativeCommands.BookingSaveCommand command);

    void deleteBooking(Long id);

    void updateBookingStatus(Long id, AdministrativeCommands.BookingStatusCommand command);

    AdministrativeViews.BookingView dispatch(Long id, AdministrativeCommands.DispatchCommand command);

    AdministrativeViews.UsageView returnVehicle(Long id, AdministrativeCommands.ReturnCommand command);

    void syncApprovalResult(Long bookingId, String result);

    AdministrativeViews.UsageView usageDetail(Long bookingId);
}
