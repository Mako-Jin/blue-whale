package com.bluewhale.form.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormDataExportSupport} CSV 导出安全单元测试。
 *
 * @author BlueWhale
 */
class FormDataExportSupportTest {

    @Test
    @DisplayName("csv：以公式字符开头的单元格前置单引号，数值保持原样")
    void shouldNeutralizeFormulaInCsv() {
        byte[] content = FormDataExportSupport.csv(List.of("字段一", "字段二"),
                List.of(
                        List.of("=1+1", "normal"),
                        List.of("@SUM(A1:A2)", "-3.14"),
                        List.of("-abc", "+100")));
        String text = new String(content, StandardCharsets.UTF_8);

        assertTrue(text.contains("'=1+1"));
        assertTrue(text.contains("'@SUM(A1:A2)"));
        assertTrue(text.contains("'-abc"));
        assertFalse(text.contains("'normal"));
        assertFalse(text.contains("'-3.14"));
        assertFalse(text.contains("'+100"));
    }
}
