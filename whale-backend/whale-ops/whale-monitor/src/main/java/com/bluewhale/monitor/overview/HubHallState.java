package com.bluewhale.monitor.overview;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 总控里一座殿的连通状态（比契约的三态多一个 {@link #DENIED}）。
 *
 * <p>为什么要多这一态：契约里的 {@code degraded} 表示"下游没给数"，
 * 而"调用者没有这座殿的凭据"是另一件事 —— 前者要去查服务，后者要去要权限。
 * 把两者混成一种，值班的人会先去翻服务日志，翻半小时才发现是自己没权限。</p>
 *
 * @author BlueWhale
 */
public enum HubHallState {

    /** 取到了实时快照 */
    LIVE("live"),

    /** 取到了，但下游自己说是上次成功的数据 */
    STALE("stale"),

    /** 没取到：超时 / HTTP 错误 / 返回体不认识 */
    DEGRADED("degraded"),

    /** 调用者缺该殿的权限码，或下游回了 401/403 */
    DENIED("denied");

    /** 契约与前端共用的字面量 */
    private final String value;

    /**
     * 构造
     *
     * @param value 字面量
     */
    HubHallState(String value) {
        this.value = value;
    }

    /**
     * 序列化为小写字面量
     *
     * @return 字面量
     */
    @JsonValue
    public String value() {
        return value;
    }

    /**
     * 反序列化（请求参数、缓存回读时用）
     *
     * @param value 字面量
     * @return 枚举；未知值回落 {@link #DEGRADED}
     */
    @JsonCreator
    public static HubHallState of(String value) {
        for (HubHallState state : values()) {
            if (state.value.equalsIgnoreCase(value)) {
                return state;
            }
        }
        return DEGRADED;
    }

    /**
     * 是否算"拿到了数"（{@code live} 与 {@code stale} 有数，只是新鲜度不同）
     *
     * @return 有数时 {@code true}
     */
    public boolean hasData() {
        return this == LIVE || this == STALE;
    }
}
