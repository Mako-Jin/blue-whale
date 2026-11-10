package com.bluewhale.music.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.music.domain.exception.MusicErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 作品上传来源解析单元测试（M5-3）。
 *
 * @author BlueWhale
 */
class CopyrightSourceTest {

    /**
     * 大小写与空白不敏感。
     */
    @Test
    void shouldParseCaseInsensitively() {
        assertEquals(CopyrightSource.ORIGINAL, CopyrightSource.parse(" original "));
        assertEquals(CopyrightSource.AUTHORIZED, CopyrightSource.parse("Authorized"));
        assertEquals(CopyrightSource.ROYALTY_FREE, CopyrightSource.parse("royalty_free"));
    }

    /**
     * 缺失来源返回 40020。
     */
    @Test
    void shouldRejectMissingSource() {
        BizException exception = assertThrows(BizException.class, () -> CopyrightSource.parse("  "));
        assertEquals(MusicErrorCode.COPYRIGHT_SOURCE_REQUIRED, exception.getCode());
        assertEquals(MusicErrorCode.COPYRIGHT_SOURCE_REQUIRED,
                assertThrows(BizException.class, () -> CopyrightSource.parse(null)).getCode());
    }

    /**
     * 非法取值返回 40021。
     */
    @Test
    void shouldRejectUnknownSource() {
        BizException exception = assertThrows(BizException.class, () -> CopyrightSource.parse("BORROWED"));
        assertEquals(MusicErrorCode.COPYRIGHT_SOURCE_INVALID, exception.getCode());
    }
}
