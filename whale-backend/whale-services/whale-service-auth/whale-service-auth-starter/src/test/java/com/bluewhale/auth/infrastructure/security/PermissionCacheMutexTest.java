package com.bluewhale.auth.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PermissionCacheMutex} 单元测试：透传 loader 返回值、loader 异常原样上抛（AUTH-19 / REQ-DIST-003）。
 *
 * @author BlueWhale
 */
class PermissionCacheMutexTest {

    private final PermissionCacheMutex mutex = new PermissionCacheMutex();

    @Test
    @DisplayName("透传：返回 loader 结果")
    void shouldPassThroughLoaderResult() {
        String result = mutex.load(1L, () -> "v");

        assertThat(result).isEqualTo("v");
    }

    @Test
    @DisplayName("loader 异常：原样上抛")
    void shouldPropagateLoaderException() {
        assertThatThrownBy(() -> mutex.load(1L, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }
}
