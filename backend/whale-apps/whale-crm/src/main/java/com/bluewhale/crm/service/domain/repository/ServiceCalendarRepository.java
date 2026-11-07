package com.bluewhale.crm.service.domain.repository;

import com.bluewhale.crm.service.domain.model.ServiceCalendar;

/**
 * 工作日历仓储端口。
 *
 * @author BlueWhale
 */
public interface ServiceCalendarRepository {

    /**
     * 查询启用的日历。
     *
     * @param code 日历编码
     * @return 日历；不存在返回 null
     */
    ServiceCalendar findEnabled(String code);

    /**
     * 新增或覆盖日历。
     *
     * @param calendar 日历
     */
    void upsert(ServiceCalendar calendar);
}
