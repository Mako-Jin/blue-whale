package com.bluewhale.crm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 售后服务（M4 工单 / SLA）配置。
 *
 * <p>D7 默认口径：P1 2h/8h、P2 4h/24h、P3 8h/72h；7×8 工作时段（09:00–18:00，午休 60 分钟，全周可用）。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.crm.service")
public class CrmServiceProperties {

    /** 售后服务模块开关 */
    private boolean enabled = true;

    /** 默认日历编码 */
    private String defaultCalendarCode = "DEFAULT";

    /** 默认上班时间 */
    private String workdayStart = "09:00";

    /** 默认下班时间 */
    private String workdayEnd = "18:00";

    /** 默认午休分钟数 */
    private int breakMinutes = 60;

    /** 默认工作日（ISO 1–7） */
    private String weekdays = "1,2,3,4,5,6,7";

    /** P1 响应时限（工作小时） */
    private int p1ResponseHours = 2;

    /** P1 解决时限（工作小时） */
    private int p1ResolveHours = 8;

    /** P2 响应时限（工作小时） */
    private int p2ResponseHours = 4;

    /** P2 解决时限（工作小时） */
    private int p2ResolveHours = 24;

    /** P3 响应时限（工作小时） */
    private int p3ResponseHours = 8;

    /** P3 解决时限（工作小时） */
    private int p3ResolveHours = 72;
}
