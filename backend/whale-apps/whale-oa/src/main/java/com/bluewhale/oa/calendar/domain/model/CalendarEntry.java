package com.bluewhale.oa.calendar.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 日程聚合根。
 *
 * <p>先按 D10 只支持单次日程（不做 RRULE 重复）；时间区间必须非空且开始早于结束。</p>
 *
 * @author BlueWhale
 */
@Data
public class CalendarEntry {

    /** 主键ID */
    private Long id;

    /** 所有者用户ID */
    private Long ownerId;

    /** 所有者姓名快照 */
    private String ownerName;

    /** 日程标题 */
    private String title;

    /** 日程描述 */
    private String description;

    /** 地点 */
    private String location;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 是否全天：0 否 / 1 是 */
    private Integer allDay;

    /** 可见性 */
    private String visibility;

    /** 提前提醒分钟数 */
    private Integer remindMinutes;

    /** 来源 */
    private String source;

    /** 来源业务ID */
    private Long sourceId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验时间区间。
     */
    public void assertTimeRange() {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw OaErrorCode.exception(OaErrorCode.CALENDAR_INVALID, "日程开始时间必须早于结束时间");
        }
    }

    /**
     * 判断是否对指定用户可见。
     *
     * @param viewerId      查看人用户ID
     * @param sharedUserIds 共享名单用户ID
     * @return 是否可见
     */
    public boolean visibleTo(Long viewerId, Collection<Long> sharedUserIds) {
        if (viewerId == null) {
            return false;
        }
        if (ownerId != null && ownerId.equals(viewerId)) {
            return true;
        }
        if (CalendarVisibility.PUBLIC.name().equals(visibility)) {
            return true;
        }
        return CalendarVisibility.SHARED.name().equals(visibility) && sharedUserIds != null
                && sharedUserIds.contains(viewerId);
    }
}
