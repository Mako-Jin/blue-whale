package com.bluewhale.message.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投递看板查询条件。
 *
 * <p>时间窗按投递时间（{@code msg_delivery.sent_at}）过滤；不传时默认最近 24 小时，
 * 只传一侧时另一侧按默认窗口补齐，避免全表扫描。</p>
 *
 * @author BlueWhale
 */
@Data
public class MessageDashboardQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 窗口开始时间（含），ISO-8601，如 2026-11-19T00:00:00 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /** 窗口结束时间（含），ISO-8601 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
