package com.bluewhale.sts.log.clientlog;

import java.util.Locale;

/**
 * 客户端日志级别。
 *
 * <p>取值与前端 {@code common-tools} 的 {@code LogLevel} 一一对应 —— 这是**跨端契约的一部分**，
 * 两边改名必须同时改，否则级别筛选会静默失效（查不到数据，但不报错）。
 *
 * <p>落库统一转小写：前端 SDK 用的是小写，如果服务端存大写，检索条件的写法就得靠人记，
 * 迟早出现"同一个级别两种写法各存了一半数据"的局面。
 */
public enum ClientLogLevel {

    TRACE,
    DEBUG,
    PERF,
    INFO,
    SUCCESS,
    WARN,
    ERROR;

    /**
     * 解析级别。
     *
     * <p>返回 {@code null} 表示无法识别 —— 由调用方决定怎么处理（本项目采取
     * "整批不拒绝、逐条计入 rejected"的策略，见 {@link ClientLogService}）。
     * 这里不抛异常，也不兜底成 {@code INFO}：把非法值悄悄变成合法值，
     * 会让上游的拼写错误永远暴露不出来。
     */
    public static ClientLogLevel parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 落库与传输用的写法（小写） */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

}
