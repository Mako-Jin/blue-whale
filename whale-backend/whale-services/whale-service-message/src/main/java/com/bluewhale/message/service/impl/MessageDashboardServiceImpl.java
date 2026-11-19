package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.message.dto.MessageDashboardQueryRequest;
import com.bluewhale.message.enums.DeliveryStatus;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageReceiptMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.po.MessageDeliveryPo;
import com.bluewhale.message.po.MessageReceiptPo;
import com.bluewhale.message.po.MessageRecordPo;
import com.bluewhale.message.service.MessageDashboardService;
import com.bluewhale.message.vo.ChannelStatVO;
import com.bluewhale.message.vo.FailureStatVO;
import com.bluewhale.message.vo.MessageDashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 投递看板服务实现：一次聚合返回总览、分渠道成功率、失败 TopN 与未匹配回执数。
 *
 * <p>统计在数据库侧用 {@code GROUP BY} 完成（租户隔离与逻辑删除由 MyBatis-Plus 插件追加），
 * 避免把时间窗内的明细拉回应用内存。成功率口径与 {@code MessageStatusAggregator} 对齐：
 * 成功为 {@code DELIVERED + READ}，失败为 {@code FAILED + BOUNCED}。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MessageDashboardServiceImpl implements MessageDashboardService {

    /** 未指定时间窗时的默认窗口长度（小时） */
    private static final long DEFAULT_WINDOW_HOURS = 24L;

    /** 失败原因 TopN 条数 */
    private static final int TOP_FAILURE_LIMIT = 10;

    /** 成功率保留的小数位数对应的放大倍数 */
    private static final double RATE_SCALE = 10000D;

    /** 计入失败统计的投递状态 */
    private static final List<String> FAILURE_STATUSES =
            List.of(DeliveryStatus.FAILED.name(), DeliveryStatus.BOUNCED.name());

    private final MessageRecordMapper recordMapper;

    private final MessageDeliveryMapper deliveryMapper;

    private final MessageReceiptMapper receiptMapper;

    @Override
    public MessageDashboardVO dashboard(MessageDashboardQueryRequest request) {
        LocalDateTime end = request == null || request.getEndTime() == null
                ? LocalDateTime.now() : request.getEndTime();
        LocalDateTime start = request == null || request.getStartTime() == null
                ? end.minusHours(DEFAULT_WINDOW_HOURS) : request.getStartTime();
        if (!start.isBefore(end)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "看板时间窗非法：开始时间必须早于结束时间");
        }
        MessageDashboardVO dashboard = new MessageDashboardVO();
        dashboard.setStartTime(start);
        dashboard.setEndTime(end);
        dashboard.setTotalMessages(countMessages(start, end));
        dashboard.setUnmatchedReceipts(countUnmatchedReceipts(start, end));
        List<ChannelStatVO> channels = loadChannelStats(start, end);
        dashboard.setChannels(channels);
        long success = channels.stream()
                .mapToLong(stat -> stat.getDelivered() + stat.getRead())
                .sum();
        long failed = channels.stream()
                .mapToLong(stat -> stat.getFailed() + stat.getBounced())
                .sum();
        long total = channels.stream().mapToLong(ChannelStatVO::getTotal).sum();
        dashboard.setTotalDeliveries(total);
        dashboard.setSuccessCount(success);
        dashboard.setFailedCount(failed);
        dashboard.setSuccessRate(rate(success, total));
        dashboard.setTopFailures(loadTopFailures(start, end));
        return dashboard;
    }

    /**
     * 统计窗口内创建的消息条数。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 消息条数
     */
    private long countMessages(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<MessageRecordPo> wrapper = new QueryWrapper<>();
        wrapper.ge("create_time", start).le("create_time", end);
        Long count = recordMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 统计窗口内未匹配到投递记录的回执条数。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 回执条数
     */
    private long countUnmatchedReceipts(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<MessageReceiptPo> wrapper = new QueryWrapper<>();
        wrapper.eq("match_status", "UNMATCHED")
                .ge("create_time", start)
                .le("create_time", end);
        Long count = receiptMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 按渠道与状态分组统计投递。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 分渠道统计，按渠道名升序
     */
    private List<ChannelStatVO> loadChannelStats(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<MessageDeliveryPo> wrapper = new QueryWrapper<>();
        wrapper.select("channel", "status", "COUNT(*) AS cnt")
                .ge("sent_at", start)
                .le("sent_at", end)
                .groupBy("channel", "status");
        Map<String, ChannelStatVO> stats = new TreeMap<>();
        for (Map<String, Object> row : deliveryMapper.selectMaps(wrapper)) {
            String channel = text(row, "channel");
            long count = number(row, "cnt");
            ChannelStatVO stat = stats.computeIfAbsent(channel, MessageDashboardServiceImpl::newStat);
            stat.setTotal(stat.getTotal() + count);
            accumulate(stat, text(row, "status"), count);
        }
        stats.values().forEach(stat ->
                stat.setSuccessRate(rate(stat.getDelivered() + stat.getRead(), stat.getTotal())));
        return new ArrayList<>(stats.values());
    }

    /**
     * 查询窗口内失败原因 TopN。
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 失败原因统计，按条数倒序
     */
    private List<FailureStatVO> loadTopFailures(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<MessageDeliveryPo> wrapper = new QueryWrapper<>();
        wrapper.select("channel", "error_message", "COUNT(*) AS cnt")
                .in("status", FAILURE_STATUSES)
                .ge("sent_at", start)
                .le("sent_at", end)
                .groupBy("channel", "error_message")
                .orderByDesc("cnt")
                .last("LIMIT " + TOP_FAILURE_LIMIT);
        return deliveryMapper.selectMaps(wrapper).stream().map(row -> {
            FailureStatVO stat = new FailureStatVO();
            stat.setChannel(text(row, "channel"));
            stat.setErrorMessage(text(row, "error_message"));
            stat.setCount(number(row, "cnt"));
            return stat;
        }).toList();
    }

    /**
     * 创建渠道统计对象。
     *
     * @param channel 渠道
     * @return 统计对象
     */
    private static ChannelStatVO newStat(String channel) {
        ChannelStatVO stat = new ChannelStatVO();
        stat.setChannel(channel);
        return stat;
    }

    /**
     * 按投递状态累加计数；无法识别的状态忽略。
     *
     * @param stat   渠道统计
     * @param status 状态文本
     * @param count  条数
     */
    private static void accumulate(ChannelStatVO stat, String status, long count) {
        DeliveryStatus parsed = DeliveryStatus.parse(status);
        if (parsed == null) {
            return;
        }
        switch (parsed) {
            case SENT -> stat.setSent(stat.getSent() + count);
            case DELIVERED -> stat.setDelivered(stat.getDelivered() + count);
            case READ -> stat.setRead(stat.getRead() + count);
            case BOUNCED -> stat.setBounced(stat.getBounced() + count);
            case FAILED -> stat.setFailed(stat.getFailed() + count);
            case SKIPPED -> stat.setSkipped(stat.getSkipped() + count);
            case MOCKED -> stat.setMocked(stat.getMocked() + count);
            case EXPIRED -> stat.setExpired(stat.getExpired() + count);
        }
    }

    /**
     * 计算成功率，保留 4 位小数。
     *
     * @param success 成功条数
     * @param total   总条数
     * @return 成功率；总数为 0 时返回 0
     */
    private static double rate(long success, long total) {
        if (total <= 0L) {
            return 0D;
        }
        return Math.round(success * RATE_SCALE / total) / RATE_SCALE;
    }

    /**
     * 读取分组结果中的文本列。
     *
     * @param row 结果行
     * @param key 列名
     * @return 文本值；缺失时为 {@code null}
     */
    private static String text(Map<String, Object> row, String key) {
        Object value = column(row, key);
        return value == null ? null : value.toString();
    }

    /**
     * 读取分组结果中的数值列。
     *
     * @param row 结果行
     * @param key 列名
     * @return 数值；缺失或非数值时为 0
     */
    private static long number(Map<String, Object> row, String key) {
        Object value = column(row, key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 按列名读取分组结果（列名大小写不敏感，兼容不同驱动的返回习惯）。
     *
     * @param row 结果行
     * @param key 列名
     * @return 列值；缺失时为 {@code null}
     */
    private static Object column(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object direct = row.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
