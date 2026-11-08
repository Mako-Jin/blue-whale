package com.bluewhale.oa.administrative.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.administrative.domain.model.AdministrativeQuery;
import com.bluewhale.oa.administrative.domain.model.BookingStatus;
import com.bluewhale.oa.administrative.domain.model.Driver;
import com.bluewhale.oa.administrative.domain.model.PurchaseItem;
import com.bluewhale.oa.administrative.domain.model.PurchaseRequest;
import com.bluewhale.oa.administrative.domain.model.SealUse;
import com.bluewhale.oa.administrative.domain.model.SealUseFile;
import com.bluewhale.oa.administrative.domain.model.Vehicle;
import com.bluewhale.oa.administrative.domain.model.VehicleBooking;
import com.bluewhale.oa.administrative.domain.model.VehicleUsage;
import com.bluewhale.oa.administrative.domain.repository.AdministrativeRepository;
import com.bluewhale.oa.administrative.infrastructure.persistence.entity.DriverEntity;
import com.bluewhale.oa.administrative.infrastructure.persistence.entity.PurchaseItemEntity;
import com.bluewhale.oa.administrative.infrastructure.persistence.entity.PurchaseRequestEntity;
import com.bluewhale.oa.administrative.infrastructure.persistence.entity.SealUseEntity;
import com.bluewhale.oa.administrative.infrastructure.persistence.entity.SealUseFileEntity;
import com.bluewhale.oa.administrative.infrastructure.persistence.entity.VehicleBookingEntity;
import com.bluewhale.oa.administrative.infrastructure.persistence.entity.VehicleEntity;
import com.bluewhale.oa.administrative.infrastructure.persistence.entity.VehicleUsageEntity;
import com.bluewhale.oa.administrative.infrastructure.persistence.mapper.DriverMapper;
import com.bluewhale.oa.administrative.infrastructure.persistence.mapper.PurchaseItemMapper;
import com.bluewhale.oa.administrative.infrastructure.persistence.mapper.PurchaseRequestMapper;
import com.bluewhale.oa.administrative.infrastructure.persistence.mapper.SealUseFileMapper;
import com.bluewhale.oa.administrative.infrastructure.persistence.mapper.SealUseMapper;
import com.bluewhale.oa.administrative.infrastructure.persistence.mapper.VehicleBookingMapper;
import com.bluewhale.oa.administrative.infrastructure.persistence.mapper.VehicleMapper;
import com.bluewhale.oa.administrative.infrastructure.persistence.mapper.VehicleUsageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 行政业务仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AdministrativeRepositoryImpl implements AdministrativeRepository {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final VehicleMapper vehicleMapper;

    private final DriverMapper driverMapper;

    private final VehicleBookingMapper bookingMapper;

    private final VehicleUsageMapper usageMapper;

    private final SealUseMapper sealUseMapper;

    private final SealUseFileMapper sealUseFileMapper;

    private final PurchaseRequestMapper purchaseMapper;

    private final PurchaseItemMapper purchaseItemMapper;

    @Override
    public List<Vehicle> pageVehicles(AdministrativeQuery query) {
        return vehicleMapper.selectPage(page(query), vehicleFilter(query)).getRecords().stream()
                .map(AdministrativeRepositoryImpl::toVehicle).toList();
    }

    @Override
    public long countVehicles(AdministrativeQuery query) {
        Long total = vehicleMapper.selectCount(vehicleFilter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<Vehicle> findVehicleById(Long id) {
        return Optional.ofNullable(vehicleMapper.selectById(id))
                .map(AdministrativeRepositoryImpl::toVehicle);
    }

    @Override
    public boolean existsVehiclePlate(String plateNo, Long excludeId) {
        LambdaQueryWrapper<VehicleEntity> wrapper = new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getPlateNo, plateNo);
        if (excludeId != null) {
            wrapper.ne(VehicleEntity::getId, excludeId);
        }
        Long total = vehicleMapper.selectCount(wrapper);
        return total != null && total > 0;
    }

    @Override
    public void insertVehicle(Vehicle vehicle) {
        VehicleEntity entity = new VehicleEntity();
        fill(entity, vehicle);
        vehicleMapper.insert(entity);
        vehicle.setId(entity.getId());
    }

    @Override
    public void updateVehicle(Vehicle vehicle) {
        VehicleEntity entity = new VehicleEntity();
        entity.setId(vehicle.getId());
        fill(entity, vehicle);
        vehicleMapper.updateById(entity);
    }

    @Override
    public void deleteVehicle(Long id) {
        vehicleMapper.deleteById(id);
    }

    @Override
    public List<Driver> pageDrivers(AdministrativeQuery query) {
        return driverMapper.selectPage(page(query), driverFilter(query)).getRecords().stream()
                .map(AdministrativeRepositoryImpl::toDriver).toList();
    }

    @Override
    public long countDrivers(AdministrativeQuery query) {
        Long total = driverMapper.selectCount(driverFilter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<Driver> findDriverById(Long id) {
        return Optional.ofNullable(driverMapper.selectById(id))
                .map(AdministrativeRepositoryImpl::toDriver);
    }

    @Override
    public boolean existsDriverLicense(String licenseNo, Long excludeId) {
        LambdaQueryWrapper<DriverEntity> wrapper = new LambdaQueryWrapper<DriverEntity>()
                .eq(DriverEntity::getLicenseNo, licenseNo);
        if (excludeId != null) {
            wrapper.ne(DriverEntity::getId, excludeId);
        }
        Long total = driverMapper.selectCount(wrapper);
        return total != null && total > 0;
    }

    @Override
    public void insertDriver(Driver driver) {
        DriverEntity entity = new DriverEntity();
        fill(entity, driver);
        driverMapper.insert(entity);
        driver.setId(entity.getId());
    }

    @Override
    public void updateDriver(Driver driver) {
        DriverEntity entity = new DriverEntity();
        entity.setId(driver.getId());
        fill(entity, driver);
        driverMapper.updateById(entity);
    }

    @Override
    public void deleteDriver(Long id) {
        driverMapper.deleteById(id);
    }

    @Override
    public List<VehicleBooking> pageBookings(AdministrativeQuery query) {
        return bookingMapper.selectPage(page(query), bookingFilter(query)).getRecords().stream()
                .map(AdministrativeRepositoryImpl::toBooking).toList();
    }

    @Override
    public long countBookings(AdministrativeQuery query) {
        Long total = bookingMapper.selectCount(bookingFilter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<VehicleBooking> findBookingById(Long id) {
        return Optional.ofNullable(bookingMapper.selectById(id))
                .map(AdministrativeRepositoryImpl::toBooking);
    }

    @Override
    public long countOverlappingBookings(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime,
                                         Long excludeId) {
        LambdaQueryWrapper<VehicleBookingEntity> wrapper = new LambdaQueryWrapper<VehicleBookingEntity>()
                .eq(VehicleBookingEntity::getVehicleId, vehicleId)
                .lt(VehicleBookingEntity::getStartTime, endTime)
                .gt(VehicleBookingEntity::getEndTime, startTime)
                .notIn(VehicleBookingEntity::getStatus,
                        BookingStatus.REJECTED.name(), BookingStatus.CANCELLED.name());
        if (excludeId != null) {
            wrapper.ne(VehicleBookingEntity::getId, excludeId);
        }
        Long total = bookingMapper.selectCount(wrapper);
        return total == null ? 0L : total;
    }

    @Override
    public void insertBooking(VehicleBooking booking) {
        VehicleBookingEntity entity = new VehicleBookingEntity();
        fill(entity, booking);
        bookingMapper.insert(entity);
        booking.setId(entity.getId());
    }

    @Override
    public void updateBooking(VehicleBooking booking) {
        VehicleBookingEntity entity = new VehicleBookingEntity();
        entity.setId(booking.getId());
        fill(entity, booking);
        bookingMapper.updateById(entity);
    }

    @Override
    public void deleteBooking(Long id) {
        bookingMapper.deleteById(id);
    }

    @Override
    public Optional<VehicleUsage> findUsageByBookingId(Long bookingId) {
        return Optional.ofNullable(usageMapper.selectOne(new LambdaQueryWrapper<VehicleUsageEntity>()
                        .eq(VehicleUsageEntity::getBookingId, bookingId)))
                .map(AdministrativeRepositoryImpl::toUsage);
    }

    @Override
    public void insertUsage(VehicleUsage usage) {
        VehicleUsageEntity entity = new VehicleUsageEntity();
        fill(entity, usage);
        usageMapper.insert(entity);
        usage.setId(entity.getId());
    }

    @Override
    public List<SealUse> pageSealUses(AdministrativeQuery query) {
        return sealUseMapper.selectPage(page(query), sealFilter(query)).getRecords().stream()
                .map(AdministrativeRepositoryImpl::toSealUse).toList();
    }

    @Override
    public long countSealUses(AdministrativeQuery query) {
        Long total = sealUseMapper.selectCount(sealFilter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<SealUse> findSealUseById(Long id) {
        SealUseEntity entity = sealUseMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        SealUse sealUse = toSealUse(entity);
        sealUse.setFiles(sealUseFileMapper.selectList(new LambdaQueryWrapper<SealUseFileEntity>()
                        .eq(SealUseFileEntity::getSealUseId, id))
                .stream().map(AdministrativeRepositoryImpl::toSealUseFile).toList());
        return Optional.of(sealUse);
    }

    @Override
    public void insertSealUse(SealUse sealUse) {
        SealUseEntity entity = new SealUseEntity();
        fill(entity, sealUse);
        sealUseMapper.insert(entity);
        sealUse.setId(entity.getId());
    }

    @Override
    public void updateSealUse(SealUse sealUse) {
        SealUseEntity entity = new SealUseEntity();
        entity.setId(sealUse.getId());
        fill(entity, sealUse);
        sealUseMapper.updateById(entity);
    }

    @Override
    public void deleteSealUse(Long id) {
        sealUseMapper.deleteById(id);
    }

    @Override
    public void replaceSealUseFiles(Long sealUseId, List<SealUseFile> files) {
        sealUseFileMapper.delete(new LambdaQueryWrapper<SealUseFileEntity>()
                .eq(SealUseFileEntity::getSealUseId, sealUseId));
        if (files == null) {
            return;
        }
        for (SealUseFile file : files) {
            SealUseFileEntity entity = new SealUseFileEntity();
            entity.setSealUseId(sealUseId);
            entity.setFileId(file.getFileId());
            entity.setFileName(file.getFileName());
            sealUseFileMapper.insert(entity);
        }
    }

    @Override
    public List<PurchaseRequest> pagePurchases(AdministrativeQuery query) {
        return purchaseMapper.selectPage(page(query), purchaseFilter(query)).getRecords().stream()
                .map(AdministrativeRepositoryImpl::toPurchase).toList();
    }

    @Override
    public long countPurchases(AdministrativeQuery query) {
        Long total = purchaseMapper.selectCount(purchaseFilter(query));
        return total == null ? 0L : total;
    }

    @Override
    public Optional<PurchaseRequest> findPurchaseById(Long id) {
        PurchaseRequestEntity entity = purchaseMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        PurchaseRequest purchase = toPurchase(entity);
        purchase.setItems(purchaseItemMapper.selectList(new LambdaQueryWrapper<PurchaseItemEntity>()
                        .eq(PurchaseItemEntity::getPurchaseId, id)
                        .orderByAsc(PurchaseItemEntity::getLineNo))
                .stream().map(AdministrativeRepositoryImpl::toPurchaseItem).toList());
        return Optional.of(purchase);
    }

    @Override
    public void insertPurchase(PurchaseRequest purchase) {
        PurchaseRequestEntity entity = new PurchaseRequestEntity();
        fill(entity, purchase);
        purchaseMapper.insert(entity);
        purchase.setId(entity.getId());
    }

    @Override
    public void updatePurchase(PurchaseRequest purchase) {
        PurchaseRequestEntity entity = new PurchaseRequestEntity();
        entity.setId(purchase.getId());
        fill(entity, purchase);
        purchaseMapper.updateById(entity);
    }

    @Override
    public void deletePurchase(Long id) {
        purchaseMapper.deleteById(id);
    }

    @Override
    public void replacePurchaseItems(Long purchaseId, List<PurchaseItem> items) {
        purchaseItemMapper.delete(new LambdaQueryWrapper<PurchaseItemEntity>()
                .eq(PurchaseItemEntity::getPurchaseId, purchaseId));
        if (items == null) {
            return;
        }
        for (PurchaseItem item : items) {
            PurchaseItemEntity entity = new PurchaseItemEntity();
            entity.setPurchaseId(purchaseId);
            fill(entity, item);
            purchaseItemMapper.insert(entity);
            item.setId(entity.getId());
        }
    }

    @Override
    public List<PurchaseRequest> listPushablePurchases(int limit) {
        return purchaseMapper.selectList(new LambdaQueryWrapper<PurchaseRequestEntity>()
                        .eq(PurchaseRequestEntity::getStatus, "APPROVED")
                        .in(PurchaseRequestEntity::getErpPushStatus, "NONE", "PENDING", "FAILED")
                        .orderByAsc(PurchaseRequestEntity::getId)
                        .last("LIMIT " + Math.max(limit, 1)))
                .stream().map(AdministrativeRepositoryImpl::toPurchase).toList();
    }

    /**
     * 构造分页参数，页大小限制在 {@code [1, MAX_PAGE_SIZE]} 区间内。
     *
     * @param query 查询条件
     * @param <T> 分页记录类型
     * @return 分页对象
     */
    private <T> Page<T> page(AdministrativeQuery query) {
        long size = Math.min(Math.max(query.getSize(), 1L), MAX_PAGE_SIZE);
        return new Page<>(Math.max(query.getCurrent(), 1L), size, false);
    }

    /**
     * 构造车辆查询条件，支持车牌号模糊匹配与状态过滤。
     *
     * @param query 查询条件
     * @return 车辆查询包装器
     */
    private LambdaQueryWrapper<VehicleEntity> vehicleFilter(AdministrativeQuery query) {
        return new LambdaQueryWrapper<VehicleEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(VehicleEntity::getPlateNo, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), VehicleEntity::getStatus, query.getStatus())
                .orderByDesc(VehicleEntity::getId);
    }

    /**
     * 构造司机查询条件，支持姓名或手机号模糊匹配与状态过滤。
     *
     * @param query 查询条件
     * @return 司机查询包装器
     */
    private LambdaQueryWrapper<DriverEntity> driverFilter(AdministrativeQuery query) {
        return new LambdaQueryWrapper<DriverEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(DriverEntity::getName, query.getKeyword())
                        .or().like(DriverEntity::getPhone, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), DriverEntity::getStatus, query.getStatus())
                .orderByDesc(DriverEntity::getId);
    }

    /**
     * 构造用车申请查询条件，支持单号或目的地模糊匹配，以及状态、申请人、车辆过滤。
     *
     * @param query 查询条件
     * @return 用车申请查询包装器
     */
    private LambdaQueryWrapper<VehicleBookingEntity> bookingFilter(AdministrativeQuery query) {
        return new LambdaQueryWrapper<VehicleBookingEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(VehicleBookingEntity::getCode, query.getKeyword())
                        .or().like(VehicleBookingEntity::getDestination, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), VehicleBookingEntity::getStatus, query.getStatus())
                .eq(query.getApplicantId() != null, VehicleBookingEntity::getApplicantId, query.getApplicantId())
                .eq(query.getVehicleId() != null, VehicleBookingEntity::getVehicleId, query.getVehicleId())
                .orderByDesc(VehicleBookingEntity::getId);
    }

    /**
     * 构造用章查询条件，支持单号或印章名称模糊匹配，以及状态、申请人过滤。
     *
     * @param query 查询条件
     * @return 用章查询包装器
     */
    private LambdaQueryWrapper<SealUseEntity> sealFilter(AdministrativeQuery query) {
        return new LambdaQueryWrapper<SealUseEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(SealUseEntity::getCode, query.getKeyword())
                        .or().like(SealUseEntity::getSealName, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), SealUseEntity::getStatus, query.getStatus())
                .eq(query.getApplicantId() != null, SealUseEntity::getApplicantId, query.getApplicantId())
                .orderByDesc(SealUseEntity::getId);
    }

    /**
     * 构造采购申请查询条件，支持单号或采购事由模糊匹配，以及状态、ERP 推送状态、申请人过滤。
     *
     * @param query 查询条件
     * @return 采购申请查询包装器
     */
    private LambdaQueryWrapper<PurchaseRequestEntity> purchaseFilter(AdministrativeQuery query) {
        return new LambdaQueryWrapper<PurchaseRequestEntity>()
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(PurchaseRequestEntity::getCode, query.getKeyword())
                        .or().like(PurchaseRequestEntity::getReason, query.getKeyword()))
                .eq(StringUtils.hasText(query.getStatus()), PurchaseRequestEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getErpPushStatus()), PurchaseRequestEntity::getErpPushStatus,
                        query.getErpPushStatus())
                .eq(query.getApplicantId() != null, PurchaseRequestEntity::getApplicantId, query.getApplicantId())
                .orderByDesc(PurchaseRequestEntity::getId);
    }

    /**
     * 将车辆持久化实体转换为车辆领域模型。
     *
     * @param entity 车辆实体
     * @return 车辆领域模型
     */
    private static Vehicle toVehicle(VehicleEntity entity) {
        Vehicle model = new Vehicle();
        model.setId(entity.getId());
        model.setPlateNo(entity.getPlateNo());
        model.setVehicleType(entity.getVehicleType());
        model.setSeats(entity.getSeats());
        model.setStatus(entity.getStatus());
        model.setCurrentMileage(entity.getCurrentMileage());
        model.setPurchaseDate(entity.getPurchaseDate());
        model.setRemark(entity.getRemark());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    /**
     * 用车辆领域模型的可写字段填充车辆持久化实体。
     *
     * @param entity 车辆实体
     * @param model 车辆领域模型
     */
    private static void fill(VehicleEntity entity, Vehicle model) {
        entity.setPlateNo(model.getPlateNo());
        entity.setVehicleType(model.getVehicleType());
        entity.setSeats(model.getSeats());
        entity.setStatus(model.getStatus());
        entity.setCurrentMileage(model.getCurrentMileage());
        entity.setPurchaseDate(model.getPurchaseDate());
        entity.setRemark(model.getRemark());
    }

    /**
     * 将司机持久化实体转换为司机领域模型。
     *
     * @param entity 司机实体
     * @return 司机领域模型
     */
    private static Driver toDriver(DriverEntity entity) {
        Driver model = new Driver();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setPhone(entity.getPhone());
        model.setLicenseNo(entity.getLicenseNo());
        model.setLicenseType(entity.getLicenseType());
        model.setStatus(entity.getStatus());
        model.setRemark(entity.getRemark());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    /**
     * 用司机领域模型的可写字段填充司机持久化实体。
     *
     * @param entity 司机实体
     * @param model 司机领域模型
     */
    private static void fill(DriverEntity entity, Driver model) {
        entity.setName(model.getName());
        entity.setPhone(model.getPhone());
        entity.setLicenseNo(model.getLicenseNo());
        entity.setLicenseType(model.getLicenseType());
        entity.setStatus(model.getStatus());
        entity.setRemark(model.getRemark());
    }

    /**
     * 将用车申请持久化实体转换为用车申请领域模型。
     *
     * @param entity 用车申请实体
     * @return 用车申请领域模型
     */
    private static VehicleBooking toBooking(VehicleBookingEntity entity) {
        VehicleBooking model = new VehicleBooking();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setApplicant(entity.getApplicant());
        model.setApplicantId(entity.getApplicantId());
        model.setDeptId(entity.getDeptId());
        model.setVehicleId(entity.getVehicleId());
        model.setDriverId(entity.getDriverId());
        model.setStartTime(entity.getStartTime());
        model.setEndTime(entity.getEndTime());
        model.setOrigin(entity.getOrigin());
        model.setDestination(entity.getDestination());
        model.setReason(entity.getReason());
        model.setStatus(entity.getStatus());
        model.setApprovalInstanceId(entity.getApprovalInstanceId());
        model.setRemark(entity.getRemark());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    /**
     * 用用车申请领域模型的可写字段填充用车申请持久化实体。
     *
     * @param entity 用车申请实体
     * @param model 用车申请领域模型
     */
    private static void fill(VehicleBookingEntity entity, VehicleBooking model) {
        entity.setCode(model.getCode());
        entity.setApplicant(model.getApplicant());
        entity.setApplicantId(model.getApplicantId());
        entity.setDeptId(model.getDeptId());
        entity.setVehicleId(model.getVehicleId());
        entity.setDriverId(model.getDriverId());
        entity.setStartTime(model.getStartTime());
        entity.setEndTime(model.getEndTime());
        entity.setOrigin(model.getOrigin());
        entity.setDestination(model.getDestination());
        entity.setReason(model.getReason());
        entity.setStatus(model.getStatus());
        entity.setApprovalInstanceId(model.getApprovalInstanceId());
        entity.setRemark(model.getRemark());
    }

    /**
     * 将用车记录持久化实体转换为用车记录领域模型。
     *
     * @param entity 用车记录实体
     * @return 用车记录领域模型
     */
    private static VehicleUsage toUsage(VehicleUsageEntity entity) {
        VehicleUsage model = new VehicleUsage();
        model.setId(entity.getId());
        model.setBookingId(entity.getBookingId());
        model.setVehicleId(entity.getVehicleId());
        model.setStartMileage(entity.getStartMileage());
        model.setEndMileage(entity.getEndMileage());
        model.setFuelCost(entity.getFuelCost());
        model.setRemark(entity.getRemark());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    /**
     * 用用车记录领域模型的可写字段填充用车记录持久化实体。
     *
     * @param entity 用车记录实体
     * @param model 用车记录领域模型
     */
    private static void fill(VehicleUsageEntity entity, VehicleUsage model) {
        entity.setBookingId(model.getBookingId());
        entity.setVehicleId(model.getVehicleId());
        entity.setStartMileage(model.getStartMileage());
        entity.setEndMileage(model.getEndMileage());
        entity.setFuelCost(model.getFuelCost());
        entity.setRemark(model.getRemark());
    }

    /**
     * 将用章持久化实体转换为用章领域模型。
     *
     * @param entity 用章实体
     * @return 用章领域模型
     */
    private static SealUse toSealUse(SealUseEntity entity) {
        SealUse model = new SealUse();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setApplicant(entity.getApplicant());
        model.setApplicantId(entity.getApplicantId());
        model.setDeptId(entity.getDeptId());
        model.setSealId(entity.getSealId());
        model.setSealName(entity.getSealName());
        model.setReason(entity.getReason());
        model.setFileCount(entity.getFileCount());
        model.setUseTime(entity.getUseTime());
        model.setStatus(entity.getStatus());
        model.setRegisteredTime(entity.getRegisteredTime());
        model.setRegisteredBy(entity.getRegisteredBy());
        model.setApprovalInstanceId(entity.getApprovalInstanceId());
        model.setRemark(entity.getRemark());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    /**
     * 用用章领域模型的可写字段填充用章持久化实体。
     *
     * @param entity 用章实体
     * @param model 用章领域模型
     */
    private static void fill(SealUseEntity entity, SealUse model) {
        entity.setCode(model.getCode());
        entity.setApplicant(model.getApplicant());
        entity.setApplicantId(model.getApplicantId());
        entity.setDeptId(model.getDeptId());
        entity.setSealId(model.getSealId());
        entity.setSealName(model.getSealName());
        entity.setReason(model.getReason());
        entity.setFileCount(model.getFileCount());
        entity.setUseTime(model.getUseTime());
        entity.setStatus(model.getStatus());
        entity.setRegisteredTime(model.getRegisteredTime());
        entity.setRegisteredBy(model.getRegisteredBy());
        entity.setApprovalInstanceId(model.getApprovalInstanceId());
        entity.setRemark(model.getRemark());
    }

    /**
     * 将用章附件持久化实体转换为用章附件领域模型。
     *
     * @param entity 用章附件实体
     * @return 用章附件领域模型
     */
    private static SealUseFile toSealUseFile(SealUseFileEntity entity) {
        SealUseFile model = new SealUseFile();
        model.setId(entity.getId());
        model.setSealUseId(entity.getSealUseId());
        model.setFileId(entity.getFileId());
        model.setFileName(entity.getFileName());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    /**
     * 将采购申请持久化实体转换为采购申请领域模型。
     *
     * @param entity 采购申请实体
     * @return 采购申请领域模型
     */
    private static PurchaseRequest toPurchase(PurchaseRequestEntity entity) {
        PurchaseRequest model = new PurchaseRequest();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setApplicant(entity.getApplicant());
        model.setApplicantId(entity.getApplicantId());
        model.setDeptId(entity.getDeptId());
        model.setReason(entity.getReason());
        model.setExpectedDate(entity.getExpectedDate());
        model.setSupplierId(entity.getSupplierId());
        model.setWarehouseId(entity.getWarehouseId());
        model.setTotalAmount(entity.getTotalAmount());
        model.setCurrency(entity.getCurrency());
        model.setStatus(entity.getStatus());
        model.setApprovalInstanceId(entity.getApprovalInstanceId());
        model.setErpPushStatus(entity.getErpPushStatus());
        model.setErpPoNo(entity.getErpPoNo());
        model.setRemark(entity.getRemark());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    /**
     * 用采购申请领域模型的可写字段填充采购申请持久化实体。
     *
     * @param entity 采购申请实体
     * @param model 采购申请领域模型
     */
    private static void fill(PurchaseRequestEntity entity, PurchaseRequest model) {
        entity.setCode(model.getCode());
        entity.setApplicant(model.getApplicant());
        entity.setApplicantId(model.getApplicantId());
        entity.setDeptId(model.getDeptId());
        entity.setReason(model.getReason());
        entity.setExpectedDate(model.getExpectedDate());
        entity.setSupplierId(model.getSupplierId());
        entity.setWarehouseId(model.getWarehouseId());
        entity.setTotalAmount(model.getTotalAmount());
        entity.setCurrency(model.getCurrency());
        entity.setStatus(model.getStatus());
        entity.setApprovalInstanceId(model.getApprovalInstanceId());
        entity.setErpPushStatus(model.getErpPushStatus());
        entity.setErpPoNo(model.getErpPoNo());
        entity.setRemark(model.getRemark());
    }

    /**
     * 将采购明细持久化实体转换为采购明细领域模型。
     *
     * @param entity 采购明细实体
     * @return 采购明细领域模型
     */
    private static PurchaseItem toPurchaseItem(PurchaseItemEntity entity) {
        PurchaseItem model = new PurchaseItem();
        model.setId(entity.getId());
        model.setPurchaseId(entity.getPurchaseId());
        model.setLineNo(entity.getLineNo());
        model.setItemCode(entity.getItemCode());
        model.setItemName(entity.getItemName());
        model.setQuantity(entity.getQuantity());
        model.setUnitPrice(entity.getUnitPrice());
        model.setAmount(entity.getAmount());
        model.setSupplierAdvice(entity.getSupplierAdvice());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }

    /**
     * 用采购明细领域模型的可写字段填充采购明细持久化实体。
     *
     * @param entity 采购明细实体
     * @param model 采购明细领域模型
     */
    private static void fill(PurchaseItemEntity entity, PurchaseItem model) {
        entity.setLineNo(model.getLineNo());
        entity.setItemCode(model.getItemCode());
        entity.setItemName(model.getItemName());
        entity.setQuantity(model.getQuantity());
        entity.setUnitPrice(model.getUnitPrice());
        entity.setAmount(model.getAmount());
        entity.setSupplierAdvice(model.getSupplierAdvice());
    }
}
