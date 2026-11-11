package com.bluewhale.auth.application.userconfig;

import com.bluewhale.auth.application.userconfig.port.TenantConfigRepository;
import com.bluewhale.auth.application.userconfig.port.UserConfigRepository;
import com.bluewhale.auth.application.userconfig.vo.ConfigBatchItemVO;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserConfigBatchService} 单测：合并优先级与批量上限。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class UserConfigBatchServiceTest {

    @Mock
    private UserConfigRepository userConfigRepository;

    @Mock
    private TenantConfigRepository tenantConfigRepository;

    @InjectMocks
    private UserConfigBatchService service;

    /**
     * 用户层优先于租户层：命中用户配置即返回 USER 来源，且不查租户层
     */
    @Test
    @DisplayName("用户配置命中时返回 USER 来源且不查租户层")
    void shouldPreferUserOverTenant() {
        when(userConfigRepository.findValue(1L, 101L, "notify.twin.alert"))
                .thenReturn(Optional.of("{\"channels\":[\"EMAIL\"]}"));

        List<ConfigBatchItemVO> items =
                service.batchGet(1L, List.of(101L), List.of("notify.twin.alert"));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).value()).isEqualTo("{\"channels\":[\"EMAIL\"]}");
        assertThat(items.get(0).source()).isEqualTo(ConfigBatchItemVO.SOURCE_USER);
        // 租户默认层在本请求内预查一次（用于未命中用户层时的回退）
        verify(tenantConfigRepository, times(1)).findValues(eq(1L), anyList());
    }

    /**
     * 用户未配置回落租户默认
     */
    @Test
    @DisplayName("用户未配置时回落租户默认，来源 TENANT")
    void shouldFallBackToTenantDefault() {
        when(userConfigRepository.findValue(1L, 102L, "notify.twin.alert"))
                .thenReturn(Optional.empty());
        when(tenantConfigRepository.findValues(eq(1L), anyList()))
                .thenReturn(Map.of("notify.twin.alert", "{\"channels\":[\"INAPP\"]}"));

        List<ConfigBatchItemVO> items =
                service.batchGet(1L, List.of(102L), List.of("notify.twin.alert"));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).value()).isEqualTo("{\"channels\":[\"INAPP\"]}");
        assertThat(items.get(0).source()).isEqualTo(ConfigBatchItemVO.SOURCE_TENANT);
    }

    /**
     * 两层都未配置返回 NONE、value 为 null（调用方按默认放行）
     */
    @Test
    @DisplayName("两层都未配置返回 NONE 且 value 为 null")
    void shouldReturnNoneWhenAbsent() {
        when(userConfigRepository.findValue(anyLong(), anyLong(), eq("ui.theme")))
                .thenReturn(Optional.empty());
        when(tenantConfigRepository.findValues(eq(1L), anyList())).thenReturn(Map.of());

        List<ConfigBatchItemVO> items = service.batchGet(1L, List.of(103L), List.of("ui.theme"));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).value()).isNull();
        assertThat(items.get(0).source()).isEqualTo(ConfigBatchItemVO.SOURCE_NONE);
    }

    /**
     * 多用户 × 多键去重后按笛卡尔展开
     */
    @Test
    @DisplayName("多用户多键去重后按笛卡尔展开")
    void shouldExpandCartesian() {
        when(userConfigRepository.findValue(anyLong(), anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(tenantConfigRepository.findValues(eq(1L), anyList())).thenReturn(Map.of());

        List<ConfigBatchItemVO> items =
                service.batchGet(1L, List.of(1L, 1L, 2L), List.of("k1.b", "k1.b", "k2.d"));

        assertThat(items).hasSize(4); // 去重后 2 用户 × 2 键
        verify(userConfigRepository, times(4)).findValue(anyLong(), anyLong(), anyString());
    }

    /**
     * 超上限抛 400
     */
    @Test
    @DisplayName("用户数或键数超上限抛参数异常")
    void shouldRejectOversizedBatch() {
        List<Long> tooManyUsers = LongStream.rangeClosed(1, 201).boxed().toList();
        assertThatThrownBy(() -> service.batchGet(1L, tooManyUsers, List.of("ui.theme")))
                .isInstanceOf(BizException.class);

        List<String> tooManyKeys = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "a" + i + ".b").toList();
        assertThatThrownBy(() -> service.batchGet(1L, List.of(1L), tooManyKeys))
                .isInstanceOf(BizException.class);
    }

    /**
     * 非法键直接拒绝，不触发任何仓储查询
     */
    @Test
    @DisplayName("键不合法直接 400 且不查仓储")
    void shouldRejectInvalidKeyBeforeQuery() {
        assertThatThrownBy(() -> service.batchGet(1L, List.of(1L), List.of("BadKey")))
                .isInstanceOf(BizException.class);
        verify(userConfigRepository, never()).findValue(anyLong(), anyLong(), anyString());
    }
}
