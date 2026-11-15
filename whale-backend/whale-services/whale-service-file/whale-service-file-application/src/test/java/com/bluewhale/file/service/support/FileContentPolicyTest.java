package com.bluewhale.file.service.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.file.config.FileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileContentPolicy} 上传内容魔数校验的单元测试。
 *
 * @author BlueWhale
 */
class FileContentPolicyTest {

    private static final int PARAM_ERROR_CODE = 400;

    private FileProperties fileProperties;

    private FileContentPolicy policy;

    /**
     * 每个用例使用默认配置（校验开启、拦截 EXE/ELF/MACHO）
     */
    @BeforeEach
    void setUp() {
        fileProperties = new FileProperties();
        policy = new FileContentPolicy(fileProperties);
    }

    @Test
    @DisplayName("内容识别为可执行文件：拒绝，即使扩展名是 txt")
    void shouldBlockExecutableContent() {
        BizException ex = assertThrows(BizException.class,
                () -> policy.validate("readme.txt", "MZ-fake".getBytes(StandardCharsets.US_ASCII)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("禁止上传可执行文件"));
    }

    @Test
    @DisplayName("扩展名与内容类型不符：拒绝")
    void shouldRejectMismatchedExtension() {
        BizException ex = assertThrows(BizException.class,
                () -> policy.validate("report.pdf", "PK\u0003\u0004data".getBytes(StandardCharsets.ISO_8859_1)));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        assertTrue(ex.getMessage().contains("文件内容与扩展名不匹配"));
    }

    @Test
    @DisplayName("内容类型与扩展名一致：放行")
    void shouldAllowMatchedExtension() {
        assertDoesNotThrow(() -> policy.validate("report.pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("docx/xlsx/pptx 识别为 ZIP 容器：放行")
    void shouldAllowZipBasedOfficeDocument() {
        byte[] zip = "PK\u0003\u0004data".getBytes(StandardCharsets.ISO_8859_1);

        assertDoesNotThrow(() -> policy.validate("report.docx", zip));
        assertDoesNotThrow(() -> policy.validate("report.xlsx", zip));
        assertDoesNotThrow(() -> policy.validate("report.pptx", zip));
    }

    @Test
    @DisplayName("内容无法识别：一律放行，避免误伤自定义格式")
    void shouldAllowUnknownContent() {
        assertDoesNotThrow(() -> policy.validate("report.pdf", "custom-body".getBytes(StandardCharsets.US_ASCII)));
        assertDoesNotThrow(() -> policy.validate("report.pdf", null));
    }

    @Test
    @DisplayName("扩展名无映射或缺失：只做可执行文件拦截，不做一致性判定")
    void shouldSkipCheckWhenExtensionUnmapped() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        assertDoesNotThrow(() -> policy.validate("README", png));
        assertDoesNotThrow(() -> policy.validate("data.bin", png));
        assertDoesNotThrow(() -> policy.validate(null, png));
    }

    @Test
    @DisplayName("扩展名大小写不敏感：PDF 内容 + 大写扩展名放行")
    void shouldIgnoreExtensionCase() {
        assertDoesNotThrow(() -> policy.validate("REPORT.PDF", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("校验开关关闭：即使内容为可执行文件也放行")
    void shouldPassWhenMagicDisabled() {
        fileProperties.getSecurity().setMagicEnabled(false);

        assertDoesNotThrow(() -> policy.validate("readme.txt", "MZ-fake".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @DisplayName("禁止类型可配置：自定义为 ZIP 后普通压缩包按可执行文件口径拒绝")
    void shouldRespectCustomBlockedTypes() {
        fileProperties.getSecurity().setBlockedTypes(List.of("zip"));

        BizException ex = assertThrows(BizException.class,
                () -> policy.validate("report.zip", "PK\u0003\u0004data".getBytes(StandardCharsets.ISO_8859_1)));

        assertTrue(ex.getMessage().contains("禁止上传可执行文件"));
    }
}
