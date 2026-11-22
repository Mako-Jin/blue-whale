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
        @NotBlank(message = "{validation.oa.punch.punch-type-required}")
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
        @Size(max = 255, message = "{validation.oa.punch.address-size}")
        private String address;

        /** 设备编码 */
        @Size(max = 64, message = "{validation.oa.punch.device-code-size}")
        private String deviceCode;

        /** 用户姓名快照 */
        @Size(max = 64, message = "{validation.oa.punch.user-name-size}")
        private String userName;

        /** 备注 */
        @Size(max = 255, message = "{validation.oa.punch.remark-size}")
        private String remark;
    }

    /**
     * 考勤组保存命令。
     */
    @Data
    public static class GroupSaveCommand {

        @NotBlank(message = "{validation.oa.group.code-required}")
        @Size(max = 32, message = "{validation.oa.group.code-size}")
        private String code;

        @NotBlank(message = "{validation.oa.group.name-required}")
        @Size(max = 64, message = "{validation.oa.group.name-size}")
        private String name;

        @NotNull(message = "{validation.oa.group.shift-id-required}")
        private Long shiftId;

        private Long ruleId;

        private Long locationId;

        private Long deviceId;

        private Integer enabled;

        @Size(max = 255, message = "{validation.oa.group.remark-size}")
        private String remark;

        /** 成员列表 */
        private List<Member> members;
    }

    /**
     * 考勤组成员。
     */
    @Data
    public static class Member {

        @NotNull(message = "{validation.oa.member.user-id-required}")
        private Long userId;

        @Size(max = 64, message = "{validation.oa.member.user-name-size}")
        private String userName;
    }

    /**
     * 班次保存命令。
     */
    @Data
    public static class ShiftSaveCommand {

        @NotBlank(message = "{validation.oa.shift.code-required}")
        @Size(max = 32, message = "{validation.oa.shift.code-size}")
        private String code;

        @NotBlank(message = "{validation.oa.shift.name-required}")
        @Size(max = 64, message = "{validation.oa.shift.name-size}")
        private String name;

        @NotNull(message = "{validation.oa.shift.work-start-required}")
        private LocalTime workStart;

        @NotNull(message = "{validation.oa.shift.work-end-required}")
        private LocalTime workEnd;

        private Integer flexMinutes;

        private Integer breakMinutes;

        private Integer enabled;

        @Size(max = 255, message = "{validation.oa.shift.remark-size}")
        private String remark;
    }

    /**
     * 考勤规则保存命令。
     */
    @Data
    public static class RuleSaveCommand {

        @NotBlank(message = "{validation.oa.rule.code-required}")
        @Size(max = 32, message = "{validation.oa.rule.code-size}")
        private String code;

        @NotBlank(message = "{validation.oa.rule.name-required}")
        @Size(max = 64, message = "{validation.oa.rule.name-size}")
        private String name;

        private Integer allowExternal;

        private Integer requireLocation;

        private Integer requireDevice;

        private Integer maxCorrectionsPerMonth;

        private Integer correctionWindowDays;

        private Integer enabled;

        @Size(max = 255, message = "{validation.oa.rule.remark-size}")
        private String remark;
    }

    /**
     * 打卡地点保存命令。
     */
    @Data
    public static class LocationSaveCommand {

        @NotBlank(message = "{validation.oa.location.code-required}")
        @Size(max = 32, message = "{validation.oa.location.code-size}")
        private String code;

        @NotBlank(message = "{validation.oa.location.name-required}")
        @Size(max = 64, message = "{validation.oa.location.name-size}")
        private String name;

        @NotNull(message = "{validation.oa.location.latitude-required}")
        private BigDecimal latitude;

        @NotNull(message = "{validation.oa.location.longitude-required}")
        private BigDecimal longitude;

        private Integer radiusMeters;

        private Integer enabled;

        @Size(max = 255, message = "{validation.oa.location.remark-size}")
        private String remark;
    }

    /**
     * 打卡设备保存命令。
     */
    @Data
    public static class DeviceSaveCommand {

        @NotBlank(message = "{validation.oa.device.code-required}")
        @Size(max = 64, message = "{validation.oa.device.code-size}")
        private String code;

        @NotBlank(message = "{validation.oa.device.name-required}")
        @Size(max = 64, message = "{validation.oa.device.name-size}")
        private String name;

        @NotBlank(message = "{validation.oa.device.device-type-required}")
        @Size(max = 64, message = "{validation.oa.device.device-type-size}")
        private String deviceType;

        private Long locationId;

        private Integer enabled;

        @Size(max = 255, message = "{validation.oa.device.remark-size}")
        private String remark;
    }

    /**
     * 补卡申请命令。
     */
    @Data
    public static class CorrectionSaveCommand {

        @NotNull(message = "{validation.oa.correction.work-date-required}")
        private LocalDate workDate;

        @NotNull(message = "{validation.oa.correction.punch-time-required}")
        private LocalDateTime punchTime;

        @NotBlank(message = "{validation.oa.correction.punch-type-required}")
        private String punchType;

        @Size(max = 512, message = "{validation.oa.correction.reason-size}")
        private String reason;

        @Size(max = 64, message = "{validation.oa.correction.user-name-size}")
        private String userName;
    }

    /**
     * 补卡状态变更命令。
     */
    @Data
    public static class CorrectionStatusCommand {

        @NotBlank(message = "{validation.oa.correction-status.status-required}")
        private String status;

        @Size(max = 255, message = "{validation.oa.correction-status.remark-size}")
        private String remark;
    }

    /**
     * 考勤异常处理命令。
     */
    @Data
    public static class ExceptionHandleCommand {

        @NotBlank(message = "{validation.oa.exception-handle.status-required}")
        private String status;

        @Size(max = 255, message = "{validation.oa.exception-handle.remark-size}")
        private String remark;
    }

    /**
     * 考勤日报重算命令。
     */
    @Data
    public static class RecalculateCommand {

        @NotNull(message = "{validation.oa.recalculate.date-from-required}")
        private LocalDate dateFrom;

        @NotNull(message = "{validation.oa.recalculate.date-to-required}")
        private LocalDate dateTo;

        /** 指定用户，为空时重算全部在册成员 */
        private Long userId;
    }
}
