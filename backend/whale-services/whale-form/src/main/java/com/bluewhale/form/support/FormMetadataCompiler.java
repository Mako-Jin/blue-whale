package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.form.entity.FormColumnMeta;
import com.bluewhale.form.entity.FormFieldConfig;
import com.bluewhale.form.entity.FormFieldOption;
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
 * <p>把表单视图层的字段配置（{@link FormFieldConfig}，含选项 {@link FormFieldOption}）与表定义层的
 * 列元数据（{@link FormColumnMeta}）编译为 Wave 5.27 冻结的 {@code schema_json} 结构
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
    public static String compile(List<FormFieldConfig> configs, Map<Long, FormColumnMeta> columns,
                                 Map<Long, List<FormFieldOption>> options) {
        ArrayNode fields = MAPPER.createArrayNode();
        Set<String> keys = new HashSet<>();
        for (FormFieldConfig config : configs) {
            if (config.getColumnMetaId() == null) {
                continue;
            }
            FormColumnMeta column = columns.get(config.getColumnMetaId());
            if (column == null) {
                throw paramError("字段配置关联的列不存在：" + config.getColumnMetaId());
            }
            String key = column.getColumnCode();
            if (!keys.add(key)) {
                throw paramError("同一表单视图重复引用列：" + key);
            }
            fields.add(toFieldNode(config, column, options.get(config.getId())));
        }
        if (fields.isEmpty()) {
            throw paramError("表单视图未配置任何数据字段");
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.set(FormSchemaParser.KEY_FIELDS, fields);
        return root.toString();
    }

    /**
     * 单个字段配置 → schema 字段节点
     *
     * @param config 字段配置
     * @param column 关联列元数据
     * @param options 该字段的选项列表（可空）
     * @return schema 字段节点
     */
    private static ObjectNode toFieldNode(FormFieldConfig config, FormColumnMeta column,
                                          List<FormFieldOption> options) {
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
            for (FormFieldOption option : options) {
                optionArray.add(option.getOptionValue());
            }
        }
        return node;
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
