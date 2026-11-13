package com.bluewhale.common.springdoc;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * springdoc 契约口径对齐：把 OpenAPI 文档中所有 {@code type: integer, format: int64}
 * 改写为 {@code type: string}，与 {@link com.bluewhale.common.jackson.config.JacksonConfig}
 * 的全局 Long→String 序列化保持一致。
 * <p>不修的话 openapi-typescript 生成的 TS 类型把 Long 字段标成 {@code number}，
 * 而运行时 JSON 里它们全是字符串（雪花 ID 超出 JS 安全整数），类型接线反而引入错误。
 * 影响面：components.schemas 与 paths 下内联 schema（含参数、数组项、additionalProperties、
 * allOf/anyOf/oneOf）中的一切 int64 字段；{@code int}/{@code Integer}（int32）不受影响。</p>
 *
 * <p>注意：springdoc 3.1.1 的 {@code GlobalOpenApiCustomizer} 只被分组文档
 * （AbstractMultipleOpenApiResource）消费，默认单文档端点 {@code /v3/api-docs}
 * 不认它 —— 必须注册普通 {@code OpenApiCustomizer}（在路径扫描完成后调用）。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
@ConditionalOnClass(OpenApiCustomizer.class)
public class SpringdocConfig {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(SpringdocConfig.class);

    /** OpenAPI 里 Jackson Long 对应的 format 标记 */
    private static final String FORMAT_INT64 = "int64";
    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_STRING = "string";

    /**
     * 注册 Long→String 契约改写器
     *
     * @return OpenAPI 定制器
     */
    @Bean
    public OpenApiCustomizer whaleLongToStringApiCustomizer() {
        return this::customise;
    }

    private int rewritten = 0;

    /**
     * 遍历契约并改写全部 int64 字段，输出改写数量日志
     *
     * @param openApi OpenAPI 契约文档
     */
    private void customise(OpenAPI openApi) {
        rewritten = 0;
        if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
            openApi.getComponents().getSchemas().values().forEach(this::fixSchema);
        }
        if (openApi.getPaths() != null) {
            for (PathItem item : openApi.getPaths().values()) {
                fixPathItem(item);
            }
        }
        LOG.info("whaleLongToStringApiCustomizer: rewrote {} int64 fields", rewritten);
    }

    /**
     * 改写路径项：参数、请求体、响应中的内联 schema 全部下钻
     *
     * @param item 路径项
     */
    private void fixPathItem(PathItem item) {
        if (item.getParameters() != null) {
            item.getParameters().forEach(this::fixParameter);
        }
        for (Map.Entry<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> e
                : item.readOperationsMap().entrySet()) {
            io.swagger.v3.oas.models.Operation op = e.getValue();
            if (op == null) {
                continue;
            }
            if (op.getParameters() != null) {
                op.getParameters().forEach(this::fixParameter);
            }
            if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
                op.getRequestBody().getContent().values().stream()
                        .filter(mt -> mt != null && mt.getSchema() != null)
                        .forEach(mt -> fixSchema(mt.getSchema()));
            }
            if (op.getResponses() != null) {
                op.getResponses().values().stream()
                        .filter(resp -> resp != null && resp.getContent() != null)
                        .flatMap(resp -> resp.getContent().values().stream())
                        .filter(mt -> mt != null && mt.getSchema() != null)
                        .forEach(mt -> fixSchema(mt.getSchema()));
            }
        }
    }

    /**
     * 改写单个参数的 schema
     *
     * @param p 参数（可空）
     */
    private void fixParameter(Parameter p) {
        if (p != null && p.getSchema() != null) {
            fixSchema(p.getSchema());
        }
    }

    /** 递归改写：int64 → string；并下钻嵌套结构与组合结构。
     *  OpenAPI 3.1 下 springdoc 用 {@code addType} 填 types 集合，{@code getType()} 为 null，
     *  必须双轨判断并双轨回写，否则改写落空（rewrote=0）。 */
    private void fixSchema(Schema<?> s) {
        if (s == null) {
            return;
        }
        boolean isInt64 = FORMAT_INT64.equals(s.getFormat())
                && (TYPE_INTEGER.equals(s.getType())
                        || (s.getTypes() != null && s.getTypes().contains(TYPE_INTEGER)));
        if (isInt64) {
            s.setType(TYPE_STRING);
            s.setTypes(new java.util.LinkedHashSet<>(java.util.Set.of(TYPE_STRING)));
            s.setFormat(null);
            rewritten++;
        }
        if (s.getProperties() != null) {
            s.getProperties().values().forEach(this::fixSchema);
        }
        if (s.getItems() != null) {
            fixSchema(s.getItems());
        }
        if (s.getAdditionalProperties() instanceof Schema<?> ap) {
            fixSchema(ap);
        }
        if (s.getAllOf() != null) {
            s.getAllOf().forEach(this::fixSchema);
        }
        if (s.getAnyOf() != null) {
            s.getAnyOf().forEach(this::fixSchema);
        }
        if (s.getOneOf() != null) {
            s.getOneOf().forEach(this::fixSchema);
        }
    }
}
