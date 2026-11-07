package com.bluewhale.oa.attendance.application.view;

import com.bluewhale.oa.attendance.domain.model.AttendanceCorrection;
import com.bluewhale.oa.attendance.domain.model.AttendanceDaily;
import com.bluewhale.oa.attendance.domain.model.AttendanceDevice;
import com.bluewhale.oa.attendance.domain.model.AttendanceException;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroup;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroupMember;
import com.bluewhale.oa.attendance.domain.model.AttendanceLocation;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecord;
import com.bluewhale.oa.attendance.domain.model.AttendanceRule;
import com.bluewhale.oa.attendance.domain.model.AttendanceShift;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 考勤视图集合。
 *
 * @author BlueWhale
 */
public final class AttendanceViews {

    /**
     * 工具类私有构造器。
     */
    private AttendanceViews() {
    }

    /**
     * 打卡记录视图。
     */
    @Data
    public static class RecordView {

        private Long id;

        private Long userId;

        private String userName;

        private LocalDateTime punchTime;

        private String punchType;

        private String source;

        private BigDecimal latitude;

        private BigDecimal longitude;

        private String address;

        private String deviceCode;

        private Integer correction;

        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param model 打卡记录
         * @return 视图
         */
        public static RecordView from(AttendanceRecord model) {
            RecordView view = new RecordView();
            view.setId(model.getId());
            view.setUserId(model.getUserId());
            view.setUserName(model.getUserName());
            view.setPunchTime(model.getPunchTime());
            view.setPunchType(model.getPunchType());
            view.setSource(model.getSource());
            view.setLatitude(model.getLatitude());
            view.setLongitude(model.getLongitude());
            view.setAddress(model.getAddress());
            view.setDeviceCode(model.getDeviceCode());
            view.setCorrection(model.getCorrection());
            view.setRemark(model.getRemark());
            return view;
        }
    }

    /**
     * 考勤日报视图。
     */
    @Data
    public static class DailyView {

        private Long id;

        private Long userId;

        private String userName;

        private LocalDate workDate;

        private Long groupId;

        private String shiftCode;

        private LocalTime shiftStart;

        private LocalTime shiftEnd;

        private LocalDateTime firstIn;

        private LocalDateTime lastOut;

        private Integer lateMinutes;

        private Integer earlyMinutes;

        private Integer absent;

        private String status;

        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param model 日汇总
         * @return 视图
         */
        public static DailyView from(AttendanceDaily model) {
            DailyView view = new DailyView();
            view.setId(model.getId());
            view.setUserId(model.getUserId());
            view.setUserName(model.getUserName());
            view.setWorkDate(model.getWorkDate());
            view.setGroupId(model.getGroupId());
            view.setShiftCode(model.getShiftCode());
            view.setShiftStart(model.getShiftStart());
            view.setShiftEnd(model.getShiftEnd());
            view.setFirstIn(model.getFirstIn());
            view.setLastOut(model.getLastOut());
            view.setLateMinutes(model.getLateMinutes());
            view.setEarlyMinutes(model.getEarlyMinutes());
            view.setAbsent(model.getAbsent());
            view.setStatus(model.getStatus());
            view.setRemark(model.getRemark());
            return view;
        }
    }

    /**
     * 考勤组视图。
     */
    @Data
    public static class GroupView {

        private Long id;

        private String code;

        private String name;

        private Long shiftId;

        private Long ruleId;

        private Long locationId;

        private Long deviceId;

        private Integer enabled;

        private String remark;

        private List<MemberView> members;

        /**
         * 由领域对象构建视图。
         *
         * @param model 考勤组
         * @param members 成员
         * @return 视图
         */
        public static GroupView from(AttendanceGroup model, List<AttendanceGroupMember> members) {
            GroupView view = new GroupView();
            view.setId(model.getId());
            view.setCode(model.getCode());
            view.setName(model.getName());
            view.setShiftId(model.getShiftId());
            view.setRuleId(model.getRuleId());
            view.setLocationId(model.getLocationId());
            view.setDeviceId(model.getDeviceId());
            view.setEnabled(model.getEnabled());
            view.setRemark(model.getRemark());
            view.setMembers(members.stream().map(MemberView::from).toList());
            return view;
        }
    }

    /**
     * 考勤组成员视图。
     */
    @Data
    public static class MemberView {

        private Long userId;

        private String userName;

        /**
         * 由领域对象构建视图。
         *
         * @param model 成员
         * @return 视图
         */
        public static MemberView from(AttendanceGroupMember model) {
            MemberView view = new MemberView();
            view.setUserId(model.getUserId());
            view.setUserName(model.getUserName());
            return view;
        }
    }

    /**
     * 班次视图。
     */
    @Data
    public static class ShiftView {

        private Long id;

        private String code;

        private String name;

        private LocalTime workStart;

        private LocalTime workEnd;

        private Integer flexMinutes;

        private Integer breakMinutes;

