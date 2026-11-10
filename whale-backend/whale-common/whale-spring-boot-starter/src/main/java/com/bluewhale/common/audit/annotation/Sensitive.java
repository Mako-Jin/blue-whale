package com.bluewhale.common.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级脱敏注解（AUTH-11 兜底，REQ-SEC-006）：标注在返回体对象的字段上时，
 * 该字段值无论是否命中 key 脱敏规则都整体掩码。
 *
 * <p>使用约束：</p>
 * <ol>
 *   <li>仅对返回体（{@code result_body}）脱敏生效，由
 *       {@code SensitiveDataMasker#maskJson(Object, String)} 读取被记录类型上的注解；</li>
 *   <li>与内置规则 / {@code whale.audit.sensitive.keys} 配置化键名<b>并集</b>生效，命中即整体掩码；</li>
 *   <li>不改变 {@code request_params} 既有脱敏语义。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {
}
