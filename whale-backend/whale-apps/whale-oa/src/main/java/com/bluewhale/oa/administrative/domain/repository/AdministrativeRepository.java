package com.bluewhale.oa.administrative.domain.repository;

import com.bluewhale.oa.administrative.domain.model.AdministrativeQuery;
import com.bluewhale.oa.administrative.domain.model.Driver;
import com.bluewhale.oa.administrative.domain.model.PurchaseItem;
import com.bluewhale.oa.administrative.domain.model.PurchaseRequest;
import com.bluewhale.oa.administrative.domain.model.SealUse;
import com.bluewhale.oa.administrative.domain.model.SealUseFile;
import com.bluewhale.oa.administrative.domain.model.Vehicle;
import com.bluewhale.oa.administrative.domain.model.VehicleBooking;
import com.bluewhale.oa.administrative.domain.model.VehicleUsage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 行政业务（用车 / 用章 / 采购）仓储。
 *
 * @author BlueWhale
 */
public interface AdministrativeRepository {

    /**
     * 分页查询车辆。
     *
     * @param query 查询条件
     * @return 车辆列表
     */
    List<Vehicle> pageVehicles(AdministrativeQuery query);

    /**
     * 统计车辆数量。
     *
     * @param query 查询条件
     * @return 数量
     */
    long countVehicles(AdministrativeQuery query);

    /**
     * 按ID查询车辆。
     *
     * @param id 主键
     * @return 车辆
     */
    Optional<Vehicle> findVehicleById(Long id);

    /**
     * 判断车牌是否已存在。
     *
     * @param plateNo 车牌号
     * @param excludeId 排除ID
     * @return 是否存在
     */
    boolean existsVehiclePlate(String plateNo, Long excludeId);

    /**
     * 新建车辆。
     *
     * @param vehicle 车辆
     */
    void insertVehicle(Vehicle vehicle);

    /**
     * 更新车辆。
     *
     * @param vehicle 车辆
     */
    void updateVehicle(Vehicle vehicle);

    /**
     * 删除车辆。
     *
     * @param id 主键
     */
    void deleteVehicle(Long id);

    /**
     * 分页查询司机。
     *
     * @param query 查询条件
     * @return 司机列表
     */
    List<Driver> pageDrivers(AdministrativeQuery query);

    /**
     * 统计司机数量。
     *
     * @param query 查询条件
     * @return 数量
     */
    long countDrivers(AdministrativeQuery query);

    /**
     * 按ID查询司机。
     *
     * @param id 主键
     * @return 司机
     */
    Optional<Driver> findDriverById(Long id);

    /**
     * 判断驾驶证号是否已存在。
     *
     * @param licenseNo 驾驶证号
     * @param excludeId 排除ID
     * @return 是否存在
     */
    boolean existsDriverLicense(String licenseNo, Long excludeId);

    /**
     * 新建司机。
     *
     * @param driver 司机
     */
    void insertDriver(Driver driver);

    /**
     * 更新司机。
     *
     * @param driver 司机
     */
    void updateDriver(Driver driver);

    /**
     * 删除司机。
     *
     * @param id 主键
     */
    void deleteDriver(Long id);

    /**
     * 分页查询用车预约。
     *
     * @param query 查询条件
     * @return 用车预约列表
     */
    List<VehicleBooking> pageBookings(AdministrativeQuery query);

    /**
     * 统计用车预约数量。
     *
     * @param query 查询条件
     * @return 数量
     */
    long countBookings(AdministrativeQuery query);

    /**
     * 按ID查询用车预约。
     *
     * @param id 主键
     * @return 用车预约
     */
    Optional<VehicleBooking> findBookingById(Long id);

    /**
     * 查询同车时间段重叠的预约数量（排除 REJECTED/CANCELLED 与自身）。
     *
     * @param vehicleId 车辆ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param excludeId 排除ID
     * @return 重叠数量
     */
    long countOverlappingBookings(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime, Long excludeId);

    /**
     * 新建用车预约。
     *
     * @param booking 用车预约
     */
    void insertBooking(VehicleBooking booking);

    /**
     * 更新用车预约。
     *
     * @param booking 用车预约
     */
    void updateBooking(VehicleBooking booking);

    /**
     * 删除用车预约。
     *
     * @param id 主键
     */
    void deleteBooking(Long id);

    /**
     * 按用车单查询出车登记。
     *
     * @param bookingId 用车预约ID
     * @return 出车登记
     */
    Optional<VehicleUsage> findUsageByBookingId(Long bookingId);

    /**
     * 新增出车登记。
     *
     * @param usage 出车登记
     */
    void insertUsage(VehicleUsage usage);

    /**
     * 分页查询用章申请。
     *
     * @param query 查询条件
     * @return 用章申请列表
     */
    List<SealUse> pageSealUses(AdministrativeQuery query);

    /**
     * 统计用章申请数量。
     *
     * @param query 查询条件
     * @return 数量
     */
    long countSealUses(AdministrativeQuery query);

    /**
     * 按ID查询用章申请（含文件）。
     *
     * @param id 主键
     * @return 用章申请
     */
    Optional<SealUse> findSealUseById(Long id);

    /**
     * 新建用章申请。
     *
     * @param sealUse 用章申请
     */
    void insertSealUse(SealUse sealUse);

    /**
     * 更新用章申请。
     *
     * @param sealUse 用章申请
     */
    void updateSealUse(SealUse sealUse);

    /**
     * 删除用章申请。
     *
     * @param id 主键
     */
    void deleteSealUse(Long id);

    /**
     * 替换用章文件。
     *
     * @param sealUseId 用章申请ID
     * @param files 文件列表
     */
    void replaceSealUseFiles(Long sealUseId, List<SealUseFile> files);

    /**
     * 分页查询采购申请。
     *
     * @param query 查询条件
     * @return 采购申请列表
     */
    List<PurchaseRequest> pagePurchases(AdministrativeQuery query);

    /**
     * 统计采购申请数量。
     *
     * @param query 查询条件
     * @return 数量
     */
    long countPurchases(AdministrativeQuery query);

    /**
     * 按ID查询采购申请（含明细）。
     *
     * @param id 主键
     * @return 采购申请
     */
    Optional<PurchaseRequest> findPurchaseById(Long id);

    /**
     * 新建采购申请。
     *
     * @param purchase 采购申请
     */
    void insertPurchase(PurchaseRequest purchase);

    /**
     * 更新采购申请。
     *
     * @param purchase 采购申请
     */
    void updatePurchase(PurchaseRequest purchase);

    /**
     * 删除采购申请。
     *
     * @param id 主键
     */
    void deletePurchase(Long id);

    /**
     * 替换采购明细。
     *
     * @param purchaseId 采购申请ID
     * @param items 明细列表
     */
    void replacePurchaseItems(Long purchaseId, List<PurchaseItem> items);

    /**
     * 查询可推送 ERP 的采购申请。
     *
     * @param limit 上限
     * @return 采购申请列表
     */
    List<PurchaseRequest> listPushablePurchases(int limit);
}
