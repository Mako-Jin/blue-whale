package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.form.po.FormColumnMetaPo;
import com.bluewhale.form.po.FormFieldConfigPo;
import com.bluewhale.form.po.FormFieldOptionPo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表单元数据 → {@code schema_json} 发布快照编译器。
 *
 * <p>把表单视图层的字段配置（{@link FormFieldConfigPo}，含选项 {@link FormFieldOptionPo}）与表定义层的
 * 列元数据（{@link FormColumnMetaPo}）编译为 Wave 5.27 冻结的 {@code schema_json} 结构
 * （{@code {"fields":[{"key","label","type","widget","required","maxLength","options"}]}}）。
 * 该快照是发布产物（供动态引擎与前端渲染读取），元数据表才是唯一真源。</p>
 *
 * <p>纯布局字段（{@code columnMetaId == null}，如标题/分割线/说明）不参与数据列，编译时跳过。</p>
 *
 * @author BlueWhale
 */
public final class FormMetadataCompiler {

    /** JSON 解析器（仅写出，线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 工具类不允许实例化 */
    private FormMetadataCompiler() {
    }

    /**
     * 编译表单视图为 {@code schema_json} 快照
     *
     * @param configs 字段配置列表（调用方需按 {@code sortNo} 升序排列）
     * @param columns 列元数据映射（key 为列元数据 ID）
     * @param options 选项映射（key 为字段配置 ID）
     * @return {@code schema_json} 文本
     * @throws BizException 无数据字段、关联列缺失或重复引用列时抛出 {@code PARAM_ERROR}
     */
    public static String compile(List<FormFieldConfigPo> configs, Map<Long, FormColumnMetaPo> columns,
                                 Map<Long, List<FormFieldOptionPo>> options) {
        Set<String> keys = collectKeys(configs, columns);
        ArrayNode fields = MAPPER.createArrayNode();
        for (FormFieldConfigPo config : configs) {
            if (config.getColumnMetaId() == null) {
                continue;
            }
            FormColumnMetaPo column = columns.get(config.getColumnMetaId());
            fields.add(toFieldNode(config, column, options.get(config.getId()), keys));
        }
        if (fields.isEmpty()) {
            throw paramError("表单视图未配置任何数据字段");
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.set(FormSchemaParser.KEY_FIELDS, fields);
        return root.toString();
    }

    /**
     * 编译表定义层的「表级 schema」快照：按列元数据生成全部物理列字段，供发布时写入
     * {@code form_table_registry.schema_json}，作为该表版本读旧数据的结构来源。
     *
     * <p>与视图编译的区别：没有字段配置，故没有控件/选项/联动等视图级扩展；附件与子表仅保留
     * 存储类型（附件默认多文件、子表无列约束），用于解析历史数据时确定 key 与基础类型。</p>
     *
     * @param columns 该表版本下的列元数据（调用方需按 {@code sortNo} 升序排列）
     * @return {@code schema_json} 文本
     * @throws BizException 无可用列时抛出 {@code PARAM_ERROR}
     */
    public static String compileTable(List<FormColumnMetaPo> columns) {
        ArrayNode fields = MAPPER.createArrayNode();
        for (FormColumnMetaPo column : columns) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("key", column.getColumnCode());
            node.put("label", StringUtils.hasText(column.getColumnName())
                    ? column.getColumnName() : column.getColumnCode());
            node.put("type", column.getDataType());
            node.put("required", column.getRequired() != null && column.getRequired() == 1);
            if (FormDataType.TEXT.equals(column.getDataType())) {
                node.put("maxLength",
                        column.getLength() == null ? FormDataType.DEFAULT_TEXT_LENGTH : column.getLength());
            }
            if (FormDataType.ATTACHMENT.equals(column.getDataType())) {
                node.put("multiple", true);
                node.put("maxCount", 0);
            }
            if (FormDataType.SUBTABLE.equals(column.getDataType())) {
                node.put("minRows", 0);
                node.put("maxRows", 0);
            }
            fields.add(node);
        }
        if (fields.isEmpty()) {
            throw paramError("表定义无可发布的列");
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.set(FormSchemaParser.KEY_FIELDS, fields);
        return root.toString();
    }

    /**
     * 第一阶段：收集全部数据字段 key，并校验关联列存在且不重复引用
     *
     * @param configs 字段配置列表
     * @param columns 列元数据映射
     * @return 数据字段 key 集合
     * @throws BizException 关联列缺失或重复引用列时抛出 {@code PARAM_ERROR}
     */
    private static Set<String> collectKeys(List<FormFieldConfigPo> configs, Map<Long, FormColumnMetaPo> columns) {
        Set<String> keys = new HashSet<>();
        for (FormFieldConfigPo config : configs) {
            if (config.getColumnMetaId() == null) {
                continue;
            }
            FormColumnMetaPo column = columns.get(config.getColumnMetaId());
            if (column == null) {
                throw paramError("字段配置关联的列不存在：" + config.getColumnMetaId());
            }
            if (!keys.add(column.getColumnCode())) {
                throw paramError("同一表单视图重复引用列：" + column.getColumnCode());
            }
        }
        return keys;
    }

    /**
     * 单个字段配置 → schema 字段节点
     *
     * @param config 字段配置
     * @param column 关联列元数据
     * @param options 该字段的选项列表（可空）
     * @param allKeys 本表单全部数据字段 key 集合（联动校验用）
     * @return schema 字段节点
     */
    private static ObjectNode toFieldNode(FormFieldConfigPo config, FormColumnMetaPo column,
                                          List<FormFieldOptionPo> options, Set<String> allKeys) {
        ObjectNode node = MAPPER.createObjectNode();
        String label = StringUtils.hasText(config.getFieldLabel()) ? config.getFieldLabel() : column.getColumnName();
        node.put("key", column.getColumnCode());
        node.put("label", label);
        node.put("type", column.getDataType());
        node.put("widget", FormWidgetType.resolve(config.getWidgetType(), column.getDataType()));
        node.put("required", column.getRequired() != null && column.getRequired() == 1);
        if (FormDataType.TEXT.equals(column.getDataType())) {
            node.put("maxLength",
                    column.getLength() == null ? FormDataType.DEFAULT_TEXT_LENGTH : column.getLength());
        }
        if (options != null && !options.isEmpty()) {
            ArrayNode optionArray = node.putArray("options");
            for (FormFieldOptionPo option : options) {
                optionArray.add(option.getOptionValue());
            }
        }
        applyFieldExtras(node, column.getDataType(), config.getValidateJson(), label,
                column.getColumnCode(), allKeys);
        return node;
    }

    /**
     * 把字段配置里的扩展规则（附件 multiple/accept/maxCount、子表 children/minRows/maxRows、
     * 字段联动 linkage）合并进 schema 字段节点。扩展规则以 {@code validateJson} 文本持久化，
     * 避免改动既有表结构。
     *
     * @param node schema 字段节点
     * @param dataType 存储类型
     * @param validateJson 字段配置的校验规则 JSON（可空）
     * @param label 字段显示名（错误提示）
     * @param currentKey 当前字段 key（联动禁止自引用）
     * @param allKeys 本表单全部数据字段 key 集合
     * @throws BizException 扩展规则或联动规则非法时抛出 {@code PARAM_ERROR}
     */
    private static void applyFieldExtras(ObjectNode node, String dataType, String validateJson, String label,
                                         String currentKey, Set<String> allKeys) {
        if (!StringUtils.hasText(validateJson)) {
            return;
        }
        JsonNode extra;
        try {
            extra = MAPPER.readTree(validateJson);
        } catch (Exception e) {
            throw paramError("字段 " + label + " 的扩展配置不是合法 JSON");
        }
        if (extra == null || !extra.isObject()) {
            throw paramError("字段 " + label + " 的扩展配置必须为 JSON 对象");
        }
        if (FormDataType.ATTACHMENT.equals(dataType)) {
            node.put("multiple", extra.path("multiple").asBoolean(true));
            node.put("maxCount", Math.max(extra.path("maxCount").asInt(0), 0));
            JsonNode accept = extra.get("accept");
            if (accept != null && !accept.isNull() && StringUtils.hasText(accept.asText())) {
                node.put("accept", accept.asText());
            }
        }
        if (FormDataType.SUBTABLE.equals(dataType)) {
            JsonNode children = extra.get("children");
            if (children != null && children.isArray()) {
                node.set("children", children);
            }
            node.put("minRows", Math.max(extra.path("minRows").asInt(0), 0));
            node.put("maxRows", Math.max(extra.path("maxRows").asInt(0), 0));
        }
        JsonNode linkage = extra.get("linkage");
        if (linkage != null) {
            FormLinkageValidator.validate(linkage, currentKey, allKeys, label);
            node.set("linkage", linkage);
        }
    }

    /**
     * 构造参数错误异常
     *
     * @param message 错误消息
     * @return 参数错误业务异常
     */
    private static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
