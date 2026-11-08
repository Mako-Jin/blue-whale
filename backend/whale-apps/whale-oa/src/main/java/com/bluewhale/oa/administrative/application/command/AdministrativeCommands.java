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

    /**
     * 工具类私有构造器。
     */
    private AdministrativeCommands() {
    }

    /**
     * 车辆保存命令。
     */
    @Data
    public static class VehicleSaveCommand {

        @NotBlank(message = "车牌号不能为空")
        @Size(max = 32, message = "车牌号长度不能超过 32")
        private String plateNo;

        @Size(max = 32, message = "车型长度不能超过 32")
        private String vehicleType;

        private Integer seats;

        private String status;

        @DecimalMin(value = "0.0", message = "当前里程不能为负数")
        private BigDecimal currentMileage;

        private LocalDate purchaseDate;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 司机保存命令。
     */
    @Data
    public static class DriverSaveCommand {

        @NotBlank(message = "司机姓名不能为空")
        @Size(max = 64, message = "姓名长度不能超过 64")
        private String name;

        @Size(max = 32, message = "联系电话长度不能超过 32")
        private String phone;

        @NotBlank(message = "驾驶证号不能为空")
        @Size(max = 64, message = "驾驶证号长度不能超过 64")
        private String licenseNo;

        @Size(max = 16, message = "准驾车型长度不能超过 16")
        private String licenseType;

        private String status;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 用车预约保存命令。
     */
    @Data
    public static class BookingSaveCommand {

        @NotBlank(message = "申请人不能为空")
        @Size(max = 64, message = "申请人账号长度不能超过 64")
        private String applicant;

        private Long applicantId;

        private Long deptId;

        @NotNull(message = "用车开始时间不能为空")
        private LocalDateTime startTime;

        @NotNull(message = "用车结束时间不能为空")
        private LocalDateTime endTime;

        @Size(max = 128, message = "起点长度不能超过 128")
        private String origin;

        @Size(max = 128, message = "终点长度不能超过 128")
        private String destination;

        @Size(max = 255, message = "用车事由长度不能超过 255")
        private String reason;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 用车状态命令。
     */
    @Data
    public static class BookingStatusCommand {

        @NotBlank(message = "状态不能为空")
        private String status;
    }

    /**
     * 派车命令。
     */
    @Data
    public static class DispatchCommand {

        @NotNull(message = "车辆不能为空")
        private Long vehicleId;

        @NotNull(message = "司机不能为空")
        private Long driverId;
    }

    /**
     * 回车登记命令。
     */
    @Data
    public static class ReturnCommand {

        @NotNull(message = "出车里程不能为空")
        @DecimalMin(value = "0.0", message = "出车里程不能为负数")
        private BigDecimal startMileage;

        @NotNull(message = "回车里程不能为空")
        @DecimalMin(value = "0.0", message = "回车里程不能为负数")
        private BigDecimal endMileage;

        @DecimalMin(value = "0.0", message = "油费不能为负数")
        private BigDecimal fuelCost;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 用章申请保存命令。
     */
    @Data
    public static class SealUseSaveCommand {

        @NotBlank(message = "申请人不能为空")
        @Size(max = 64, message = "申请人账号长度不能超过 64")
        private String applicant;

        private Long applicantId;

        private Long deptId;

        @NotNull(message = "印章不能为空")
        private Long sealId;

        @Size(max = 64, message = "印章名称长度不能超过 64")
        private String sealName;

        @NotBlank(message = "用章事由不能为空")
        @Size(max = 255, message = "用章事由长度不能超过 255")
        private String reason;

        private LocalDateTime useTime;

        @Size(max = 255, message = "备注长度不能超过 255")
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

        @NotBlank(message = "文件名不能为空")
        @Size(max = 255, message = "文件名长度不能超过 255")
        private String fileName;
    }

    /**
     * 用章状态命令。
     */
    @Data
    public static class SealUseStatusCommand {

        @NotBlank(message = "状态不能为空")
        private String status;
    }

    /**
     * 采购申请保存命令。
     */
    @Data
    public static class PurchaseSaveCommand {

        @NotBlank(message = "申请人不能为空")
        @Size(max = 64, message = "申请人账号长度不能超过 64")
        private String applicant;

        private Long applicantId;

        private Long deptId;

        @NotBlank(message = "采购事由不能为空")
        @Size(max = 255, message = "采购事由长度不能超过 255")
        private String reason;

        private LocalDate expectedDate;

        private Long supplierId;

        private Long warehouseId;

        @Size(max = 8, message = "币种长度不能超过 8")
        private String currency;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;

        @NotEmpty(message = "采购明细不能为空")
        @Valid
        private List<PurchaseItemCommand> items;
    }

    /**
     * 采购明细命令。
     */
    @Data
    public static class PurchaseItemCommand {

        @NotBlank(message = "物料编码不能为空")
        @Size(max = 64, message = "物料编码长度不能超过 64")
        private String itemCode;

        @Size(max = 128, message = "物料名称长度不能超过 128")
        private String itemName;

        @NotNull(message = "数量不能为空")
        @DecimalMin(value = "0.01", message = "数量必须大于 0")
        private BigDecimal quantity;

        @NotNull(message = "单价不能为空")
        @DecimalMin(value = "0.0", message = "单价不能为负数")
        private BigDecimal unitPrice;

        @Size(max = 255, message = "供应商建议长度不能超过 255")
        private String supplierAdvice;
    }

    /**
     * 采购状态命令。
     */
    @Data
    public static class PurchaseStatusCommand {

        @NotBlank(message = "状态不能为空")
        private String status;
    }
}
