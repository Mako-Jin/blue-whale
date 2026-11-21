package com.bluewhale.message.service.impl;

import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.po.stat.MessageChannelFailPo;
import com.bluewhale.message.po.stat.MessageDayDeliveryPo;
import com.bluewhale.message.po.stat.MessageDeliveryStatPo;
import com.bluewhale.message.service.MessageOverviewStatQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link MessageOverviewStatQuery} 的 MyBatis 实现。
 *
 * <p>所有查询显式带 {@code tenant_id}，时间一律写成 {@code column >= ? AND column < ?}，
 * 函数不套在条件列上——{@code DATE(sent_at) = ?} 会让索引直接失效（按天聚合那条仅用
 * {@code DATE()} 作分组键，条件仍是范围比较）。</p>
 *
 * <p>空结果折算成 0 而不是 null：概览是给人看的，"今日投递 null 条"比 0 更像故障。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MessageOverviewStatQueryImpl implements MessageOverviewStatQuery {

    /** 渠道缺失时的兜底展示名（历史数据允许为空） */
    private static final String UNKNOWN_CHANNEL = "未知渠道";

    private final MessageDeliveryMapper deliveryMapper;

    private final MessageRecordMapper recordMapper;

    @Override
    public long sentCount(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return 0L;
        }
        return deliveryMapper.countSentBetween(tenantId, from, to);
    }

    @Override
    public DeliveryStat deliveryStat(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return new DeliveryStat(0L, 0L, 0L, 0L);
        }
        MessageDeliveryStatPo row = deliveryMapper.sumDeliveryBetween(tenantId, from, to);
        if (row == null) {
            return new DeliveryStat(0L, 0L, 0L, 0L);
        }
        return new DeliveryStat(valueOf(row.getTotal()), valueOf(row.getSuccess()),
                valueOf(row.getFailed()), valueOf(row.getBounced()));
    }

    @Override
    public long backlog(Long tenantId, LocalDateTime deadline) {
        if (tenantId == null || deadline == null) {
            return 0L;
        }
        return recordMapper.countBacklog(tenantId, deadline);
    }

    @Override
    public List<DayDelivery> dailyDeliveries(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return List.of();
        }
        List<MessageDayDeliveryPo> rows = deliveryMapper.countSentByDay(tenantId, from, to);
        List<DayDelivery> result = new ArrayList<>(rows.size());
        for (MessageDayDeliveryPo row : rows) {
            if (row.getDay() == null) {
                continue;
            }
            result.add(new DayDelivery(row.getDay(), valueOf(row.getDeliveries())));
        }
        return List.copyOf(result);
    }

    @Override
    public List<ChannelFail> failedChannels(Long tenantId, LocalDateTime from, LocalDateTime to,
                                            int limit) {
        if (invalidWindow(tenantId, from, to) || limit <= 0) {
            return List.of();
        }
        List<MessageChannelFailPo> rows = deliveryMapper.topFailedChannels(tenantId, from, to, limit);
        List<ChannelFail> result = new ArrayList<>(rows.size());
        for (MessageChannelFailPo row : rows) {
            String channel = row.getChannel();
            result.add(new ChannelFail(channel == null || channel.isBlank() ? UNKNOWN_CHANNEL : channel,
                    valueOf(row.getFailures())));
        }
        return List.copyOf(result);
    }

    /**
     * 时间窗参数是否不可用
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻
     * @param to       截止时刻
     * @return 任一缺失或窗口为空时返回 true
     */
    private boolean invalidWindow(Long tenantId, LocalDateTime from, LocalDateTime to) {
        return tenantId == null || from == null || to == null || !from.isBefore(to);
    }

    /**
     * Long 拆箱兜底
     *
     * @param value 可能为 null 的计数
     * @return 非空计数
     */
    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }
}
