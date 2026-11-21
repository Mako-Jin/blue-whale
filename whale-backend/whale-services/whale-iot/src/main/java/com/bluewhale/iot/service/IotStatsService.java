package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.iot.dto.IotDayCount;
import com.bluewhale.iot.dto.IotStats;
import com.bluewhale.iot.entity.IotCommand;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotRule;
import com.bluewhale.iot.entity.IotTelemetry;
import com.bluewhale.iot.mapper.IotCommandMapper;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotRuleMapper;
import com.bluewhale.iot.mapper.IotTelemetryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 兵部坊（IoT）统计口径：设备在线、遥测心跳、指令回执、规则启用。
 *
 * <p><b>口径只在这里定义一次。</b>「在线率」是 {@code 启用中且 online = 1 ÷ 启用中}，
 * 不是 {@code 在线 ÷ 全部}——停用设备不在线是正常状态，算进分母会让一座全停用的
 * 机房显示"在线率 0%，全城失联"。同一件事故的两种说法，值班的人只信后一种。</p>
 *
 * <p><b>近 7 日按天计数用 7 次 count，不写 GROUP BY。</b>
 * 遥测表按月分区、{@code (tenant_id, ts)} 有索引，单日 count 是索引区间扫描；
 * 而自定义 SQL 要绕开 MyBatis-Plus 的租户拼装，得自己保证 {@code tenant_id} 条件不漏——
 * 一旦漏了就是跨租户串数，且不会报错。7 次单日 count 慢一点，但口径与租户隔离都由
 * 插件保证，不依赖写 SQL 的人记得加条件。</p>
 *
 * <p>统计只读，不写库：概览会被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class IotStatsService {

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    /** 趋势横轴格式：概览图表横轴是字符串，格式在服务端定死 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    /** 「已发出但还没回执」的指令状态：pending 待下发 / sent 已下发 */
    private static final List<String> PENDING_COMMAND_STATUS = List.of(
            IotCommandService.STATUS_PENDING, IotCommandService.STATUS_SENT);

    /** 在线标记值：{@code iot_device.online} 0 离线 / 1 在线 */
    private static final int ONLINE = 1;

    /** 规则启用标记：{@code iot_rule.enabled} 0 停用 / 1 启用 */
    private static final int RULE_ENABLED = 1;

    private final IotDeviceMapper deviceMapper;

    private final IotTelemetryMapper telemetryMapper;

    private final IotCommandMapper commandMapper;

    private final IotRuleMapper ruleMapper;

    /**
     * 取一次统计快照。
     *
     * @param now 统计基准时刻（可空，缺省取当前时间）；趋势的"今天"由它决定
     * @return 统计快照
     */
    public IotStats snapshot(LocalDateTime now) {
        LocalDateTime at = now == null ? LocalDateTime.now() : now;
        long deviceTotal = count(deviceMapper.selectCount(null));
        long deviceActive = count(deviceMapper.selectCount(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getStatus, IotDeviceService.STATUS_ACTIVE)));
        long deviceOnline = count(deviceMapper.selectCount(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getStatus, IotDeviceService.STATUS_ACTIVE)
                .eq(IotDevice::getOnline, ONLINE)));
        long telemetry24h = count(telemetryMapper.selectCount(new LambdaQueryWrapper<IotTelemetry>()
                .ge(IotTelemetry::getTs, at.minusHours(24))));
        long pendingCommands = count(commandMapper.selectCount(new LambdaQueryWrapper<IotCommand>()
                .in(IotCommand::getStatus, PENDING_COMMAND_STATUS)));
        long rulesTotal = count(ruleMapper.selectCount(null));
        long rulesEnabled = count(ruleMapper.selectCount(new LambdaQueryWrapper<IotRule>()
                .eq(IotRule::getEnabled, RULE_ENABLED)));
        return new IotStats(deviceTotal, deviceActive, deviceOnline, telemetry24h,
                trend(telemetryMapper, at), pendingCommands, rulesTotal, rulesEnabled);
    }

    /**
     * 近 7 日按天的遥测条数（含今天，按日期升序）。
     *
     * <p>区间是左闭右开 {@code [当天 00:00, 次日 00:00)}：写 {@code between} 会把次日零点
     * 那一笔算进前一天，做 7 天趋势时看不出来，一对总数就对不上。</p>
     *
     * @param mapper 遥测 Mapper
     * @param at     基准时刻
     * @return 7 个日计数
     */
    private static List<IotDayCount> trend(IotTelemetryMapper mapper, LocalDateTime at) {
        LocalDate today = at.toLocalDate();
        List<IotDayCount> days = new ArrayList<>(TREND_DAYS);
        for (int offset = TREND_DAYS - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            long dayCount = count(mapper.selectCount(new LambdaQueryWrapper<IotTelemetry>()
                    .ge(IotTelemetry::getTs, day.atStartOfDay())
                    .lt(IotTelemetry::getTs, day.plusDays(1).atStartOfDay())));
            days.add(new IotDayCount(day.format(DAY_LABEL), dayCount));
        }
        return days;
    }

    /**
     * 计数兜底：{@code selectCount} 在极端情况下返回 {@code null}，直接拆箱就是 NPE。
     *
     * @param value 计数值，可空
     * @return 非空计数
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
