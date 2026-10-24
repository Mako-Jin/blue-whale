package com.bluewhale.agent.support.dataquery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只读数据脱敏单测：敏感字段、手机号、身份证与普通值。
 *
 * @author BlueWhale
 */
class DataMaskerTest {

    @Test
    @DisplayName("敏感字段：整体遮蔽")
    void shouldMaskSensitiveColumn() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_phone", "13812345678");
        row.put("api_token", "abc");
        row.put("fact_count", 12);

        Map<String, Object> masked = DataMasker.maskRow(row);

        assertThat(masked.get("user_phone")).isEqualTo("***");
        assertThat(masked.get("api_token")).isEqualTo("***");
        assertThat(masked.get("fact_count")).isEqualTo(12);
        assertThat(row.get("user_phone")).isEqualTo("13812345678");
    }

    @Test
    @DisplayName("普通文本中的手机号与身份证：按位置掩码")
    void shouldMaskPhoneAndIdCardInText() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("remark", "联系 13812345678");
        row.put("cert", "110101199001011234");

        Map<String, Object> masked = DataMasker.maskRow(row);

        assertThat(masked.get("remark")).isEqualTo("联系 138****5678");
        assertThat(masked.get("cert")).isEqualTo("110101********1234");
    }

    @Test
    @DisplayName("普通字段与空值：保持不变")
    void shouldKeepNormalValues() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fact_type", "SERVICE");
        row.put("last_captured_at", null);

        Map<String, Object> masked = DataMasker.maskRow(row);

        assertThat(masked.get("fact_type")).isEqualTo("SERVICE");
        assertThat(masked.get("last_captured_at")).isNull();
    }

    @Test
    @DisplayName("多行脱敏：返回新列表")
    void shouldMaskRows() {
        List<Map<String, Object>> rows = List.of(Map.of("phone", "13812345678"));

        List<Map<String, Object>> masked = DataMasker.maskRows(rows);

        assertThat(masked).hasSize(1);
        assertThat(masked.get(0).get("phone")).isEqualTo("***");
    }
}
