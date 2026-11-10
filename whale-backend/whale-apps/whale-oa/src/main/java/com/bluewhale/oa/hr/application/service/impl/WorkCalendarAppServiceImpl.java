package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.WorkCalendarSaveCommand;
import com.bluewhale.oa.hr.application.service.WorkCalendarAppService;
import com.bluewhale.oa.hr.application.view.WorkCalendarView;
import com.bluewhale.oa.hr.domain.model.WorkCalendarDay;
import com.bluewhale.oa.hr.domain.model.WorkCalendarQuery;
import com.bluewhale.oa.hr.domain.repository.WorkCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作日历应用服务实现。
 *
 * <p>无日历记录时按周一至周五兜底为工作日，标准工时 8 小时；
 * 有记录时以记录为准（含节假日与调休）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkCalendarAppServiceImpl implements WorkCalendarAppService {

    /** 上午时段 */
    private static final String PERIOD_AM = "AM";

    /** 下午时段 */
    private static final String PERIOD_PM = "PM";

    /** 默认标准工时 */
    private static final BigDecimal DEFAULT_WORK_HOURS = new BigDecimal("8.0");

    /** 半天系数 */
    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");

    /** 整天系数 */
    private static final BigDecimal FULL_DAY = BigDecimal.ONE;

    private final WorkCalendarRepository workCalendarRepository;

    @Override
    public PageResult<WorkCalendarView> page(WorkCalendarQuery query) {
        resolveRange(query);
        List<WorkCalendarView> records = workCalendarRepository.page(query).stream()
                .map(this::toView).toList();
        return PageResult.of(records, workCalendarRepository.count(query),
                query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkCalendarView upsert(LocalDate date, WorkCalendarSaveCommand command) {
        if (date == null || command == null || command.getWorkday() == null) {
            throw OaErrorCode.exception(OaErrorCode.WORK_CALENDAR_INVALID, "日期与是否工作日不能为空");
        }
        WorkCalendarDay day = workCalendarRepository.findByDate(date).orElse(null);
        boolean exists = day != null;
        if (!exists) {
            day = new WorkCalendarDay();
            day.setCalendarDate(date);
        }
        day.setWorkday(command.getWorkday());
        day.setWorkHours(command.getWorkHours() == null ? DEFAULT_WORK_HOURS : command.getWorkHours());
        day.setHolidayName(command.getHolidayName());
        day.setRemark(command.getRemark());
        if (exists) {
            workCalendarRepository.updateByDate(day);
        } else {
            workCalendarRepository.insert(day);
        }
        log.info("工作日历已保存: date={}, workday={}", date, command.getWorkday());
        return toView(day);
    }

    @Override
    public BigDecimal countWorkdays(LocalDate startDate, LocalDate endDate) {
        return countWorkdays(startDate, null, endDate, null);
    }

    @Override
    public BigDecimal countWorkdays(LocalDate startDate, String startPeriod, LocalDate endDate, String endPeriod) {
        if (startDate == null || endDate == null) {
            throw OaErrorCode.exception(OaErrorCode.WORK_CALENDAR_INVALID, "开始日期与结束日期不能为空");
        }
        if (endDate.isBefore(startDate)) {
            throw OaErrorCode.exception(OaErrorCode.WORK_CALENDAR_INVALID, "结束日期不能早于开始日期");
        }
        validatePeriod(startPeriod);
        validatePeriod(endPeriod);
        Map<LocalDate, Boolean> recorded = workCalendarRepository.listByDateRange(startDate, endDate).stream()
                .collect(Collectors.toMap(WorkCalendarDay::getCalendarDate,
                        WorkCalendarDay::isWorkday, (first, second) -> second));
        boolean singleDay = startDate.equals(endDate);
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            boolean workday = recorded.getOrDefault(date, defaultWorkday(date));
            boolean fullDay = resolveFullDay(date, startDate, endDate, singleDay, startPeriod, endPeriod);
            total = total.add(fraction(workday, fullDay));
        }
        return total;
    }

    @Override
    public BigDecimal workdayFraction(LocalDate date, String period) {
        if (date == null) {
            throw OaErrorCode.exception(OaErrorCode.WORK_CALENDAR_INVALID, "日期不能为空");
        }
        validatePeriod(period);
        boolean fullDay = period == null;
        return fraction(isWorkday(date), fullDay);
    }

    @Override
    public boolean isWorkday(LocalDate date) {
        return workCalendarRepository.findByDate(date).map(WorkCalendarDay::isWorkday)
                .orElseGet(() -> defaultWorkday(date));
    }

    /**
     * 解析查询区间：优先使用年月展开为自然月。
     *
     * @param query 查询条件
     */
    private void resolveRange(WorkCalendarQuery query) {
        if (!StringUtils.hasText(query.getYearMonth())) {
            return;
        }
        try {
            YearMonth yearMonth = YearMonth.parse(query.getYearMonth());
            if (query.getStartDate() == null) {
                query.setStartDate(yearMonth.atDay(1));
            }
            if (query.getEndDate() == null) {
                query.setEndDate(yearMonth.atEndOfMonth());
            }
        } catch (DateTimeParseException exception) {
            throw OaErrorCode.exception(OaErrorCode.WORK_CALENDAR_INVALID,
                    "年月格式非法，应为 yyyy-MM");
        }
    }

    /**
     * 判断区间内某天是否按整天计算。
     *
     * @param date 当前日期
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param singleDay 是否同一天
     * @param startPeriod 开始时段
     * @param endPeriod 结束时段
     * @return 整天返回 {@code true}
     */
    private boolean resolveFullDay(LocalDate date, LocalDate startDate, LocalDate endDate,
                                   boolean singleDay, String startPeriod, String endPeriod) {
        if (singleDay) {
            return PERIOD_AM.equals(startPeriod) && PERIOD_PM.equals(endPeriod);
        }
        if (date.equals(startDate)) {
            return PERIOD_AM.equals(startPeriod);
        }
        if (date.equals(endDate)) {
            return PERIOD_PM.equals(endPeriod);
        }
        return true;
    }

    /**
     * 按工作日与整天标记计算系数。
     *
     * @param workday 是否工作日
     * @param fullDay 是否整天
     * @return 工作日系数
     */
    private BigDecimal fraction(boolean workday, boolean fullDay) {
        if (!workday) {
            return BigDecimal.ZERO;
        }
        return fullDay ? FULL_DAY : HALF_DAY;
    }

    /**
     * 默认工作日规则：周一至周五。
     *
     * @param date 日期
     * @return 工作日返回 {@code true}
     */
    private boolean defaultWorkday(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    /**
     * 校验时段取值。
     *
     * @param period 时段
     */
    private void validatePeriod(String period) {
        if (period != null && !PERIOD_AM.equals(period) && !PERIOD_PM.equals(period)) {
            throw OaErrorCode.exception(OaErrorCode.WORK_CALENDAR_INVALID, "时段仅支持 AM / PM");
        }
    }

    /**
     * 领域模型转视图。
     *
     * @param day 日历
     * @return 日历视图
     */
    private WorkCalendarView toView(WorkCalendarDay day) {
        WorkCalendarView view = new WorkCalendarView();
        view.setCalendarDate(day.getCalendarDate());
        view.setWorkday(day.getWorkday());
        view.setWorkHours(day.getWorkHours());
        view.setHolidayName(day.getHolidayName());
        view.setRemark(day.getRemark());
        return view;
    }
}
