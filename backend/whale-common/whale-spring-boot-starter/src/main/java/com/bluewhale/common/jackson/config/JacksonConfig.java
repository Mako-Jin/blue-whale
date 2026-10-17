package com.bluewhale.common.jackson.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 全局 Jackson 序列化配置（Spring Boot 4 使用 Jackson 3，包名 {@code tools.jackson.*}）。
 * <p>将 {@code Long} / {@code long} 统一序列化为 JSON <b>字符串</b>：
 * 雪花主键为 64 位 long，超出 JavaScript {@code Number.MAX_SAFE_INTEGER}（2^53-1），
 * 前端直接按 number 解析会丢精度（如 {@code 1251323419503984640} → {@code 1251323419503984600}）。</p>
 * <p>影响面：全平台所有 {@code Long}/{@code long} 字段（含 {@code id}、
 * {@code PageResult.total/size/current}）在 JSON 中均为字符串；{@code int}/{@code Integer}
 * （如 {@code Result.code}）不受影响。前端类型需按 {@code string} 声明 Long 字段。</p>
 *
 * @author BlueWhale
 */
@AutoConfiguration
public class JacksonConfig {

    /** 序列化模块名，便于序列化异常时定位来源 */
    private static final String MODULE_NAME = "whale-long-to-string";

    /**
     * 注册 Long → String 序列化器，作用于 Spring MVC 的 HttpMessageConverter 所使用的 JsonMapper
     */
    @Bean
    public JsonMapperBuilderCustomizer whaleLongToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule(MODULE_NAME);
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.addModule(module);
        };
    }
}
