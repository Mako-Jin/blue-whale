package com.bluewhale.form.model.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表单对外 VO 的 JSON 契约守护测试。
 *
 * <p>{@link FormDataVO} 与 {@link FormDefinitionVO} 的字段名是与前端冻结的契约，
 * 本测试固化字段集合，任何改名/删字段都会失败，防止无意破坏前端。</p>
 *
 * @author BlueWhale
 */
class FormContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 表单数据契约字段（新增字段可追加，已有字段不得改名/删除） */
    private static final List<String> DATA_FIELDS = List.of(
            "id", "tenantId", "definitionId", "definitionCode", "definitionVersion", "bizKey", "dataJson",
            "fields", "submitter", "status", "bizStatus", "processInstanceId", "createTime", "updateTime");

    /** 表单定义契约字段 */
    private static final List<String> DEFINITION_FIELDS = List.of(
            "id", "code", "name", "tableDefinitionId", "version", "status", "remark", "category", "tags",
            "createTime", "updateTime", "fields");

    @Test
    @DisplayName("FormDataVO：JSON 字段集合与冻结契约一致")
    void shouldKeepDataContract() throws Exception {
        FormDataVO vo = new FormDataVO();
        vo.setFields(Map.of("name", "张三"));
        ObjectNode json = (ObjectNode) MAPPER.readTree(MAPPER.writeValueAsString(vo));

        for (String field : DATA_FIELDS) {
            assertTrue(json.has(field), "FormDataVO 缺少契约字段：" + field);
        }
        assertTrue(json.has("processInstanceId"));
    }

    @Test
    @DisplayName("FormDefinitionVO：JSON 字段集合与冻结契约一致")
    void shouldKeepDefinitionContract() throws Exception {
        FormDefinitionVO vo = new FormDefinitionVO();
        vo.setFields(List.of());
        ObjectNode json = (ObjectNode) MAPPER.readTree(MAPPER.writeValueAsString(vo));

        for (String field : DEFINITION_FIELDS) {
            assertTrue(json.has(field), "FormDefinitionVO 缺少契约字段：" + field);
        }
        assertFalse(json.has("schemaJson"), "schemaJson 为空时应按 NON_NULL 省略");
    }
}
