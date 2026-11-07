package com.bluewhale.oa.workbench.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalCenterQuery;
import com.bluewhale.oa.approval.application.service.ApprovalCenterAppService;
import com.bluewhale.oa.approval.application.view.ApprovalTaskView;
import com.bluewhale.oa.approval.domain.model.ApprovalActor;
import com.bluewhale.oa.collab.domain.model.CollabQuery;
import com.bluewhale.oa.collab.domain.model.Meeting;
import com.bluewhale.oa.collab.domain.model.MeetingRoom;
import com.bluewhale.oa.collab.domain.model.OaTask;
import com.bluewhale.oa.collab.domain.repository.CollabRepository;
import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.EmployeeProfileQuery;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.oa.workbench.application.service.WorkbenchAppService;
import com.bluewhale.oa.workbench.application.view.WorkbenchViews;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作台应用服务实现。
 *
 * <p>汇总数据按用户缓存 30 秒，缓存命中直接返回不可变视图，避免并发修改问题；
 * 生日窗口为 {@code [today, today+7]}，跨年按 {@link MonthDay} 语义计算。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class WorkbenchAppServiceImpl implements WorkbenchAppService {

    /** 汇总缓存有效期（毫秒） */
    private static final long CACHE_TTL_MILLIS = 30_000L;

    /** 今日会议单次拉取上限 */
    private static final long MEETING_PAGE_SIZE = 200L;

    /** 到期任务默认条数 */
    private static final int DUE_TASK_LIMIT = 10;

    /** 生日档案单页条数 */
    private static final long BIRTHDAY_PAGE_SIZE = 200L;

    /** 生日档案最多扫描页数 */
    private static final int BIRTHDAY_MAX_PAGES = 5;

    /** 生日窗口天数 */
    private static final int BIRTHDAY_WINDOW_DAYS = 7;

    /** 会议状态：已排期 */
    private static final String STATUS_SCHEDULED = "SCHEDULED";

    /** 生日条目上限 */
    private static final int BIRTHDAY_LIMIT = 10;

    private final ApprovalCenterAppService approvalCenterAppService;

    private final CollabRepository collabRepository;

    private final EmployeeProfileRepository employeeProfileRepository;

    private final Map<Long, CachedSummary> cache = new ConcurrentHashMap<>();

    @Override
    public WorkbenchViews.SummaryView summary(Long userId, String username, LocalDate today) {
        if (userId == null) {
            return buildSummary(userId, username, today);
        }
        long now = System.currentTimeMillis();
        CachedSummary cached = cache.get(userId);
        if (cached != null) {
            if (cached.expireAtMillis() > now) {
                return cached.summary();
            }
            cache.remove(userId);
        }
        WorkbenchViews.SummaryView summary = buildSummary(userId, username, today);
        cache.put(userId, new CachedSummary(summary, now + CACHE_TTL_MILLIS));
        return summary;
    }

    /**
     * 组装汇总视图。
     *
     * @param userId 用户ID
     * @param username 用户账号
     * @param today 业务日期
     * @return 汇总视图
     */
    private WorkbenchViews.SummaryView buildSummary(Long userId, String username, LocalDate today) {
        WorkbenchViews.SummaryView summary = new WorkbenchViews.SummaryView();
        summary.setPendingApprovalCount(pendingApprovalCount(userId, username));
        summary.setUnreadNoticeCount(collabRepository.countUnreadNotices(userId));
        summary.setTodayMeetings(todayMeetings(userId, today));
        summary.setDueTasks(dueTasks(userId, today));
        summary.setBirthdays(birthdays(today));
        return summary;
    }

    /**
     * 统计待办审批数。
     *
     * @param userId 用户ID
     * @param username 用户账号
     * @return 待办总数
     */
    private long pendingApprovalCount(Long userId, String username) {
        ApprovalCenterQuery query = new ApprovalCenterQuery();
        query.setCurrent(1L);
        query.setSize(1L);
        PageResult<ApprovalTaskView> page = approvalCenterAppService.pagePending(
                new ApprovalActor(userId, username), query);
        return page == null ? 0L : page.getTotal();
    }

    /**
     * 查询今日会议并映射会议室名称。
     *
     * @param userId 用户ID
     * @param today 业务日期
     * @return 今日会议
     */
    private List<WorkbenchViews.MeetingItem> todayMeetings(Long userId, LocalDate today) {
        CollabQuery query = new CollabQuery();
        query.setParticipantId(userId);
        query.setDateFrom(today);
        query.setDateTo(today);
        query.setStatus(STATUS_SCHEDULED);
        query.setCurrent(1L);
        query.setSize(MEETING_PAGE_SIZE);
        PageResult<Meeting> page = collabRepository.pageMeetings(query);
        if (page == null || page.getRecords().isEmpty()) {
            return List.of();
        }
        Map<Long, String> roomNames = new HashMap<>();
        CollabQuery roomQuery = new CollabQuery();
        List<MeetingRoom> rooms = collabRepository.listRooms(roomQuery);
        rooms.forEach(room -> roomNames.put(room.getId(), room.getName()));
        return page.getRecords().stream().map(meeting -> {
            WorkbenchViews.MeetingItem item = new WorkbenchViews.MeetingItem();
            item.setId(meeting.getId());
            item.setTitle(meeting.getTitle());
            item.setStartTime(meeting.getStartTime());
            item.setEndTime(meeting.getEndTime());
            item.setRoomName(meeting.getRoomId() == null ? null : roomNames.get(meeting.getRoomId()));
            return item;
        }).toList();
    }

    /**
     * 查询到期任务。
     *
     * @param userId 用户ID
     * @param today 业务日期
     * @return 到期任务
     */
    private List<WorkbenchViews.TaskItem> dueTasks(Long userId, LocalDate today) {
        List<OaTask> tasks = collabRepository.listDueTasks(userId, today, DUE_TASK_LIMIT);
        if (tasks == null) {
            return List.of();
        }
        return tasks.stream().map(task -> {
            WorkbenchViews.TaskItem item = new WorkbenchViews.TaskItem();
            item.setId(task.getId());
            item.setTitle(task.getTitle());
            item.setDueDate(task.getDueDate());
            item.setStatus(task.getStatus());
            item.setPriority(task.getPriority());
            return item;
        }).toList();
    }

    /**
     * 查询生日窗口内的员工。
     *
     * @param today 业务日期
     * @return 生日条目（按距今天数升序）
     */
    private List<WorkbenchViews.BirthdayItem> birthdays(LocalDate today) {
        List<EmployeeProfile> profiles = new ArrayList<>();
        for (int pageNo = 1; pageNo <= BIRTHDAY_MAX_PAGES; pageNo++) {
            EmployeeProfileQuery query = new EmployeeProfileQuery();
            query.setCurrent(pageNo);
            query.setSize(BIRTHDAY_PAGE_SIZE);
            List<EmployeeProfile> records = employeeProfileRepository.page(query);
            if (records == null || records.isEmpty()) {
                break;
            }
            profiles.addAll(records);
            if (records.size() < BIRTHDAY_PAGE_SIZE) {
                break;
            }
        }
        return profiles.stream()
                .filter(profile -> profile.getBirthday() != null)
                .map(profile -> toBirthdayItem(profile, today))
                .filter(item -> item.getDaysAhead() >= 0 && item.getDaysAhead() <= BIRTHDAY_WINDOW_DAYS)
                .sorted((left, right) -> Integer.compare(left.getDaysAhead(), right.getDaysAhead()))
                .limit(BIRTHDAY_LIMIT)
                .toList();
    }

    /**
     * 档案转生日条目。
     *
     * @param profile 员工档案
     * @param today 业务日期
     * @return 生日条目
     */
    private static WorkbenchViews.BirthdayItem toBirthdayItem(EmployeeProfile profile, LocalDate today) {
        MonthDay monthDay = MonthDay.from(profile.getBirthday());
        LocalDate next = birthdayInYear(monthDay, today.getYear());
        if (next.isBefore(today)) {
            next = birthdayInYear(monthDay, today.getYear() + 1);
        }
        WorkbenchViews.BirthdayItem item = new WorkbenchViews.BirthdayItem();
        item.setUserId(profile.getUserId());
        item.setEmployeeNo(profile.getEmployeeNo());
        item.setDeptId(profile.getDeptId());
        item.setBirthday(profile.getBirthday());
        item.setDaysAhead((int) ChronoUnit.DAYS.between(today, next));
        return item;
    }

    /**
     * 计算指定年份的生日日期；2 月 29 日在平年回落到 2 月 28 日。
     *
     * @param monthDay 月日
     * @param year 年份
     * @return 生日日期
     */
    private static LocalDate birthdayInYear(MonthDay monthDay, int year) {
        try {
            return monthDay.atYear(year);
        } catch (DateTimeException ex) {
            return LocalDate.of(year, 2, 28);
        }
    }

    /**
     * 汇总缓存条目。
     *
     * @param summary 汇总视图
     * @param expireAtMillis 过期时间戳
     */
    private record CachedSummary(WorkbenchViews.SummaryView summary, long expireAtMillis) {
    }
}
