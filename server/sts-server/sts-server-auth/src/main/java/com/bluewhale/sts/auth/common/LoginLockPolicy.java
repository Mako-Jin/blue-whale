package com.bluewhale.sts.auth.common;

import java.time.Duration;

/**
 * 登录锁定策略 —— 失败多少次锁定、锁多久、退避怎么涨。
 *
 * <p>与 {@link PasswordPolicy} 同一形态：从 {@link AuthProperties} 读取策略参数、
 * 对外暴露纯计算。它把"锁定"这件事的**算术**从 {@code AuthService} 里挪出来，
 * 使得"第 n 次锁定多久""封顶是多少""层级很大时会不会溢出"可以脱离 Spring 与数据库直接断言 ——
 * 这几条恰恰是退避算法最容易出错、而又最难在端到端测试里构造的地方。
 *
 * <p><b>与参照实现 {@code SystemTerminator} 的取舍：</b>
 * 它的退避算法写在 {@code LoginAttemptEntity.calculateLockDuration()} 里，
 * 依赖实体自身的 {@code lockLevel} / {@code lockStrategy} 字段（且注释与代码不一致：
 * 注释写"5/15/30/60/120/240/1440"，代码算的是 {@code 5 × 2^n}）。
 * 本仓把参数全部外置到配置、算法本身做成无状态纯函数 —— 一处定义、一处测试。
 */
public class LoginLockPolicy {

    private final int maxFailedAttempts;
    private final Duration baseDuration;
    private final double growthFactor;
    private final Duration maxDuration;

    public LoginLockPolicy(AuthProperties properties) {
        AuthProperties.Login config = properties.getLogin();
        this.maxFailedAttempts = config.getMaxFailedAttempts();
        this.baseDuration = config.getLockDuration();
        this.growthFactor = config.getLockGrowthFactor();
        this.maxDuration = config.getMaxLockDuration();
    }

    /** 连续失败达到此值即触发一次锁定 */
    public int maxFailedAttempts() {
        return maxFailedAttempts;
    }

    /**
     * 第 {@code level} 次被锁定的时长：{@code 基数 × 增长因子^(level-1)}，上限 {@link #maxDuration}。
     *
     * <p>{@code level} 从 1 起（第 1 次被锁定只锁基数时长）。传入 0 或负数按 1 处理，
     * 不抛异常 —— 它来自数据库列，历史数据里完全可能是 {@code null}（已由调用方兜成 0）。
     *
     * <p><b>比较在 {@code double} 上做完再转 {@code long}：</b>层级很大时 {@code Math.pow}
     * 会溢出成 {@code Infinity}，此时 {@code (long)} 得到 {@code Long.MAX_VALUE}，
     * 再与上限比较虽仍正确，但**若先转再比**、或把上限写成"先加时长再取 min"的形态，
     * 就会溢出成负数 —— 那是"锁定到过去"，等于没锁。
     */
    public Duration lockDurationOf(int level) {
        double millis = baseDuration.toMillis() * Math.pow(growthFactor, Math.max(0, level - 1));
        return millis >= maxDuration.toMillis() ? maxDuration : Duration.ofMillis((long) millis);
    }

}
