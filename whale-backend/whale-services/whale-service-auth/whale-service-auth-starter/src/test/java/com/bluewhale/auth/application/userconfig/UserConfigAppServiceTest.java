package com.bluewhale.auth.application.userconfig;

import com.bluewhale.auth.application.userconfig.command.SaveUserConfigCommand;
import com.bluewhale.auth.application.userconfig.port.UserConfigRepository;
import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserConfigAppService} 单测：入口校验委托与仓储调用。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class UserConfigAppServiceTest {

    @Mock
    private UserConfigRepository repository;

    @InjectMocks
    private UserConfigAppService service;

    /**
     * 合法保存命令透传仓储
     */
    @Test
    @DisplayName("合法保存命令透传仓储 upsert")
    void shouldUpsertValidCommand() {
        service.upsert(1L, 101L, new SaveUserConfigCommand(
                "notify.twin.alert", "{\"channels\":[\"EMAIL\"]}", 101L));

        verify(repository).upsert(1L, 101L, "notify.twin.alert",
                "{\"channels\":[\"EMAIL\"]}", 101L);
    }

    /**
     * 非法键/非法值拒绝且不触仓储
     */
    @Test
    @DisplayName("非法键或非法值拒绝保存且不触仓储")
    void shouldRejectInvalidUpsert() {
        assertThatThrownBy(() -> service.upsert(1L, 101L, new SaveUserConfigCommand(
                "BadKey", "{}", 101L))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.upsert(1L, 101L, new SaveUserConfigCommand(
                "notify.twin.alert", "{broken", 101L))).isInstanceOf(BizException.class);
        verify(repository, never()).upsert(any(), any(), any(), any(), any());
    }

    /**
     * 列表带前缀过滤
     */
    @Test
    @DisplayName("列表按前缀过滤并映射为 VO")
    void shouldListWithPrefix() {
        when(repository.listByUser(1L, 101L, "notify."))
                .thenReturn(List.of(new UserConfigEntry(1L, 101L,
                        "notify.twin.alert", "{}")));

        List<com.bluewhale.auth.application.userconfig.vo.UserConfigVO> items =
                service.list(1L, 101L, "notify.");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).configKey()).isEqualTo("notify.twin.alert");
    }

    /**
     * 删除前校验键格式
     */
    @Test
    @DisplayName("删除非法键抛参数异常且不触仓储")
    void shouldRejectInvalidDelete() {
        assertThatThrownBy(() -> service.delete(1L, 101L, "invalid"))
                .isInstanceOf(BizException.class);
        verify(repository, never()).delete(any(), any(), any());
    }

    /**
     * findValue 透传
     */
    @Test
    @DisplayName("findValue 合法键透传仓储")
    void shouldFindValue() {
        when(repository.findValue(eq(1L), eq(101L), eq("ui.theme")))
                .thenReturn(Optional.of("{}"));

        assertThat(service.findValue(1L, 101L, "ui.theme")).contains("{}");
    }
}
