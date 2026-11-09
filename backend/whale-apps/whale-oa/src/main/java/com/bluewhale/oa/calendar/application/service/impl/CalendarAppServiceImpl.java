package com.bluewhale.oa.calendar.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.calendar.application.command.CalendarCommands;
import com.bluewhale.oa.calendar.application.service.CalendarAppService;
import com.bluewhale.oa.calendar.application.view.CalendarViews;
import com.bluewhale.oa.calendar.domain.model.CalendarEntry;
import com.bluewhale.oa.calendar.domain.model.CalendarQuery;
import com.bluewhale.oa.calendar.domain.model.CalendarShare;
import com.bluewhale.oa.calendar.domain.model.CalendarVisibility;
import com.bluewhale.oa.calendar.domain.repository.CalendarRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 日程应用服务实现。
 *
 * <p>可见性规则：所有者始终可见；{@code PUBLIC} 租户内可见；{@code SHARED} 仅共享名单可见；
 * 编辑、删除与共享名单维护仅限所有者。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CalendarAppServiceImpl implements CalendarAppService {

    private static final String VISIBILITY_PRIVATE = "PRIVATE";

    private static final String VISIBILITY_SHARED = "SHARED";

    private static final String VISIBILITY_PUBLIC = "PUBLIC";

    private static final String SOURCE_PERSONAL = "PERSONAL";

    private static final int MAX_REMIND_MINUTES = 10080;

    private static final int MAX_REMINDER_WINDOW = 1440;

    private final CalendarRepository repository;

    @Override
    public PageResult<CalendarViews.EntryView> page(CalendarQuery query) {
        if (query.getUserId() == null) {
            throw OaErrorCode.exception(OaErrorCode.CALENDAR_INVALID, "缺少当前用户信息");
        }
        PageResult<CalendarEntry> page = repository.pageEntries(query);
        List<CalendarViews.EntryView> records = page.getRecords().stream()
                .map(CalendarAppServiceImpl::toView).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public CalendarViews.EntryDetailView detail(Long id, Long viewerId) {
        CalendarEntry entry = repository.findEntry(id);
        if (entry == null) {
            throw OaErrorCode.exception(OaErrorCode.CALENDAR_NOT_FOUND);
        }
        List<CalendarShare> shares = repository.listShares(id);
        boolean owner = entry.getOwnerId() != null && entry.getOwnerId().equals(viewerId);
        if (!owner) {
            Set<Long> sharedUserIds = new LinkedHashSet<>();
            shares.forEach(share -> sharedUserIds.add(share.getUserId()));
            if (!entry.visibleTo(viewerId, sharedUserIds)) {
                throw OaErrorCode.exception(OaErrorCode.CALENDAR_NOT_FOUND);
            }
        }
        CalendarViews.EntryDetailView view = new CalendarViews.EntryDetailView();
        fillView(view, entry);
        view.setEditable(owner);
        view.setShares(owner ? shares.stream().map(CalendarAppServiceImpl::toShareView).toList() : null);
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CalendarViews.EntryView create(CalendarCommands.EntrySaveCommand command) {
        if (command.getOwnerId() == null) {
            throw OaErrorCode.exception(OaErrorCode.CALENDAR_INVALID, "缺少日程所有者信息");
        }
        CalendarEntry entry = new CalendarEntry();
        entry.setOwnerId(command.getOwnerId());
        entry.setOwnerName(command.getOwnerName());
        fillEntry(entry, command);
        entry.setSource(SOURCE_PERSONAL);
        repository.saveEntry(entry);
        return toView(entry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CalendarViews.EntryView update(Long id, CalendarCommands.EntrySaveCommand command, Long operatorId) {
        CalendarEntry entry = requireOwned(id, operatorId);
        fillEntry(entry, command);
        repository.updateEntry(entry);
        return toView(entry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long operatorId) {
        requireOwned(id, operatorId);
        repository.deleteShares(id);
        repository.deleteEntry(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CalendarViews.ShareView> replaceShares(
            Long id, CalendarCommands.ShareSaveCommand command, Long operatorId) {
        CalendarEntry entry = requireOwned(id, operatorId);
        Set<Long> userIds = new LinkedHashSet<>();
        List<CalendarShare> shares = new ArrayList<>();
        if (command.getUsers() != null) {
            for (CalendarCommands.ShareUserCommand user : command.getUsers()) {
                if (user.getUserId() != null && !user.getUserId().equals(entry.getOwnerId())) {
                    userIds.add(user.getUserId());
                }
            }
        }
        repository.deleteShares(id);
        for (Long userId : userIds) {
            CalendarShare share = new CalendarShare();
            share.setCalendarId(id);
            share.setUserId(userId);
            share.setUserName(findUserName(command, userId));
            repository.saveShare(share);
            shares.add(share);
        }
        if (!VISIBILITY_PUBLIC.equals(entry.getVisibility())) {
            entry.setVisibility(userIds.isEmpty() ? VISIBILITY_PRIVATE : VISIBILITY_SHARED);
            repository.updateEntry(entry);
        }
        return shares.stream().map(CalendarAppServiceImpl::toShareView).toList();
    }

    @Override
    public List<CalendarViews.EntryView> reminders(Long ownerId, int minutes) {
        if (minutes <= 0 || minutes > MAX_REMINDER_WINDOW) {
            throw OaErrorCode.exception(OaErrorCode.CALENDAR_INVALID, "提醒窗口须在 1–1440 分钟内");
        }
        LocalDateTime now = LocalDateTime.now();
        return repository.listReminders(ownerId, now, now.plusMinutes(minutes)).stream()
                .map(CalendarAppServiceImpl::toView).toList();
    }

    /**
     * 读取日程并校验操作者为所有者。
     *
     * @param id 日程ID
     * @param operatorId 操作者用户ID
     * @return 日程实体
     */
    private CalendarEntry requireOwned(Long id, Long operatorId) {
        CalendarEntry entry = repository.findEntry(id);
        if (entry == null) {
            throw OaErrorCode.exception(OaErrorCode.CALENDAR_NOT_FOUND);
        }
        if (entry.getOwnerId() == null || !entry.getOwnerId().equals(operatorId)) {
            throw OaErrorCode.exception(OaErrorCode.CALENDAR_FORBIDDEN);
        }
        return entry;
    }

    /**
     * 从共享命令中查找指定用户的姓名。
     *
     * @param command 共享名单保存命令
     * @param userId 用户ID
     * @return 用户姓名，未找到时返回 null
     */
    private static String findUserName(CalendarCommands.ShareSaveCommand command, Long userId) {
        if (command.getUsers() == null) {
            return null;
        }
        return command.getUsers().stream()
                .filter(user -> user.getUserId() != null && user.getUserId().equals(userId))
                .map(CalendarCommands.ShareUserCommand::getUserName)
                .findFirst().orElse(null);
    }

    /**
     * 将保存命令的字段填充到日程实体，并校验时间范围。
     *
     * @param entry 日程实体
     * @param command 日程保存命令
     */
    private static void fillEntry(CalendarEntry entry, CalendarCommands.EntrySaveCommand command) {
        entry.setTitle(command.getTitle().trim());
        entry.setDescription(StringUtils.hasText(command.getDescription())
                ? command.getDescription().trim() : null);
        entry.setLocation(StringUtils.hasText(command.getLocation()) ? command.getLocation().trim() : null);
        entry.setStartTime(command.getStartTime());
        entry.setEndTime(command.getEndTime());
        entry.setAllDay(command.getAllDay() != null && command.getAllDay() == 1 ? 1 : 0);
        entry.setVisibility(CalendarVisibility.of(command.getVisibility()).name());
        Integer remind = command.getRemindMinutes();
        if (remind != null && remind > MAX_REMIND_MINUTES) {
            throw OaErrorCode.exception(OaErrorCode.CALENDAR_INVALID, "提醒分钟数不能超过 7 天");
        }
        entry.setRemindMinutes(remind == null || remind <= 0 ? null : remind);
        entry.assertTimeRange();
    }

    /**
     * 将日程实体转换为日程列表视图。
     *
     * @param entry 日程实体
     * @return 日程列表视图
     */
    private static CalendarViews.EntryView toView(CalendarEntry entry) {
        CalendarViews.EntryView view = new CalendarViews.EntryView();
        fillView(view, entry);
        return view;
    }

    /**
     * 将日程实体的字段填充到日程列表视图。
     *
     * @param view 日程列表视图
     * @param entry 日程实体
     */
    private static void fillView(CalendarViews.EntryView view, CalendarEntry entry) {
        view.setId(entry.getId());
        view.setOwnerId(entry.getOwnerId());
        view.setOwnerName(entry.getOwnerName());
        view.setTitle(entry.getTitle());
        view.setDescription(entry.getDescription());
        view.setLocation(entry.getLocation());
        view.setStartTime(entry.getStartTime());
        view.setEndTime(entry.getEndTime());
        view.setAllDay(entry.getAllDay());
        view.setVisibility(entry.getVisibility());
        view.setRemindMinutes(entry.getRemindMinutes());
        view.setSource(entry.getSource());
        view.setSourceId(entry.getSourceId());
        view.setCreateTime(entry.getCreateTime());
    }

    /**
     * 将共享实体转换为共享用户视图。
     *
     * @param share 共享实体
     * @return 共享用户视图
     */
    private static CalendarViews.ShareView toShareView(CalendarShare share) {
        CalendarViews.ShareView view = new CalendarViews.ShareView();
        view.setUserId(share.getUserId());
        view.setUserName(share.getUserName());
        return view;
    }
}
