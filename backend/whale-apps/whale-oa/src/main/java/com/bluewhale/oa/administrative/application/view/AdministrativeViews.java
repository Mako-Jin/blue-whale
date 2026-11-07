package com.bluewhale.oa.administrative.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 行政业务视图集合。
 *
 * @author BlueWhale
 */
public final class AdministrativeViews {

    private AdministrativeViews() {
    }

    /**
     * 车辆视图。
     */
    @Data
    public static class VehicleView {

        private Long id;

        private String plateNo;

        private String vehicleType;

        private Integer seats;

        private String status;

        private BigDecimal currentMileage;

        private LocalDate purchaseDate;

        private String remark;

        private LocalDateTime createTime;
    }

    /**
     * 司机视图。
     */
    @Data
    public static class DriverView {

        private Long id;

        private String name;

        private String phone;

        private String licenseNo;

        private String licenseType;

        private String status;

        private String remark;

        private LocalDateTime createTime;
    }

    /**
     * 用车预约视图。
     */
    @Data
    public static class BookingView {

        private Long id;

        private String code;

        private String applicant;

        private Long applicantId;

        private Long deptId;

        private Long vehicleId;

        private Long driverId;

        private LocalDateTime startTime;

        private LocalDateTime endTime;

        private String origin;

        private String destination;

        private String reason;

        private String status;

        private Long approvalInstanceId;

        private String remark;

        private LocalDateTime createTime;
    }

    /**
     * 出车登记视图。
     */
    @Data
    public static class UsageView {

        private Long id;

        private Long bookingId;

        private Long vehicleId;

        private BigDecimal startMileage;

        private BigDecimal endMileage;

        private BigDecimal fuelCost;

        private String remark;

        private LocalDateTime createTime;
    }

    /**
     * 用章申请视图。
     */
    @Data
    public static class SealUseView {

        private Long id;

        private String code;

        private String applicant;

        private Long applicantId;

        private Long deptId;

        private Long sealId;

        private String sealName;

        private String reason;

        private Integer fileCount;

        private LocalDateTime useTime;

        private String status;

        private LocalDateTime registeredTime;

        private Long registeredBy;

        private Long approvalInstanceId;

        private String remark;

        private List<FileView> files;

        private LocalDateTime createTime;
    }

    /**
     * 用章文件视图。
     */
    @Data
    public static class FileView {

        private Long id;

        private Long fileId;

        private String fileName;
    }

    /**
     * 采购申请视图。
     */
    @Data
    public static class PurchaseView {

        private Long id;

        private String code;

        private String applicant;

        private Long applicantId;

        private Long deptId;

        private String reason;

        private LocalDate expectedDate;

        private Long supplierId;

        private Long warehouseId;

        private BigDecimal totalAmount;

        private String currency;

        private String status;

        private Long approvalInstanceId;

        private String erpPushStatus;

        private String erpPoNo;

        private String remark;

        private List<ItemView> items;

        private LocalDateTime createTime;
    }

    /**
     * 采购明细视图。
     */
    @Data
    public static class ItemView {

        private Long id;

        private Integer lineNo;

        private String itemCode;

        private String itemName;

        private BigDecimal quantity;

        private BigDecimal unitPrice;

        private BigDecimal amount;

        private String supplierAdvice;
    }
}
