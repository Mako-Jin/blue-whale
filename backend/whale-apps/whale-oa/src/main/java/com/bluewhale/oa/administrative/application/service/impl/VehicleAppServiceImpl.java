package com.bluewhale.oa.administrative.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.administrative.application.command.AdministrativeCommands;
import com.bluewhale.oa.administrative.application.service.VehicleAppService;
import com.bluewhale.oa.administrative.application.view.AdministrativeViews;
import com.bluewhale.oa.administrative.domain.model.AdministrativeQuery;
import com.bluewhale.oa.administrative.domain.model.BookingStatus;
import com.bluewhale.oa.administrative.domain.model.Driver;
import com.bluewhale.oa.administrative.domain.model.Vehicle;
import com.bluewhale.oa.administrative.domain.model.VehicleBooking;
import com.bluewhale.oa.administrative.domain.model.VehicleUsage;
import com.bluewhale.oa.administrative.domain.repository.AdministrativeRepository;
import com.bluewhale.oa.approval.application.command.ApprovalInstanceRegisterCommand;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用车应用服务实现。
 *
 * <p>同车时段重叠与派车约束在服务层校验；回车登记写入出车登记并回写车辆里程。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleAppServiceImpl implements VehicleAppService {

    private static final String BIZ_TYPE_VEHICLE = "VEHICLE";

    private static final String CODE_BIZ_TYPE = "VEHICLE";

    private static final String CODE_PREFIX = "VEH";

    private static final String STATUS_ENABLED = "ENABLED";

    private static final String DEFAULT_VEHICLE_TYPE = "SEDAN";

    private static final int DEFAULT_SEATS = 5;

    private final AdministrativeRepository repository;

    private final ApprovalInstanceAppService approvalInstanceAppService;

    private final SequenceAppService sequenceAppService;

    @Override
    public PageResult<AdministrativeViews.VehicleView> pageVehicles(AdministrativeQuery query) {
        List<AdministrativeViews.VehicleView> records = repository.pageVehicles(query).stream()
                .map(VehicleAppServiceImpl::toVehicleView).toList();
        return PageResult.of(records, repository.countVehicles(query), query.getCurrent(), query.getSize());
    }

    @Override
    public AdministrativeViews.VehicleView vehicleDetail(Long id) {
        return toVehicleView(requireVehicle(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.VehicleView createVehicle(AdministrativeCommands.VehicleSaveCommand command) {
        String plateNo = command.getPlateNo().trim();
        if (repository.existsVehiclePlate(plateNo, null)) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "车牌号已存在：" + plateNo);
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNo(plateNo);
        vehicle.setVehicleType(StringUtils.hasText(command.getVehicleType())
                ? command.getVehicleType() : DEFAULT_VEHICLE_TYPE);
        vehicle.setSeats(command.getSeats() == null ? DEFAULT_SEATS : command.getSeats());
        vehicle.setStatus(StringUtils.hasText(command.getStatus()) ? command.getStatus() : STATUS_ENABLED);
        vehicle.setCurrentMileage(command.getCurrentMileage() == null
                ? BigDecimal.ZERO : command.getCurrentMileage());
        vehicle.setPurchaseDate(command.getPurchaseDate());
        vehicle.setRemark(command.getRemark());
        repository.insertVehicle(vehicle);
        log.info("车辆已创建: id={}, plateNo={}", vehicle.getId(), vehicle.getPlateNo());
        return toVehicleView(vehicle);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.VehicleView updateVehicle(Long id,
                                                         AdministrativeCommands.VehicleSaveCommand command) {
        Vehicle vehicle = requireVehicle(id);
        String plateNo = command.getPlateNo().trim();
        if (repository.existsVehiclePlate(plateNo, id)) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "车牌号已存在：" + plateNo);
        }
        vehicle.setPlateNo(plateNo);
        vehicle.setVehicleType(command.getVehicleType());
        vehicle.setSeats(command.getSeats());
        vehicle.setStatus(StringUtils.hasText(command.getStatus()) ? command.getStatus() : vehicle.getStatus());
        vehicle.setCurrentMileage(command.getCurrentMileage() == null
                ? vehicle.getCurrentMileage() : command.getCurrentMileage());
        vehicle.setPurchaseDate(command.getPurchaseDate());
        vehicle.setRemark(command.getRemark());
        repository.updateVehicle(vehicle);
        return toVehicleView(vehicle);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVehicle(Long id) {
        requireVehicle(id);
        repository.deleteVehicle(id);
    }

    @Override
    public PageResult<AdministrativeViews.DriverView> pageDrivers(AdministrativeQuery query) {
        List<AdministrativeViews.DriverView> records = repository.pageDrivers(query).stream()
                .map(VehicleAppServiceImpl::toDriverView).toList();
        return PageResult.of(records, repository.countDrivers(query), query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.DriverView createDriver(AdministrativeCommands.DriverSaveCommand command) {
        String licenseNo = command.getLicenseNo().trim();
        if (repository.existsDriverLicense(licenseNo, null)) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "驾驶证号已存在：" + licenseNo);
        }
        Driver driver = new Driver();
        driver.setName(command.getName().trim());
        driver.setPhone(command.getPhone());
        driver.setLicenseNo(licenseNo);
        driver.setLicenseType(command.getLicenseType());
        driver.setStatus(StringUtils.hasText(command.getStatus()) ? command.getStatus() : STATUS_ENABLED);
        driver.setRemark(command.getRemark());
        repository.insertDriver(driver);
        return toDriverView(driver);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.DriverView updateDriver(Long id, AdministrativeCommands.DriverSaveCommand command) {
        Driver driver = requireDriver(id);
        String licenseNo = command.getLicenseNo().trim();
        if (repository.existsDriverLicense(licenseNo, id)) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "驾驶证号已存在：" + licenseNo);
        }
        driver.setName(command.getName().trim());
        driver.setPhone(command.getPhone());
        driver.setLicenseNo(licenseNo);
        driver.setLicenseType(command.getLicenseType());
        driver.setStatus(StringUtils.hasText(command.getStatus()) ? command.getStatus() : driver.getStatus());
        driver.setRemark(command.getRemark());
        repository.updateDriver(driver);
        return toDriverView(driver);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDriver(Long id) {
        requireDriver(id);
        repository.deleteDriver(id);
    }

    @Override
    public PageResult<AdministrativeViews.BookingView> pageBookings(AdministrativeQuery query) {
        List<AdministrativeViews.BookingView> records = repository.pageBookings(query).stream()
                .map(VehicleAppServiceImpl::toBookingView).toList();
        return PageResult.of(records, repository.countBookings(query), query.getCurrent(), query.getSize());
    }

    @Override
    public AdministrativeViews.BookingView bookingDetail(Long id) {
        return toBookingView(requireBooking(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.BookingView createBooking(AdministrativeCommands.BookingSaveCommand command) {
        VehicleBooking booking = new VehicleBooking();
        applyBooking(booking, command);
        booking.assertTimeRange();
        booking.setCode(sequenceAppService.nextCode(CODE_BIZ_TYPE, CODE_PREFIX));
        booking.setStatus(BookingStatus.PENDING.name());
        repository.insertBooking(booking);
        registerApproval(booking);
        log.info("用车申请已创建: id={}, code={}", booking.getId(), booking.getCode());
        return toBookingView(booking);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.BookingView updateBooking(Long id,
                                                         AdministrativeCommands.BookingSaveCommand command) {
        VehicleBooking booking = requireBooking(id);
        booking.assertEditable();
        applyBooking(booking, command);
        booking.assertTimeRange();
        repository.updateBooking(booking);
        return toBookingView(booking);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBooking(Long id) {
        VehicleBooking booking = requireBooking(id);
        booking.assertEditable();
        repository.deleteBooking(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBookingStatus(Long id, AdministrativeCommands.BookingStatusCommand command) {
        VehicleBooking booking = requireBooking(id);
        if (!booking.changeStatus(BookingStatus.of(command.getStatus()))) {
            return;
        }
        repository.updateBooking(booking);
        log.info("用车状态已变更: id={}, status={}", id, booking.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.BookingView dispatch(Long id, AdministrativeCommands.DispatchCommand command) {
        VehicleBooking booking = requireBooking(id);
        booking.dispatch(command.getVehicleId(), command.getDriverId());
        Vehicle vehicle = requireVehicle(command.getVehicleId());
        Driver driver = requireDriver(command.getDriverId());
        if (!STATUS_ENABLED.equals(vehicle.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "车辆已停用，不可派车");
        }
        if (!STATUS_ENABLED.equals(driver.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "司机已停用，不可派车");
        }
        if (repository.countOverlappingBookings(vehicle.getId(), booking.getStartTime(), booking.getEndTime(),
                booking.getId()) > 0) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_CONFLICT);
        }
        repository.updateBooking(booking);
        log.info("用车已派车: id={}, vehicleId={}, driverId={}", id, vehicle.getId(), driver.getId());
        return toBookingView(booking);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdministrativeViews.UsageView returnVehicle(Long id, AdministrativeCommands.ReturnCommand command) {
        VehicleBooking booking = requireBooking(id);
        if (BookingStatus.of(booking.getStatus()) != BookingStatus.APPROVED || booking.getVehicleId() == null) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, "仅已派车的用车单可回车登记");
        }
        if (repository.findUsageByBookingId(id).isPresent()) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, "该用车单已登记回车");
        }
        if (command.getEndMileage().compareTo(command.getStartMileage()) < 0) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "回车里程不能小于出车里程");
        }
        VehicleUsage usage = new VehicleUsage();
        usage.setBookingId(id);
        usage.setVehicleId(booking.getVehicleId());
        usage.setStartMileage(command.getStartMileage());
        usage.setEndMileage(command.getEndMileage());
        usage.setFuelCost(command.getFuelCost() == null ? BigDecimal.ZERO : command.getFuelCost());
        usage.setRemark(command.getRemark());
        repository.insertUsage(usage);

        Vehicle vehicle = requireVehicle(booking.getVehicleId());
        if (vehicle.getCurrentMileage() == null
                || command.getEndMileage().compareTo(vehicle.getCurrentMileage()) > 0) {
            vehicle.setCurrentMileage(command.getEndMileage());
            repository.updateVehicle(vehicle);
        }
        log.info("用车回车已登记: bookingId={}, endMileage={}", id, command.getEndMileage());
        return toUsageView(usage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncApprovalResult(Long bookingId, String result) {
        AdministrativeCommands.BookingStatusCommand command = new AdministrativeCommands.BookingStatusCommand();
        command.setStatus(result);
        if (BookingStatus.of(result) == BookingStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, "审批结果不能为待审批");
        }
        updateBookingStatus(bookingId, command);
    }

    @Override
    public AdministrativeViews.UsageView usageDetail(Long bookingId) {
        return repository.findUsageByBookingId(bookingId).map(VehicleAppServiceImpl::toUsageView)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_NOT_FOUND, "该用车单尚无回车登记"));
    }

    /**
     * 将保存命令中的字段写入用车申请实体。
     *
     * @param booking 用车申请实体
     * @param command 用车申请保存命令
     */
    private void applyBooking(VehicleBooking booking, AdministrativeCommands.BookingSaveCommand command) {
        booking.setApplicant(command.getApplicant());
        booking.setApplicantId(command.getApplicantId());
        booking.setDeptId(command.getDeptId());
        booking.setStartTime(command.getStartTime());
        booking.setEndTime(command.getEndTime());
        booking.setOrigin(command.getOrigin());
        booking.setDestination(command.getDestination());
        booking.setReason(command.getReason());
        booking.setRemark(command.getRemark());
    }

    /**
     * 为用车申请注册审批实例并回写实例标识。
     *
     * <p>已存在审批实例时直接跳过，避免重复注册。</p>
     *
     * @param booking 用车申请实体
     */
    private void registerApproval(VehicleBooking booking) {
        if (booking.getApprovalInstanceId() != null) {
            return;
        }
        ApprovalInstanceRegisterCommand command = new ApprovalInstanceRegisterCommand();
        command.setBusinessType(BIZ_TYPE_VEHICLE);
        command.setBusinessId(booking.getId());
        command.setBusinessCode(booking.getCode());
        command.setTitle(StringUtils.hasText(booking.getReason())
                ? "用车申请-" + booking.getReason() : "用车申请-" + booking.getCode());
        command.setInitiatorId(booking.getApplicantId());
        command.setInitiatorName(booking.getApplicant());
        command.setRemark(booking.getRemark());
        ApprovalInstanceView instance = approvalInstanceAppService.register(command);
        booking.setApprovalInstanceId(instance.getId());
        repository.updateBooking(booking);
    }

    /**
     * 按标识获取车辆，不存在时抛出业务异常。
     *
     * @param id 车辆标识
     * @return 车辆实体
     */
    private Vehicle requireVehicle(Long id) {
        return repository.findVehicleById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "车辆不存在：" + id));
    }

    /**
     * 按标识获取司机，不存在时抛出业务异常。
     *
     * @param id 司机标识
     * @return 司机实体
     */
    private Driver requireDriver(Long id) {
        return repository.findDriverById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "司机不存在：" + id));
    }

    /**
     * 按标识获取用车申请，不存在时抛出业务异常。
     *
     * @param id 用车申请标识
     * @return 用车申请实体
     */
    private VehicleBooking requireBooking(Long id) {
        return repository.findBookingById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_NOT_FOUND));
    }

    /**
     * 将车辆实体转换为车辆视图。
     *
     * @param vehicle 车辆实体
     * @return 车辆视图
     */
    private static AdministrativeViews.VehicleView toVehicleView(Vehicle vehicle) {
        AdministrativeViews.VehicleView view = new AdministrativeViews.VehicleView();
        view.setId(vehicle.getId());
        view.setPlateNo(vehicle.getPlateNo());
        view.setVehicleType(vehicle.getVehicleType());
        view.setSeats(vehicle.getSeats());
        view.setStatus(vehicle.getStatus());
        view.setCurrentMileage(vehicle.getCurrentMileage());
        view.setPurchaseDate(vehicle.getPurchaseDate());
        view.setRemark(vehicle.getRemark());
        view.setCreateTime(vehicle.getCreateTime());
        return view;
    }

    /**
     * 将司机实体转换为司机视图。
     *
     * @param driver 司机实体
     * @return 司机视图
     */
    private static AdministrativeViews.DriverView toDriverView(Driver driver) {
        AdministrativeViews.DriverView view = new AdministrativeViews.DriverView();
        view.setId(driver.getId());
        view.setName(driver.getName());
        view.setPhone(driver.getPhone());
        view.setLicenseNo(driver.getLicenseNo());
        view.setLicenseType(driver.getLicenseType());
        view.setStatus(driver.getStatus());
        view.setRemark(driver.getRemark());
        view.setCreateTime(driver.getCreateTime());
        return view;
    }

    /**
     * 将用车申请实体转换为用车申请视图。
     *
     * @param booking 用车申请实体
     * @return 用车申请视图
     */
    private static AdministrativeViews.BookingView toBookingView(VehicleBooking booking) {
        AdministrativeViews.BookingView view = new AdministrativeViews.BookingView();
        view.setId(booking.getId());
        view.setCode(booking.getCode());
        view.setApplicant(booking.getApplicant());
        view.setApplicantId(booking.getApplicantId());
        view.setDeptId(booking.getDeptId());
        view.setVehicleId(booking.getVehicleId());
        view.setDriverId(booking.getDriverId());
        view.setStartTime(booking.getStartTime());
        view.setEndTime(booking.getEndTime());
        view.setOrigin(booking.getOrigin());
        view.setDestination(booking.getDestination());
        view.setReason(booking.getReason());
        view.setStatus(booking.getStatus());
        view.setApprovalInstanceId(booking.getApprovalInstanceId());
        view.setRemark(booking.getRemark());
        view.setCreateTime(booking.getCreateTime());
        return view;
    }

    /**
     * 将用车记录实体转换为用车记录视图。
     *
     * @param usage 用车记录实体
     * @return 用车记录视图
     */
    private static AdministrativeViews.UsageView toUsageView(VehicleUsage usage) {
        AdministrativeViews.UsageView view = new AdministrativeViews.UsageView();
        view.setId(usage.getId());
        view.setBookingId(usage.getBookingId());
        view.setVehicleId(usage.getVehicleId());
        view.setStartMileage(usage.getStartMileage());
        view.setEndMileage(usage.getEndMileage());
        view.setFuelCost(usage.getFuelCost());
        view.setRemark(usage.getRemark());
        view.setCreateTime(usage.getCreateTime());
        return view;
    }
}
