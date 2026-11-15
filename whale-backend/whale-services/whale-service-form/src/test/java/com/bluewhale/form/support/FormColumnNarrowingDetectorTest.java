package com.bluewhale.form.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormColumnNarrowingDetector} 列类型收窄识别单元测试。
 *
 * @author BlueWhale
 */
class FormColumnNarrowingDetectorTest {

    /**
     * 应判定为收窄的类型变更（长度/精度/时间粒度变小）
     *
     * @return 现有类型与目标类型参数流
     */
    private static Stream<Arguments> narrowingCases() {
        return Stream.of(
                Arguments.of("varchar(255)", "varchar(100)"),
                Arguments.of("text", "varchar(255)"),
                Arguments.of("decimal(18,4)", "decimal(10,4)"),
                Arguments.of("decimal(18,4)", "decimal(18,2)"),
                Arguments.of("datetime", "date"));
    }

    /**
     * 不应判定为收窄的类型变更（放大或等价）
     *
     * @return 现有类型与目标类型参数流
     */
    private static Stream<Arguments> wideningCases() {
        return Stream.of(
                Arguments.of("varchar(100)", "varchar(255)"),
                Arguments.of("varchar(255)", "text"),
                Arguments.of("decimal(10,4)", "decimal(18,4)"),
                Arguments.of("decimal(18,2)", "decimal(18,4)"),
                Arguments.of("date", "datetime"),
                Arguments.of("varchar(255)", "varchar(255)"));
    }

    @ParameterizedTest(name = "{0} → {1} 判定为收窄")
    @MethodSource("narrowingCases")
    @DisplayName("detect：长度/精度/时间粒度变小均判定为收窄")
    void shouldDetectNarrowing(String currentType, String wantedType) {
        String reason = FormColumnNarrowingDetector.detect(currentType, wantedType);

        assertNotNull(reason, currentType + " → " + wantedType + " 应判定为收窄");
        assertTrue(reason.length() > 0);
    }

    @ParameterizedTest(name = "{0} → {1} 不判定为收窄")
    @MethodSource("wideningCases")
    @DisplayName("detect：放大或等价类型变更不判定为收窄")
    void shouldNotDetectWidening(String currentType, String wantedType) {
        assertNull(FormColumnNarrowingDetector.detect(currentType, wantedType));
    }

    @Test
    @DisplayName("detect：非本模块类型（int/bigint）不判定，交由数据库校验")
    void shouldIgnoreUnknownTypes() {
        assertNull(FormColumnNarrowingDetector.detect("int", "bigint"));
        assertNull(FormColumnNarrowingDetector.detect("bigint", "int"));
    }
}
