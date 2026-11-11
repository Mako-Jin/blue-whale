package com.bluewhale.message.support;

import com.bluewhale.message.config.MessagePreferenceProperties;
import com.bluewhale.message.dto.PreferenceResolveRequest;
import com.bluewhale.message.vo.PreferenceDecisionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link NotifyPreferenceResolver} 单测：渠道过滤 / 静默窗口 / 阈值 / fail-open。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class NotifyPreferenceResolverTest {

    @Mock
    private AuthUserConfigClient authUserConfigClient;

    private NotifyPreferenceResolver resolver;

    /**
     * 每例前构造真实 ObjectMapper（与 MessageJacksonConfig 口径一致）
     */
    @BeforeEach
    void setUp() {
        MessagePreferenceProperties properties = new MessagePreferenceProperties();
        properties.setEnabled(true);
        resolver = new NotifyPreferenceResolver(properties, authUserConfigClient, new ObjectMapper());
    }

    /**
     * 无偏好：全部按调用方默认放行
     */
    @Test
    @DisplayName("无偏好按默认放行，来源 NONE")
    void shouldDefaultWhenNoPreference() {
        when(authUserConfigClient.batchGet(anyLong(), anyString(), anyList())).thenReturn(Map.of());

        List<PreferenceDecisionVO> items = resolver.resolve(request(1L, "INFO", null));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).channels()).containsExactly("EMAIL", "INAPP");
        assertThat(items.get(0).quiet()).isFalse();
        assertThat(items.get(0).suppressed()).isFalse();
        assertThat(items.get(0).source()).isEqualTo(PreferenceDecisionVO.SOURCE_NONE);
    }

    /**
     * 渠道交集：偏好只能收紧
     */
    @Test
    @DisplayName("偏好渠道集与默认集取交集（只能收紧）")
    void shouldIntersectChannels() {
        Map<Long, String> prefs = Map.of(101L,
                "{\"channels\":[\"EMAIL\",\"SMS\",\"UNKNOWN\"]}");
        when(authUserConfigClient.batchGet(anyLong(), anyString(), anyList())).thenReturn(prefs);

        List<PreferenceDecisionVO> items = resolver.resolve(request(1L, "INFO", null));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).channels()).containsExactly("EMAIL");
        assertThat(items.get(0).source()).isEqualTo(PreferenceDecisionVO.SOURCE_USER);
    }

    /**
     * 阈值：低于阈值的非站内信渠道被过滤，站内信保留
     */
    @Test
    @DisplayName("minLevel=ERROR 时 INFO 级别只投站内信，suppressed=true")
    void shouldFilterBelowMinLevelButKeepInapp() {
        Map<Long, String> prefs = Map.of(101L, "{\"minLevel\":\"ERROR\"}");
        when(authUserConfigClient.batchGet(anyLong(), anyString(), anyList())).thenReturn(prefs);

        List<PreferenceDecisionVO> items = resolver.resolve(request(1L, "INFO", null));

        assertThat(items.get(0).channels()).containsExactly("INAPP");
        assertThat(items.get(0).suppressed()).isTrue();
    }

    /**
     * 达到阈值时非站内信渠道保留
     */
    @Test
    @DisplayName("级别达到 minLevel 时 EMAIL 保留")
    void shouldKeepChannelsAboveMinLevel() {
        Map<Long, String> prefs = Map.of(101L, "{\"minLevel\":\"ERROR\"}");
        when(authUserConfigClient.batchGet(anyLong(), anyString(), anyList())).thenReturn(prefs);

        List<PreferenceDecisionVO> items = resolver.resolve(request(1L, "ERROR", null));

        assertThat(items.get(0).channels()).containsExactly("EMAIL", "INAPP");
        assertThat(items.get(0).suppressed()).isFalse();
    }

    /**
     * 偏好值非法 JSON：fail-open 按默认放行
     */
    @Test
    @DisplayName("偏好值非法 JSON 时 fail-open")
    void shouldFailOpenOnBrokenJson() {
        Map<Long, String> prefs = Map.of(101L, "{broken-json");
        when(authUserConfigClient.batchGet(anyLong(), anyString(), anyList())).thenReturn(prefs);

        List<PreferenceDecisionVO> items = resolver.resolve(request(1L, "INFO", null));

        assertThat(items.get(0).channels()).containsExactly("EMAIL", "INAPP");
        assertThat(items.get(0).source()).isEqualTo(PreferenceDecisionVO.SOURCE_NONE);
    }

    /**
     * 静默窗口（当前时间恰好落在窗口内构造）：非法/相等窗口 fail-open
     */
    @Test
    @DisplayName("start==end 或非法窗口 fail-open 不静默")
    void shouldFailOpenOnInvalidQuietWindow() {
        Map<Long, String> prefs = new HashMap<>();
        prefs.put(101L, "{\"quietStart\":\"25:99\",\"quietEnd\":\"08:00\"}");
        prefs.put(102L, "{\"quietStart\":\"08:00\",\"quietEnd\":\"08:00\"}");
        when(authUserConfigClient.batchGet(anyLong(), anyString(), anyList())).thenReturn(prefs);

        PreferenceResolveRequest request = new PreferenceResolveRequest(
                1L, "twin.alert", List.of(101L, 102L), "INFO",
                new PreferenceResolveRequest.Defaults(List.of("EMAIL", "INAPP"), null), null);
        List<PreferenceDecisionVO> items = resolver.resolve(request);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).quiet()).isFalse();
        assertThat(items.get(1).quiet()).isFalse();
        assertThat(items.get(0).channels()).containsExactly("EMAIL", "INAPP");
    }

    /**
     * 静默窗口跨零点命中：当前时间在窗口内时 EMAIL 被静默、站内信保留
     */
    @Test
    @DisplayName("跨零点窗口命中时 EMAIL 静默、INAPP 保留")
    void shouldSuppressEmailInQuietWindow() {
        Map<Long, String> prefs = Map.of(101L, quietWindowAroundNow(1));
        when(authUserConfigClient.batchGet(anyLong(), anyString(), anyList())).thenReturn(prefs);

        List<PreferenceDecisionVO> items = resolver.resolve(request(1L, "INFO", null));

        assertThat(items.get(0).quiet()).isTrue();
        assertThat(items.get(0).channels()).containsExactly("INAPP");
        assertThat(items.get(0).suppressed()).isTrue();
    }

    /**
     * 豁免级别命中时静默不生效
     */
    @Test
    @DisplayName("quietBypassLevels 命中时窗口内仍投 EMAIL")
    void shouldBypassQuietForCritical() {
        Map<Long, String> prefs = Map.of(101L, quietWindowAroundNow(1));
        when(authUserConfigClient.batchGet(anyLong(), anyString(), anyList())).thenReturn(prefs);

        PreferenceResolveRequest request = new PreferenceResolveRequest(
                1L, "twin.alert", List.of(101L), "CRITICAL",
                new PreferenceResolveRequest.Defaults(List.of("EMAIL", "INAPP"), null),
                List.of("CRITICAL"));

        List<PreferenceDecisionVO> items = resolver.resolve(request);

        assertThat(items.get(0).quiet()).isFalse();
        assertThat(items.get(0).channels()).containsExactly("EMAIL", "INAPP");
    }

    /**
     * 构造包含当前时刻的静默窗口（now-1h 到 now+1h，自动处理跨零点）
     *
     * @param hours 窗口半径（小时）
     * @return 偏好值 JSON
     */
    private static String quietWindowAroundNow(int hours) {
        LocalTime from = LocalTime.now().minusHours(hours);
        LocalTime to = LocalTime.now().plusHours(hours);
        return "{\"quietStart\":\"" + from.format(DateTimeFormatter.ofPattern("HH:mm"))
                + "\",\"quietEnd\":\"" + to.format(DateTimeFormatter.ofPattern("HH:mm")) + "\"}";
    }

    /**
     * 构造基础解析请求
     *
     * @param tenantId 租户 ID
     * @param level    消息级别
     * @param prefs    偏好映射（供 mock 复用，此处只用于占位）
     * @return 请求对象
     */
    private static PreferenceResolveRequest request(Long tenantId, String level,
                                                    Map<Long, String> prefs) {
        return new PreferenceResolveRequest(tenantId, "twin.alert", List.of(101L), level,
                new PreferenceResolveRequest.Defaults(List.of("EMAIL", "INAPP"), null), null);
    }
}
