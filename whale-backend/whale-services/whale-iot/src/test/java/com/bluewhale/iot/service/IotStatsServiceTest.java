package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link IotStatsService} 单元测试：在线率分母口径、近 7 日趋势的日期窗口与个数、
 * 待回指令合并 pending/sent。
 *
 * <p>这里不验 SQL 长什么样——SQL 由 MyBatis-Plus 生成、租户条件由插件追加，
 * 断言它的字符串形态只会把测试绑死在框架实现上。真正会漂的是**口径**
 * （分母算谁、趋势含几天、哪两个状态算欠账），所以只钉口径。</p>
 *
 * @author BlueWhale
 */
class IotStatsServiceTest {

    /**
     * 初始化 MyBatis-Plus 表元数据。
     *
     * <p>{@code in(...)} 需要**立刻**把 lambda 解析成列名（不像 {@code eq} 是渲染期才解析），
     * 而单测里没有 MyBatis 扫描 Mapper 的过程，lambda 缓存是空的。不初始化就会抛
     * "can not find lambda cache for this entity"，且只有带 {@code in} 的那条路径会炸。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, IotDevice.class);
        TableInfoHelper.initTableInfo(assistant, IotTelemetry.class);
        TableInfoHelper.initTableInfo(assistant, IotCommand.class);
        TableInfoHelper.initTableInfo(assistant, IotRule.class);
    }

    private final IotDeviceMapper deviceMapper = mock(IotDeviceMapper.class);

    private final IotTelemetryMapper telemetryMapper = mock(IotTelemetryMapper.class);

    private final IotCommandMapper commandMapper = mock(IotCommandMapper.class);

    private final IotRuleMapper ruleMapper = mock(IotRuleMapper.class);

    private final IotStatsService service =
            new IotStatsService(deviceMapper, telemetryMapper, commandMapper, ruleMapper);

    /**
     * 让四个 Mapper 各按调用顺序返回计数。
     *
     * @param devices    设备 Mapper 依次返回：总数、启用、在线
     * @param telemetry  遥测 Mapper 的固定返回值（24h + 7 天共 8 次调用）
     * @param commands   指令 Mapper 返回值
     * @param rules      规则 Mapper 依次返回：总数、启用
     */
    private void stub(long[] devices, long telemetry, long commands, long[] rules) {
        when(deviceMapper.selectCount(any()))
                .thenReturn(devices[0], devices[1], devices[2]);
        when(telemetryMapper.selectCount(any())).thenReturn(telemetry);
        when(commandMapper.selectCount(any())).thenReturn(commands);
        when(ruleMapper.selectCount(any())).thenReturn(rules[0], rules[1]);
    }

    @Test
    @DisplayName("在线率：分母是启用设备（10 启用 8 在线 = 0.8），离线按启用减在线")
    void shouldUseActiveDevicesAsRateDenominator() {
        stub(new long[] {12L, 10L, 8L}, 0L, 0L, new long[] {0L, 0L});

        IotStats stats = service.snapshot(LocalDateTime.of(2026, 11, 21, 22, 0));

        assertThat(stats.deviceTotal()).isEqualTo(12L);
        assertThat(stats.deviceActive()).isEqualTo(10L);
        assertThat(stats.deviceOnline()).isEqualTo(8L);
        assertThat(stats.offlineDevices()).isEqualTo(2L);
        assertThat(stats.onlineRate()).isEqualTo(0.8d);
    }

    @Test
    @DisplayName("在线数大于启用数（脏数据）：离线数不能算成负的")
    void shouldClampOfflineAtZero() {
        stub(new long[] {3L, 2L, 5L}, 0L, 0L, new long[] {0L, 0L});

        assertThat(service.snapshot(LocalDateTime.of(2026, 11, 21, 22, 0)).offlineDevices()).isZero();
    }

    @Test
    @DisplayName("近 7 日趋势：7 根柱、末日是基准当天、按日期升序")
    void shouldBuildSevenDayTrendEndingToday() {
        stub(new long[] {0L, 0L, 0L}, 3L, 0L, new long[] {0L, 0L});

        List<IotDayCount> days = service.snapshot(LocalDateTime.of(2026, 11, 21, 22, 0)).telemetryDays();

        assertThat(days).hasSize(7);
        assertThat(days).extracting(IotDayCount::label)
                .containsExactly("11-15", "11-16", "11-17", "11-18", "11-19", "11-20", "11-21");
        assertThat(days).extracting(IotDayCount::count).containsOnly(3L);
    }

    @Test
    @DisplayName("待回指令与规则启用：原样透传，不在统计层做二次判定")
    void shouldPassThroughCommandAndRuleCounts() {
        stub(new long[] {5L, 5L, 5L}, 0L, 4L, new long[] {6L, 2L});

        IotStats stats = service.snapshot(LocalDateTime.of(2026, 11, 21, 22, 0));

        assertThat(stats.pendingCommands()).isEqualTo(4L);
        assertThat(stats.rulesTotal()).isEqualTo(6L);
        assertThat(stats.rulesEnabled()).isEqualTo(2L);
    }

    @Test
    @DisplayName("计数返回 null（无数据/驱动异常）：一律当 0，不能拆箱 NPE")
    void shouldTreatNullCountAsZero() {
        when(deviceMapper.selectCount(any())).thenReturn(null);
        when(telemetryMapper.selectCount(any())).thenReturn(null);
        when(commandMapper.selectCount(any())).thenReturn(null);
        when(ruleMapper.selectCount(any())).thenReturn(null);

        IotStats stats = service.snapshot(LocalDateTime.of(2026, 11, 21, 22, 0));

        assertThat(stats.deviceTotal()).isZero();
        assertThat(stats.telemetry24h()).isZero();
        assertThat(stats.telemetryDays()).extracting(IotDayCount::count).containsOnly(0L);
        assertThat(stats.pendingCommands()).isZero();
    }
}
