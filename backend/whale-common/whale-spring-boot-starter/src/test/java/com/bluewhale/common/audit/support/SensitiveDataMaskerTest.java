package com.bluewhale.common.audit.support;

import com.bluewhale.common.audit.annotation.Sensitive;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感字段脱敏器单测（覆盖规则表、嵌套结构、深度与截断边界）。
 *
 * @author BlueWhale
 */
class SensitiveDataMaskerTest {

    private static final int MAX_PARAM_LENGTH = 4096;
    private static final int OVER_LIMIT_TEXT_LENGTH = 5000;

    private final SensitiveDataMasker masker = new SensitiveDataMasker(JsonMapper.builder().build());

    @Test
    void shouldMaskSecretAndTokenFieldsWithNameVariants() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("username", "alice");
        params.put("password", "P@ssw0rd!");
        params.put("new_password", "P@ssw0rd-new");
        params.put("oldPassword", "old-secret");
        params.put("token", "jwt-token-value");
        params.put("refreshToken", "refresh-token-value");
        params.put("Authorization", "Bearer jwt-token-value");

        Map<String, Object> masked = asMap(masker.mask(params));

        assertThat(masked)
                .containsEntry("username", "alice")
                .containsEntry("password", "******")
                .containsEntry("new_password", "******")
                .containsEntry("oldPassword", "******")
                .containsEntry("token", "***")
                .containsEntry("refreshToken", "***")
                .containsEntry("Authorization", "***");
        assertThat(masked.values()).doesNotContain("P@ssw0rd!", "jwt-token-value", "refresh-token-value");
    }

    @Test
    void shouldMaskMobileIdCardBankCardAndEmail() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mobile", "13800008888");
        params.put("phone", "13900001111");
        params.put("idCard", "110101199001011234");
        params.put("bankCard", "6222020200112233445");
        params.put("email", "alice@bluewhale.com");

        Map<String, Object> masked = asMap(masker.mask(params));

        assertThat(masked)
                .containsEntry("mobile", "138****8888")
                .containsEntry("phone", "139****1111")
                .containsEntry("idCard", "110101********1234")
                .containsEntry("bankCard", "**** **** **** 3445")
                .containsEntry("email", "a***@bluewhale.com");
    }

    @Test
    void shouldMaskShortValuesCompletely() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mobile", "123");
        params.put("idCard", "1234");
        params.put("bankCard", "12");
        params.put("email", "not-an-email");
        params.put("password", 123456);

        Map<String, Object> masked = asMap(masker.mask(params));

        assertThat(masked)
                .containsEntry("mobile", "******")
                .containsEntry("idCard", "******")
                .containsEntry("bankCard", "******")
                .containsEntry("email", "******")
                .containsEntry("password", "******");
    }

    @Test
    void shouldMaskBeanFieldsAndNestedCollections() {
        MaskSampleRequest request = new MaskSampleRequest(
                1001L, "alice", "P@ssw0rd!",
                List.of(Map.of("phone", "13900001111"), Map.of("phone", "13800002222")));

        Map<String, Object> masked = asMap(masker.mask(request));

        assertThat(masked)
                .containsEntry("userId", 1001L)
                .containsEntry("username", "alice")
                .containsEntry("password", "******");
        List<?> items = (List<?>) masked.get("items");
        assertThat(((Map<?, ?>) items.get(0)).get("phone")).isEqualTo("139****1111");
        assertThat(((Map<?, ?>) items.get(1)).get("phone")).isEqualTo("138****2222");
    }

    @Test
    void shouldNotOverflowOnCyclicReference() {
        CyclicNode node = new CyclicNode();
        node.setSelf(node);

        Object masked = masker.mask(node);

        assertThat(masked).isInstanceOf(Map.class);
    }

    @Test
    void shouldReplaceInfrastructureObjectsWithTypeName() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("file", new java.io.File("audit.log"));

        Map<String, Object> masked = asMap(masker.mask(params));

        assertThat(masked).containsEntry("file", "[File]");
    }

    @Test
    void shouldReturnNullWhenNoArguments() {
        assertThat(masker.maskArguments(null)).isNull();
        assertThat(masker.maskArguments(new Object[0])).isNull();
        assertThat(masker.maskArguments(new Object[]{null})).isNull();
    }

    @Test
    void shouldSerializeArgumentsToJson() {
        String json = masker.maskArguments(new Object[]{new MaskSampleRequest(1L, "alice", "P@ss", List.of())});

        assertThat(json).contains("\"username\":\"alice\"").contains("\"password\":\"******\"");
    }

    @Test
    void shouldTruncateOversizedParams() {
        String json = masker.maskArguments(new Object[]{new BigPayload("x".repeat(OVER_LIMIT_TEXT_LENGTH))});

        assertThat(json).isNotNull().hasSize(MAX_PARAM_LENGTH);
    }

    @Test
    void shouldMaskSensitiveFieldsInValidJsonBody() {
        String json = "{\"username\":\"alice\",\"password\":\"P@ssw0rd!\",\"mobile\":\"13800008888\","
                + "\"nested\":{\"token\":\"jwt-value\"}}";

        String masked = masker.maskJson(json);

        assertThat(masked).contains("\"password\":\"******\"").contains("\"mobile\":\"138****8888\"")
                .contains("\"token\":\"***\"")
                .doesNotContain("P@ssw0rd!").doesNotContain("jwt-value").doesNotContain("13800008888");
    }

    @Test
    void shouldTruncateInvalidJsonWithoutThrowing() {
        String invalidJson = "not-a-json {" + "x".repeat(OVER_LIMIT_TEXT_LENGTH);

        String masked = masker.maskJson(invalidJson);

        assertThat(masked).hasSize(MAX_PARAM_LENGTH);
    }

    @Test
    void shouldReturnNullForBlankJsonBody() {
        assertThat(masker.maskJson(null)).isNull();
        assertThat(masker.maskJson("   ")).isNull();
    }

    @Test
    void shouldMaskSensitiveQueryStringValues() {
        String masked = masker.maskQueryString(
                "token=jwt-value&password=P%40ssw0rd&page=1&mobile=13800008888");

        assertThat(masked).isEqualTo("token=***&password=******&page=1&mobile=138****8888");
    }

    @Test
    void shouldMaskExpandedSecretKeysInQueryString() {
        String masked = masker.maskQueryString(
                "clientSecret=abc&apiKey=xyz&accessKey=ak&name=alice");

        assertThat(masked).isEqualTo("clientSecret=******&apiKey=******&accessKey=******&name=alice");
    }

    @Test
    void shouldMaskQueriesWithEncodedKeysAndDetachedPairs() {
        assertThat(masker.maskQueryString("flag&pass%77ord=secret"))
                .isEqualTo("flag&pass%77ord=******");
    }

    @Test
    void shouldReturnNullForBlankQueryString() {
        assertThat(masker.maskQueryString(null)).isNull();
        assertThat(masker.maskQueryString("   ")).isNull();
    }

    @Test
    void shouldTruncateOversizedQueryString() {
        String query = "note=" + "x".repeat(OVER_LIMIT_TEXT_LENGTH);

        assertThat(masker.maskQueryString(query)).hasSize(MAX_PARAM_LENGTH);
    }

    @Test
    void shouldMaskAnnotatedSensitiveFieldRegardlessOfKeyRule() {
        SensitiveFieldSample sample = new SensitiveFieldSample("top-secret-value", "visible");
        String json = JsonMapper.builder().build().writeValueAsString(sample);

        String masked = masker.maskJson(sample, json);

        assertThat(masked).contains("\"secretNote\":\"******\"").contains("\"note\":\"visible\"")
                .doesNotContain("top-secret-value");
    }

    @Test
    void shouldIgnoreAnnotationWhenOldSignatureUsed() {
        SensitiveFieldSample sample = new SensitiveFieldSample("top-secret-value", "visible");
        String json = JsonMapper.builder().build().writeValueAsString(sample);

        String masked = masker.maskJson(json);

        assertThat(masked).contains("\"secretNote\":\"top-secret-value\"");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    /** 脱敏用样例请求对象（字段名覆盖密码 / 手机号规则） */
    static class MaskSampleRequest {

        private final Long userId;
        private final String username;
        private final String password;
        private final List<Map<String, String>> items;

        MaskSampleRequest(Long userId, String username, String password, List<Map<String, String>> items) {
            this.userId = userId;
            this.username = username;
            this.password = password;
            this.items = items;
        }
    }

    /** 循环引用样例（验证深度上限生效） */
    static class CyclicNode {

        private CyclicNode self;

        void setSelf(CyclicNode self) {
            this.self = self;
        }
    }

    /** 超长参数样例（验证截断） */
    static class BigPayload {

        private final String note;

        BigPayload(String note) {
            this.note = note;
        }
    }

    /** 字段级脱敏样例：{@code secretNote} 标注 {@link Sensitive}，{@code note} 未标注作对照 */
    static class SensitiveFieldSample {

        @Sensitive
        private final String secretNote;

        private final String note;

        SensitiveFieldSample(String secretNote, String note) {
            this.secretNote = secretNote;
            this.note = note;
        }

        public String getSecretNote() {
            return secretNote;
        }

        public String getNote() {
            return note;
        }
    }
}
