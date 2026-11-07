package com.bluewhale.oa.attendance.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.service.AttendanceDailyAppService;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;
import com.bluewhale.oa.attendance.domain.model.AttendanceDaily;
import com.bluewhale.oa.attendance.domain.model.AttendanceDailyQuery;
import com.bluewhale.oa.attendance.domain.model.AttendanceException;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroup;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroupMember;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecord;
import com.bluewhale.oa.attendance.domain.model.AttendanceShift;
import com.bluewhale.oa.attendance.domain.model.AttendanceStatus;
import com.bluewhale.oa.attendance.domain.repository.AttendanceConfigRepository;
import com.bluewhale.oa.attendance.domain.repository.AttendanceDailyRepository;
import com.bluewhale.oa.attendance.domain.repository.AttendanceExceptionRepository;
import com.bluewhale.oa.attendance.domain.repository.AttendanceRecordRepository;
import com.bluewhale.oa.attendance.domain.service.AttendanceDailyCalculator;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.domain.model.Leave;
import com.bluewhale.oa.hr.domain.model.Trip;
import com.bluewhale.oa.hr.domain.repository.LeaveRepository;
import com.bluewhale.oa.hr.domain.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考勤日报应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AttendanceDailyAppServiceImpl implements AttendanceDailyAppService {

    /** 单次重算最大跨度（天） */
    private static final int MAX_RANGE_DAYS = 62;

    /** 单次取已批准请假/出差的最大条数 */
    private static final int LEAVE_FETCH_LIMIT = 5000;

    private final AttendanceDailyRepository dailyRepository;

    private final AttendanceRecordRepository recordRepository;

    private final AttendanceExceptionRepository exceptionRepository;

    private final AttendanceConfigRepository configRepository;

    private final LeaveRepository leaveRepository;

    private final TripRepository tripRepository;

    @Override
    public PageResult<AttendanceViews.DailyView> page(AttendanceDailyQuery query) {
        PageResult<AttendanceDaily> page = dailyRepository.page(query);
        return PageResult.of(page.getRecords().stream().map(AttendanceViews.DailyView::from).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long recalculate(AttendanceCommands.RecalculateCommand command) {
        LocalDate from = command.getDateFrom();
        LocalDate to = command.getDateTo();
        if (from == null || to == null || to.isBefore(from)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "日期区间非法");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "单次重算跨度不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        List<Long> userIds = resolveUserIds(command.getUserId());
        if (userIds.isEmpty()) {
            return 0;
        }
        List<Leave> leaves = leaveRepository.listApprovedOverlapping(userIds, from, to, LEAVE_FETCH_LIMIT);
        List<Trip> trips = tripRepository.listApprovedOverlapping(userIds, from, to, LEAVE_FETCH_LIMIT);
        Map<Long, List<Leave>> leaveMap = leaves.stream()
                .filter(leave -> leave.getApplicantId() != null)
                .collect(Collectors.groupingBy(Leave::getApplicantId));
        Map<Long, List<Trip>> tripMap = trips.stream()
                .filter(trip -> trip.getApplicantId() != null)
                .collect(Collectors.groupingBy(Trip::getApplicantId));

        long count = 0;
        for (Long userId : userIds) {
            AttendanceGroup group = configRepository.findGroupByUserId(userId).orElse(null);
            if (group == null || group.getShiftId() == null) {
                continue;
            }
            AttendanceShift shift = configRepository.findShift(group.getShiftId()).orElse(null);
            if (shift == null) {
                continue;
            }
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (isWeekend(date)) {
                    continue;
                }
                count += recalculateOne(userId, date, group, shift, leaveMap, tripMap);
            }
        }
        return count;
    }

    /**
     * 重算单个用户单日，返回写入条数。
     */
    private long recalculateOne(Long userId, LocalDate date, AttendanceGroup group, AttendanceShift shift,
                                Map<Long, List<Leave>> leaveMap, Map<Long, List<Trip>> tripMap) {
        List<AttendanceRecord> records = recordRepository.listByUser(userId, date, date);
        LocalDateTime firstIn = records.stream()
                .filter(record -> AttendanceStatus.PUNCH_IN.equals(record.getPunchType()))
                .map(AttendanceRecord::getPunchTime)
                .min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastOut = records.stream()
                .filter(record -> AttendanceStatus.PUNCH_OUT.equals(record.getPunchType()))
                .map(AttendanceRecord::getPunchTime)
                .max(LocalDateTime::compareTo).orElse(null);

        boolean onLeave = covers(leaveMap.get(userId), date);
        boolean onOfficial = coversTrip(tripMap.get(userId), date);
        AttendanceDailyCalculator.DailyComputation computation = AttendanceDailyCalculator.compute(
                shift, firstIn, lastOut, onLeave, onOfficial, date);

        AttendanceDaily daily = dailyRepository.findByUserAndDate(userId, date).orElseGet(AttendanceDaily::new);
        daily.setUserId(userId);
        if (daily.getId() == null) {
            daily.setUserName(records.isEmpty() ? null : records.get(0).getUserName());
        }
        daily.setWorkDate(date);
        daily.setGroupId(group.getId());
        daily.setShiftCode(shift.getCode());
        daily.setShiftStart(shift.getWorkStart());
        daily.setShiftEnd(shift.getWorkEnd());
        daily.setFirstIn(computation.getFirstIn());
        daily.setLastOut(computation.getLastOut());
        daily.setLateMinutes(computation.getLateMinutes());
        daily.setEarlyMinutes(computation.getEarlyMinutes());
        daily.setAbsent(computation.getAbsent());
        daily.setStatus(computation.getStatus());
        daily.setRemark(computation.getRemark());
        dailyRepository.save(daily);
        syncException(daily);
        return 1;
    }

    /**
     * 迟到/早退/缺卡派生异常；已处理（确认/豁免）的异常不重复打开。
     */
    private void syncException(AttendanceDaily daily) {
        String type = switch (daily.getStatus()) {
            case AttendanceStatus.DAILY_LATE -> AttendanceStatus.EXCEPTION_LATE;
            case AttendanceStatus.DAILY_EARLY -> AttendanceStatus.EXCEPTION_EARLY;
            case AttendanceStatus.DAILY_LATE_AND_EARLY -> AttendanceStatus.EXCEPTION_LATE;
            case AttendanceStatus.DAILY_ABSENT -> AttendanceStatus.EXCEPTION_ABSENT;
            default -> null;
        };
        if (type == null) {
            return;
        }
        String description = switch (daily.getStatus()) {
            case AttendanceStatus.DAILY_LATE -> "迟到 " + daily.getLateMinutes() + " 分钟";
            case AttendanceStatus.DAILY_EARLY -> "早退 " + daily.getEarlyMinutes() + " 分钟";
            case AttendanceStatus.DAILY_LATE_AND_EARLY -> "迟到 " + daily.getLateMinutes()
                    + " 分钟，早退 " + daily.getEarlyMinutes() + " 分钟";
            default -> daily.getRemark() == null ? "缺卡" : daily.getRemark();
        };
        AttendanceException exception = exceptionRepository.find(daily.getUserId(), daily.getWorkDate(), type)
                .orElseGet(AttendanceException::new);
        if (exception.getId() != null && !AttendanceStatus.EXCEPTION_PENDING.equals(exception.getStatus())) {
            return;
        }
        exception.setUserId(daily.getUserId());
        exception.setUserName(daily.getUserName());
        exception.setWorkDate(daily.getWorkDate());
        exception.setType(type);
        exception.setDescription(description);
        if (exception.getId() == null) {
            exception.setStatus(AttendanceStatus.EXCEPTION_PENDING);
            exceptionRepository.save(exception);
        } else {
            exceptionRepository.update(exception);
        }
    }

    /**
     * 解析待重算用户：指定用户时仅该用户，否则取全部考勤组成员。
     *
     * @param userId 用户ID，可为空
     * @return 用户ID列表
     */
    private List<Long> resolveUserIds(Long userId) {
        if (userId != null) {
            return List.of(userId);
        }
        List<Long> ids = new ArrayList<>();
        for (AttendanceGroupMember member : configRepository.listAllMembers()) {
            if (!ids.contains(member.getUserId())) {
                ids.add(member.getUserId());
            }
        }
        return ids;
    }

    /**
     * 判断指定日期是否落在请假区间内。
     *
     * @param leaves 请假列表
     * @param date 工作日期
     * @return 是否覆盖
     */
    private static boolean covers(List<Leave> leaves, LocalDate date) {
        if (leaves == null) {
            return false;
        }
        return leaves.stream().anyMatch(leave -> !leave.getStartDate().isAfter(date)
                && !leave.getEndDate().isBefore(date));
    }

    /**
     * 判断指定日期是否落在出差区间内。
     *
     * @param trips 出差列表
     * @param date 工作日期
     * @return 是否覆盖
     */
    private static boolean coversTrip(List<Trip> trips, LocalDate date) {
        if (trips == null) {
            return false;
        }
        return trips.stream().anyMatch(trip -> !trip.getStartDate().isAfter(date)
                && !trip.getEndDate().isBefore(date));
    }

    /**
     * 判断日期是否为周末。
     *
     * @param date 日期
     * @return 是否周末
     */
    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
