package com.bluewhale.sts.auth.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录锁定与退避策略测试。
 *
 * <p>退避算法的两个坑都只能在这里测：**封顶**（层级涨上去后是否停住）与
 * **溢出**（层级极大时 {@code Math.pow} 变 {@code Infinity}，若先转 {@code long} 再比较
 * 会得到负数，即"锁定到过去"）。在端到端测试里构造这两种情况极其别扭 ——
 * 得真的让账号被锁十几次，而到那时测试本身已经不可读了。
 */
class LoginLockPolicyTest {

    private static LoginLockPolicy policy(AuthProperties properties) {
        return new LoginLockPolicy(properties);
    }

    @Test
    @DisplayName("第 1 次锁定是基数时长，之后按因子翻倍")
    void shouldGrowExponentially() {
        LoginLockPolicy policy = policy(new AuthProperties()); // 默认 5 分钟 × 2

        assertEquals(Duration.ofMinutes(5), policy.lockDurationOf(1));
        assertEquals(Duration.ofMinutes(10), policy.lockDurationOf(2));
        assertEquals(Duration.ofMinutes(20), policy.lockDurationOf(3));
        assertEquals(Duration.ofMinutes(40), policy.lockDurationOf(4));
    }

    @Test
    @DisplayName("超过上限后停在 24 小时，且不会溢出成负数")
    void shouldCapAndNotOverflow() {
        LoginLockPolicy policy = policy(new AuthProperties()); // 封顶 24h

        // 5 × 2^8 = 1280 分钟，仍在封顶之内
        assertEquals(Duration.ofMinutes(1280), policy.lockDurationOf(9));
        // 5 × 2^9 = 2560 分钟，超过 1440 → 封顶
        assertEquals(Duration.ofHours(24), policy.lockDurationOf(10));

        // 层级极大时 Math.pow 溢出成 Infinity。若实现是"先 (long) 转换再与上限比较"，
        // 这里会得到负数 —— 那个值意味着"锁定到过去"，等于没锁。
        assertEquals(Duration.ofHours(24), policy.lockDurationOf(1000));
        assertTrue(policy.lockDurationOf(Integer.MAX_VALUE).toMillis() > 0);
    }

    @Test
    @DisplayName("增长因子为 1 时退化为固定时长")
    void shouldDegradeToFixedDurationWhenFactorIsOne() {
        AuthProperties properties = new AuthProperties();
        properties.getLogin().setLockGrowthFactor(1.0);
        LoginLockPolicy policy = policy(properties);

        // 这条同时说明"固定时长"不是另一套实现，而是退避的一个特例
        assertEquals(Duration.ofMinutes(5), policy.lockDurationOf(1));
        assertEquals(Duration.ofMinutes(5), policy.lockDurationOf(9));
    }

    @Test
    @DisplayName("level 为 0 或负数按第 1 次处理")
    void shouldTreatNonPositiveLevelAsFirst() {
        LoginLockPolicy policy = policy(new AuthProperties());

        // 该值来自数据库列 lock_level，历史数据里完全可能是 null（调用方兜成 0）
        assertEquals(Duration.ofMinutes(5), policy.lockDurationOf(0));
        assertEquals(Duration.ofMinutes(5), policy.lockDurationOf(-1));
    }

    @Test
    @DisplayName("maxFailedAttempts 从配置读取")
    void shouldExposeMaxFailedAttempts() {
        assertEquals(5, policy(new AuthProperties()).maxFailedAttempts());

        AuthProperties custom = new AuthProperties();
        custom.getLogin().setMaxFailedAttempts(3);
        assertEquals(3, policy(custom).maxFailedAttempts());
    }

}
