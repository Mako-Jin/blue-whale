package com.bluewhale.message.channel;

import com.bluewhale.message.config.MessageChannelProperties;
import com.bluewhale.message.enums.DeliveryStatus;
import com.bluewhale.message.enums.MessageChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChannelGuard} 渠道限流与熔断单元测试。
 *
 * @author BlueWhale
 */
class ChannelGuardTest {

    private final ChannelGuard guard = new ChannelGuard();

    /**
     * 构造渠道保护配置。
     *
     * @param rateLimitPerSecond 每秒令牌数
     * @param failureThreshold   熔断阈值
     * @param openMillis         熔断窗口
     * @return 保护配置
     */
    private static MessageChannelProperties.Guard settings(int rateLimitPerSecond, int failureThreshold,
                                                           long openMillis) {
        MessageChannelProperties.Guard settings = new MessageChannelProperties.Guard();
        settings.setRateLimitPerSecond(rateLimitPerSecond);
        settings.setFailureThreshold(failureThreshold);
        settings.setOpenMillis(openMillis);
        return settings;
    }

    @Test
    @DisplayName("未配置保护参数时直接放行")
    void nullSettingsShouldAllow() {
        assertTrue(guard.acquire(MessageChannel.SMS, null).allowed());
    }

    @Test
    @DisplayName("限流：令牌桶容量为每秒上限，超出立即拒绝")
    void rateLimitShouldRejectExcess() {
        MessageChannelProperties.Guard settings = settings(2, 0, 0);

        assertTrue(guard.acquire(MessageChannel.SMS, settings).allowed());
        assertTrue(guard.acquire(MessageChannel.SMS, settings).allowed());

        ChannelGuard.Decision rejected = guard.acquire(MessageChannel.SMS, settings);
        assertFalse(rejected.allowed());
        assertTrue(rejected.reason().contains("限流"));
    }

    @Test
    @DisplayName("熔断：连续失败达阈值后打开，窗口结束自动半开")
    void circuitShouldOpenAfterThresholdAndRecover() throws InterruptedException {
        MessageChannelProperties.Guard settings = settings(0, 2, 10);

        guard.record(MessageChannel.EMAIL, settings, DeliveryStatus.FAILED);
        assertTrue(guard.acquire(MessageChannel.EMAIL, settings).allowed());
        guard.record(MessageChannel.EMAIL, settings, DeliveryStatus.FAILED);

        ChannelGuard.Decision open = guard.acquire(MessageChannel.EMAIL, settings);
        assertFalse(open.allowed());
        assertTrue(open.reason().contains("熔断"));

        Thread.sleep(20L);
        assertTrue(guard.acquire(MessageChannel.EMAIL, settings).allowed());
    }

    @Test
    @DisplayName("成功调用清零连续失败计数")
    void successShouldResetFailureCount() {
        MessageChannelProperties.Guard settings = settings(0, 2, 1000);

        guard.record(MessageChannel.IM, settings, DeliveryStatus.FAILED);
        guard.record(MessageChannel.IM, settings, DeliveryStatus.SENT);
        guard.record(MessageChannel.IM, settings, DeliveryStatus.FAILED);

        assertTrue(guard.acquire(MessageChannel.IM, settings).allowed());
    }

    @Test
    @DisplayName("MOCKED/SKIPPED 不是渠道故障，不计入熔断")
    void mockedOrSkippedShouldNotTripCircuit() {
        MessageChannelProperties.Guard settings = settings(0, 1, 1000);

        guard.record(MessageChannel.EMAIL, settings, DeliveryStatus.MOCKED);
        guard.record(MessageChannel.EMAIL, settings, DeliveryStatus.SKIPPED);

        assertTrue(guard.acquire(MessageChannel.EMAIL, settings).allowed());
    }
}