        private Integer enabled;

        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param model 班次
         * @return 视图
         */
        public static ShiftView from(AttendanceShift model) {
            ShiftView view = new ShiftView();
            view.setId(model.getId());
            view.setCode(model.getCode());
            view.setName(model.getName());
            view.setWorkStart(model.getWorkStart());
            view.setWorkEnd(model.getWorkEnd());
            view.setFlexMinutes(model.getFlexMinutes());
            view.setBreakMinutes(model.getBreakMinutes());
            view.setEnabled(model.getEnabled());
            view.setRemark(model.getRemark());
            return view;
        }
    }

    /**
     * 考勤规则视图。
     */
    @Data
    public static class RuleView {

        private Long id;

        private String code;

        private String name;

        private Integer allowExternal;

        private Integer requireLocation;

        private Integer requireDevice;

        private Integer maxCorrectionsPerMonth;

        private Integer correctionWindowDays;

        private Integer enabled;

        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param model 规则
         * @return 视图
         */
        public static RuleView from(AttendanceRule model) {
            RuleView view = new RuleView();
            view.setId(model.getId());
            view.setCode(model.getCode());
            view.setName(model.getName());
            view.setAllowExternal(model.getAllowExternal());
            view.setRequireLocation(model.getRequireLocation());
            view.setRequireDevice(model.getRequireDevice());
            view.setMaxCorrectionsPerMonth(model.getMaxCorrectionsPerMonth());
            view.setCorrectionWindowDays(model.getCorrectionWindowDays());
            view.setEnabled(model.getEnabled());
            view.setRemark(model.getRemark());
            return view;
        }
    }

    /**
     * 打卡地点视图。
     */
    @Data
    public static class LocationView {

        private Long id;

        private String code;

        private String name;

        private BigDecimal latitude;

        private BigDecimal longitude;

        private Integer radiusMeters;

        private Integer enabled;

        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param model 地点
         * @return 视图
         */
        public static LocationView from(AttendanceLocation model) {
            LocationView view = new LocationView();
            view.setId(model.getId());
            view.setCode(model.getCode());
            view.setName(model.getName());
            view.setLatitude(model.getLatitude());
            view.setLongitude(model.getLongitude());
            view.setRadiusMeters(model.getRadiusMeters());
            view.setEnabled(model.getEnabled());
            view.setRemark(model.getRemark());
            return view;
        }
    }

    /**
     * 打卡设备视图。
     */
    @Data
    public static class DeviceView {

        private Long id;

        private String code;

        private String name;

        private String deviceType;

        private Long locationId;

        private Integer enabled;

        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param model 设备
         * @return 视图
         */
        public static DeviceView from(AttendanceDevice model) {
            DeviceView view = new DeviceView();
            view.setId(model.getId());
            view.setCode(model.getCode());
            view.setName(model.getName());
            view.setDeviceType(model.getDeviceType());
            view.setLocationId(model.getLocationId());
            view.setEnabled(model.getEnabled());
            view.setRemark(model.getRemark());
            return view;
        }
    }

    /**
     * 补卡申请视图。
     */
    @Data
    public static class CorrectionView {

        private Long id;

        private String code;

        private Long userId;

        private String userName;

        private LocalDate workDate;

        private LocalDateTime punchTime;

        private String punchType;

        private String reason;

        private String status;

        private Long approvalInstanceId;

        private String handler;

        private LocalDateTime handledTime;

        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param model 补卡申请
         * @return 视图
         */
        public static CorrectionView from(AttendanceCorrection model) {
            CorrectionView view = new CorrectionView();
            view.setId(model.getId());
            view.setCode(model.getCode());
            view.setUserId(model.getUserId());
            view.setUserName(model.getUserName());
            view.setWorkDate(model.getWorkDate());
            view.setPunchTime(model.getPunchTime());
            view.setPunchType(model.getPunchType());
            view.setReason(model.getReason());
            view.setStatus(model.getStatus());
            view.setApprovalInstanceId(model.getApprovalInstanceId());
            view.setHandler(model.getHandler());
            view.setHandledTime(model.getHandledTime());
            view.setRemark(model.getRemark());
            return view;
        }
    }

    /**
     * 考勤异常视图。
     */
    @Data
    public static class ExceptionView {

        private Long id;

        private Long userId;

        private String userName;

        private LocalDate workDate;

        private String type;

        private String description;

        private String status;

        private String handler;

        private LocalDateTime handledTime;

        private String remark;

        /**
         * 由领域对象构建视图。
         *
         * @param model 异常
         * @return 视图
         */
        public static ExceptionView from(AttendanceException model) {
            ExceptionView view = new ExceptionView();
            view.setId(model.getId());
            view.setUserId(model.getUserId());
            view.setUserName(model.getUserName());
            view.setWorkDate(model.getWorkDate());
            view.setType(model.getType());
            view.setDescription(model.getDescription());
            view.setStatus(model.getStatus());
            view.setHandler(model.getHandler());
            view.setHandledTime(model.getHandledTime());
            view.setRemark(model.getRemark());
            return view;
        }
    }
}
