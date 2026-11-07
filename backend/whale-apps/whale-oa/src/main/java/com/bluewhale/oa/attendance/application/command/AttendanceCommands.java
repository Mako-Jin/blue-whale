package com.bluewhale.oa.attendance.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 考勤写操作命令集合。
 *
 * @author BlueWhale
 */
public final class AttendanceCommands {

    /**
     * 工具类私有构造器。
     */
    private AttendanceCommands() {
    }

    /**
     * 打卡命令。
     */
    @Data
    public static class PunchCommand {

        /** 打卡类型：IN / OUT */
        @NotBlank(message = "打卡类型不能为空")
        private String punchType;

        /** 来源：APP / DEVICE / WEB，默认 WEB */
        private String source;

        /** 打卡时间，为空取当前时间 */
        private LocalDateTime punchTime;

        /** 纬度 */
        private BigDecimal latitude;

        /** 经度 */
        private BigDecimal longitude;

        /** 打卡地址 */
        @Size(max = 255, message = "地址长度不能超过 255")
        private String address;

        /** 设备编码 */
        @Size(max = 64, message = "设备编码长度不能超过 64")
        private String deviceCode;

        /** 用户姓名快照 */
        @Size(max = 64, message = "姓名长度不能超过 64")
        private String userName;

        /** 备注 */
        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 考勤组保存命令。
     */
    @Data
    public static class GroupSaveCommand {

        @NotBlank(message = "考勤组编码不能为空")
        @Size(max = 32, message = "考勤组编码长度不能超过 32")
        private String code;

        @NotBlank(message = "考勤组名称不能为空")
        @Size(max = 64, message = "考勤组名称长度不能超过 64")
        private String name;

        @NotNull(message = "班次不能为空")
        private Long shiftId;

        private Long ruleId;

        private Long locationId;

        private Long deviceId;

        private Integer enabled;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;

        /** 成员列表 */
        private List<Member> members;
    }

    /**
     * 考勤组成员。
     */
    @Data
    public static class Member {

        @NotNull(message = "成员用户ID不能为空")
        private Long userId;

        @Size(max = 64, message = "成员姓名长度不能超过 64")
        private String userName;
    }

    /**
     * 班次保存命令。
     */
    @Data
    public static class ShiftSaveCommand {

        @NotBlank(message = "班次编码不能为空")
        @Size(max = 32, message = "班次编码长度不能超过 32")
        private String code;

        @NotBlank(message = "班次名称不能为空")
        @Size(max = 64, message = "班次名称长度不能超过 64")
        private String name;

        @NotNull(message = "上班时间不能为空")
        private LocalTime workStart;

        @NotNull(message = "下班时间不能为空")
        private LocalTime workEnd;

        private Integer flexMinutes;

        private Integer breakMinutes;

        private Integer enabled;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 考勤规则保存命令。
     */
    @Data
    public static class RuleSaveCommand {

        @NotBlank(message = "规则编码不能为空")
        @Size(max = 32, message = "规则编码长度不能超过 32")
        private String code;

        @NotBlank(message = "规则名称不能为空")
        @Size(max = 64, message = "规则名称长度不能超过 64")
        private String name;

        private Integer allowExternal;

        private Integer requireLocation;

        private Integer requireDevice;

        private Integer maxCorrectionsPerMonth;

        private Integer correctionWindowDays;

        private Integer enabled;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 打卡地点保存命令。
     */
    @Data
    public static class LocationSaveCommand {

        @NotBlank(message = "地点编码不能为空")
        @Size(max = 32, message = "地点编码长度不能超过 32")
        private String code;

        @NotBlank(message = "地点名称不能为空")
        @Size(max = 64, message = "地点名称长度不能超过 64")
        private String name;

        @NotNull(message = "纬度不能为空")
        private BigDecimal latitude;

        @NotNull(message = "经度不能为空")
        private BigDecimal longitude;

        private Integer radiusMeters;

        private Integer enabled;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 打卡设备保存命令。
     */
    @Data
    public static class DeviceSaveCommand {

        @NotBlank(message = "设备编码不能为空")
        @Size(max = 64, message = "设备编码长度不能超过 64")
        private String code;

        @NotBlank(message = "设备名称不能为空")
        @Size(max = 64, message = "设备名称长度不能超过 64")
        private String name;

        @NotBlank(message = "设备类型不能为空")
        @Size(max = 64, message = "设备类型长度不能超过 64")
        private String deviceType;

        private Long locationId;

        private Integer enabled;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 补卡申请命令。
     */
    @Data
    public static class CorrectionSaveCommand {

        @NotNull(message = "补卡日期不能为空")
        private LocalDate workDate;

        @NotNull(message = "补卡时间不能为空")
        private LocalDateTime punchTime;

        @NotBlank(message = "补卡类型不能为空")
        private String punchType;

        @Size(max = 512, message = "补卡原因长度不能超过 512")
        private String reason;

        @Size(max = 64, message = "姓名长度不能超过 64")
        private String userName;
    }

    /**
     * 补卡状态变更命令。
     */
    @Data
    public static class CorrectionStatusCommand {

        @NotBlank(message = "状态不能为空")
        private String status;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 考勤异常处理命令。
     */
    @Data
    public static class ExceptionHandleCommand {

        @NotBlank(message = "状态不能为空")
        private String status;

        @Size(max = 255, message = "备注长度不能超过 255")
        private String remark;
    }

    /**
     * 考勤日报重算命令。
     */
    @Data
    public static class RecalculateCommand {

        @NotNull(message = "起始日期不能为空")
        private LocalDate dateFrom;

        @NotNull(message = "结束日期不能为空")
        private LocalDate dateTo;

        /** 指定用户，为空时重算全部在册成员 */
        private Long userId;
    }
}
