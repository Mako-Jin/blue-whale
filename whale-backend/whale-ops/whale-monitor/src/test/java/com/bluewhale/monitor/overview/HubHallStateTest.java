package com.bluewhale.monitor.overview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HubHallState} 状态字面量测试：正向解析、未知值回落、有数判定。
 *
 * @author BlueWhale
 */
class HubHallStateTest {

    @Test
    @DisplayName("序列化：四态各自输出契约约定的小写字面量")
    void shouldSerializeToLowercaseLiteral() {
        assertThat(HubHallState.LIVE.value()).isEqualTo("live");
        assertThat(HubHallState.STALE.value()).isEqualTo("stale");
        assertThat(HubHallState.DEGRADED.value()).isEqualTo("degraded");
        assertThat(HubHallState.DENIED.value()).isEqualTo("denied");
    }

    @Test
    @DisplayName("反序列化：四态字面量（含大小写混写）都能还原")
    void shouldParseKnownLiterals() {
        assertThat(HubHallState.of("live")).isEqualTo(HubHallState.LIVE);
        assertThat(HubHallState.of("Stale")).isEqualTo(HubHallState.STALE);
        assertThat(HubHallState.of("degraded")).isEqualTo(HubHallState.DEGRADED);
        assertThat(HubHallState.of("DENIED")).isEqualTo(HubHallState.DENIED);
    }

    @Test
    @DisplayName("反序列化：未知值与 null 一律回落 degraded，不让坏值冒泡成空页")
    void shouldFallbackToDegradedForUnknownLiteral() {
        assertThat(HubHallState.of("bogus")).isEqualTo(HubHallState.DEGRADED);
        assertThat(HubHallState.of(null)).isEqualTo(HubHallState.DEGRADED);
        assertThat(HubHallState.of("")).isEqualTo(HubHallState.DEGRADED);
    }

    @Test
    @DisplayName("hasData：只有 live / stale 算拿到了数，degraded / denied 不算")
    void shouldTellHasData() {
        assertThat(HubHallState.LIVE.hasData()).isTrue();
        assertThat(HubHallState.STALE.hasData()).isTrue();
        assertThat(HubHallState.DEGRADED.hasData()).isFalse();
        assertThat(HubHallState.DENIED.hasData()).isFalse();
    }
}
