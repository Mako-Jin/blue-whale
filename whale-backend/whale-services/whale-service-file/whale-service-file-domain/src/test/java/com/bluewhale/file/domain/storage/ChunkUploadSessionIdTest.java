package com.bluewhale.file.domain.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChunkUploadSessionId} 会话标识值对象校验单元测试。
 *
 * @author BlueWhale
 */
class ChunkUploadSessionIdTest {

    @Test
    @DisplayName("of：标准 UUID 归一化为小写")
    void shouldNormalizeUppercaseUuid() {
        String raw = "3F2504E0-4F89-11D3-9A0C-0305E82C3301";

        ChunkUploadSessionId sessionId = ChunkUploadSessionId.of(raw);

        assertEquals(raw.toLowerCase(Locale.ROOT), sessionId.value());
    }

    @Test
    @DisplayName("of：空值或非标准 UUID 抛参数错误")
    void shouldRejectIllegalValue() {
        assertParamError(() -> ChunkUploadSessionId.of(null));
        assertParamError(() -> ChunkUploadSessionId.of(""));
        assertParamError(() -> ChunkUploadSessionId.of("   "));
        assertParamError(() -> ChunkUploadSessionId.of("../../etc/passwd"));
        assertParamError(() -> ChunkUploadSessionId.of("1-1-1-1-1"));
        assertParamError(() -> ChunkUploadSessionId.of("not-a-uuid"));
    }

    @Test
    @DisplayName("random：生成标准 UUID")
    void shouldGenerateUuid() {
        ChunkUploadSessionId sessionId = ChunkUploadSessionId.random();

        assertEquals(UUID.fromString(sessionId.value()).toString(), sessionId.value());
        assertTrue(sessionId.value().matches(
                "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }

    /**
     * 断言构造会话标识抛出参数错误
     *
     * @param action 待执行动作
     */
    private static void assertParamError(Executable action) {
        BizException ex = assertThrows(BizException.class, action);
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }
}
