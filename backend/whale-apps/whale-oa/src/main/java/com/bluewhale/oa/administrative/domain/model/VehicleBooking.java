package com.bluewhale.oa.administrative.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用车预约聚合根。
 *
 * <p>状态机：{@code PENDING → APPROVED / REJECTED / CANCELLED}；
 * 仅 {@code APPROVED} 可派车，派车后写入车辆与司机。</p>
 *
 * @author BlueWhale
 */
@Data
public class VehicleBooking {

    /** 主键ID */
    private Long id;

    /** 用车单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 申请部门ID */
    private Long deptId;

    /** 派车车辆ID */
    private Long vehicleId;

    /** 派车司机ID */
    private Long driverId;

    /** 用车开始时间 */
    private LocalDateTime startTime;

    /** 用车结束时间 */
    private LocalDateTime endTime;

    /** 起点 */
    private String origin;

    /** 终点 */
    private String destination;

    /** 用车事由 */
    private String reason;

    /** 状态 */
    private String status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验时间区间。
     */
    public void assertTimeRange() {
        if (startTime == null || endTime == null || startTime.isAfter(endTime)) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "用车开始时间必须早于结束时间");
        }
    }

    /**
     * 派车：仅审批通过且未派车时允许。
     *
     * @param vehicleId 车辆ID
     * @param driverId 司机ID
     */
    public void dispatch(Long vehicleId, Long driverId) {
        if (BookingStatus.of(status) != BookingStatus.APPROVED) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, "仅审批通过的用车单可派车");
        }
        if (this.vehicleId != null) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, "用车单已派车，不能重复派车");
        }
        if (vehicleId == null || driverId == null) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_INVALID, "派车车辆与司机不能为空");
        }
        this.vehicleId = vehicleId;
        this.driverId = driverId;
    }

    /**
     * 变更状态：终态不可回流，重复终态幂等返回 false。
     *
     * @param target 目标状态
     * @return 是否发生变更
     */
    public boolean changeStatus(BookingStatus target) {
        BookingStatus current = BookingStatus.of(status);
        if (current == target) {
            return false;
        }
        if (current != BookingStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, "用车单已处于终态，不可再次变更");
        }
        if (target == BookingStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, "用车单不能回到待审批状态");
        }
        this.status = target.name();
        return true;
    }

    /**
     * 校验是否可编辑。
     */
    public void assertEditable() {
        if (BookingStatus.of(status) != BookingStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.VEHICLE_BOOKING_STATUS_CONFLICT, "仅待审批的用车单可编辑");
        }
    }
}
