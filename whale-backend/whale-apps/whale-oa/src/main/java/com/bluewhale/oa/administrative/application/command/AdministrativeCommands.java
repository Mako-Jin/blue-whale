package com.bluewhale.oa.administrative.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 行政业务命令集合。
 *
 * @author BlueWhale
 */
public final class AdministrativeCommands {

    /** 工具类，禁止实例化。 */
    private AdministrativeCommands() {
    }

    /**
     * 车辆保存命令。
     */
    @Data
    public static class VehicleSaveCommand {

        @NotBlank(message = "{validation.oa.vehicle.plate-no-required}")
        @Size(max = 32, message = "{validation.oa.vehicle.plate-no-size}")
        private String plateNo;

        @Size(max = 32, message = "{validation.oa.vehicle.vehicle-type-size}")
        private String vehicleType;

        private Integer seats;

        private String status;

        @DecimalMin(value = "0.0", message = "{validation.oa.vehicle.current-mileage-min}")
        private BigDecimal currentMileage;

        private LocalDate purchaseDate;

        @Size(max = 255, message = "{validation.oa.vehicle.remark-size}")
        private String remark;
    }

    /**
     * 司机保存命令。
     */
    @Data
    public static class DriverSaveCommand {

        @NotBlank(message = "{validation.oa.driver.name-required}")
        @Size(max = 64, message = "{validation.oa.driver.name-size}")
        private String name;

        @Size(max = 32, message = "{validation.oa.driver.phone-size}")
        private String phone;

        @NotBlank(message = "{validation.oa.driver.license-no-required}")
        @Size(max = 64, message = "{validation.oa.driver.license-no-size}")
        private String licenseNo;

        @Size(max = 16, message = "{validation.oa.driver.license-type-size}")
        private String licenseType;

        private String status;

        @Size(max = 255, message = "{validation.oa.driver.remark-size}")
        private String remark;
    }

    /**
     * 用车预约保存命令。
     */
    @Data
    public static class BookingSaveCommand {

        @NotBlank(message = "{validation.oa.booking.applicant-required}")
        @Size(max = 64, message = "{validation.oa.booking.applicant-size}")
        private String applicant;

        private Long applicantId;

        private Long deptId;

        @NotNull(message = "{validation.oa.booking.start-time-required}")
        private LocalDateTime startTime;

        @NotNull(message = "{validation.oa.booking.end-time-required}")
        private LocalDateTime endTime;

        @Size(max = 128, message = "{validation.oa.booking.origin-size}")
        private String origin;

        @Size(max = 128, message = "{validation.oa.booking.destination-size}")
        private String destination;

        @Size(max = 255, message = "{validation.oa.booking.reason-size}")
        private String reason;

        @Size(max = 255, message = "{validation.oa.booking.remark-size}")
        private String remark;
    }

    /**
     * 用车状态命令。
     */
    @Data
    public static class BookingStatusCommand {

        @NotBlank(message = "{validation.oa.booking-status.status-required}")
        private String status;
    }

    /**
     * 派车命令。
     */
    @Data
    public static class DispatchCommand {

        @NotNull(message = "{validation.oa.dispatch.vehicle-id-required}")
        private Long vehicleId;

        @NotNull(message = "{validation.oa.dispatch.driver-id-required}")
        private Long driverId;
    }

    /**
     * 回车登记命令。
     */
    @Data
    public static class ReturnCommand {

        @NotNull(message = "{validation.oa.return.start-mileage-required}")
        @DecimalMin(value = "0.0", message = "{validation.oa.return.start-mileage-min}")
        private BigDecimal startMileage;

        @NotNull(message = "{validation.oa.return.end-mileage-required}")
        @DecimalMin(value = "0.0", message = "{validation.oa.return.end-mileage-min}")
        private BigDecimal endMileage;

        @DecimalMin(value = "0.0", message = "{validation.oa.return.fuel-cost-min}")
        private BigDecimal fuelCost;

        @Size(max = 255, message = "{validation.oa.return.remark-size}")
        private String remark;
    }

    /**
     * 用章申请保存命令。
     */
    @Data
    public static class SealUseSaveCommand {

        @NotBlank(message = "{validation.oa.seal-use.applicant-required}")
        @Size(max = 64, message = "{validation.oa.seal-use.applicant-size}")
        private String applicant;

        private Long applicantId;

        private Long deptId;

        @NotNull(message = "{validation.oa.seal-use.seal-id-required}")
        private Long sealId;

        @Size(max = 64, message = "{validation.oa.seal-use.seal-name-size}")
        private String sealName;

        @NotBlank(message = "{validation.oa.seal-use.reason-required}")
        @Size(max = 255, message = "{validation.oa.seal-use.reason-size}")
        private String reason;

        private LocalDateTime useTime;

        @Size(max = 255, message = "{validation.oa.seal-use.remark-size}")
        private String remark;

        @Valid
        private List<SealUseFileCommand> files;
    }

    /**
     * 用章文件命令。
     */
    @Data
    public static class SealUseFileCommand {

        private Long fileId;

        @NotBlank(message = "{validation.oa.seal-use-file.file-name-required}")
        @Size(max = 255, message = "{validation.oa.seal-use-file.file-name-size}")
        private String fileName;
    }

    /**
     * 用章状态命令。
     */
    @Data
    public static class SealUseStatusCommand {

        @NotBlank(message = "{validation.oa.seal-use-status.status-required}")
        private String status;
    }

    /**
     * 采购申请保存命令。
     */
    @Data
    public static class PurchaseSaveCommand {

        @NotBlank(message = "{validation.oa.purchase.applicant-required}")
        @Size(max = 64, message = "{validation.oa.purchase.applicant-size}")
        private String applicant;

        private Long applicantId;

        private Long deptId;

        @NotBlank(message = "{validation.oa.purchase.reason-required}")
        @Size(max = 255, message = "{validation.oa.purchase.reason-size}")
        private String reason;

        private LocalDate expectedDate;

        private Long supplierId;

        private Long warehouseId;

        @Size(max = 8, message = "{validation.oa.purchase.currency-size}")
        private String currency;

        @Size(max = 255, message = "{validation.oa.purchase.remark-size}")
        private String remark;

        @NotEmpty(message = "{validation.oa.purchase.items-required}")
        @Valid
        private List<PurchaseItemCommand> items;
    }

    /**
     * 采购明细命令。
     */
    @Data
    public static class PurchaseItemCommand {

        @NotBlank(message = "{validation.oa.purchase-item.item-code-required}")
        @Size(max = 64, message = "{validation.oa.purchase-item.item-code-size}")
        private String itemCode;

        @Size(max = 128, message = "{validation.oa.purchase-item.item-name-size}")
        private String itemName;

        @NotNull(message = "{validation.oa.purchase-item.quantity-required}")
        @DecimalMin(value = "0.01", message = "{validation.oa.purchase-item.quantity-min}")
        private BigDecimal quantity;

        @NotNull(message = "{validation.oa.purchase-item.unit-price-required}")
        @DecimalMin(value = "0.0", message = "{validation.oa.purchase-item.unit-price-min}")
        private BigDecimal unitPrice;

        @Size(max = 255, message = "{validation.oa.purchase-item.supplier-advice-size}")
        private String supplierAdvice;
    }

    /**
     * 采购状态命令。
     */
    @Data
    public static class PurchaseStatusCommand {

        @NotBlank(message = "{validation.oa.purchase-status.status-required}")
        private String status;
    }
}
