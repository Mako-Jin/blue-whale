package com.bluewhale.sts.log.clientlog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 级别解析的边界用例。
 *
 * <p>客户端传上来的字符串是**不可信输入**：拼错、大小写混乱、带了空格都会出现。
 * 这个类的职责是把"不可信"翻译成"确定"，翻译不了就明确返回 null 交给上层计数，
 * 而不是悄悄兜底成某个默认级别。
 */
class ClientLogLevelTest {

    @Test
    @DisplayName("大小写与首尾空格不影响解析")
    void shouldParseCaseAndWhitespaceInsensitively() {
        assertThat(ClientLogLevel.parse("info")).isEqualTo(ClientLogLevel.INFO);
        assertThat(ClientLogLevel.parse("INFO")).isEqualTo(ClientLogLevel.INFO);
        assertThat(ClientLogLevel.parse("  Warn  ")).isEqualTo(ClientLogLevel.WARN);
    }

    @Test
    @DisplayName("无法识别时返回 null，而不是兜底成某个默认级别")
    void shouldReturnNullForUnknownLevel() {
        // 兜底成 INFO 会让"客户端拼错了级别"这件事永远无法被发现，
        // 表现为"日志查得到，但级别筛选器里永远少一根柱子"。
        assertThat(ClientLogLevel.parse("verbose")).isNull();
        assertThat(ClientLogLevel.parse("")).isNull();
        assertThat(ClientLogLevel.parse("   ")).isNull();
        assertThat(ClientLogLevel.parse(null)).isNull();
    }

    @Test
    @DisplayName("落库写法统一为小写")
    void shouldUseLowerCaseWireName() {
        assertThat(ClientLogLevel.SUCCESS.wireName()).isEqualTo("success");
        assertThat(ClientLogLevel.TRACE.wireName()).isEqualTo("trace");
    }

    @Test
    @DisplayName("级别集合与前端 LogLevel 一致，不多不少")
    void levelSetShouldMatchFrontendContract() {
        // 这七个值与 web/packages/common-tools 的 LogLevel 一一对应。
        // 任何一边增删都必须同时改 —— 数量对不上说明契约已经漂移。
        assertThat(ClientLogLevel.values()).hasSize(7);
    }

}
