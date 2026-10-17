package com.bluewhale.common.audit.support;

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
}
