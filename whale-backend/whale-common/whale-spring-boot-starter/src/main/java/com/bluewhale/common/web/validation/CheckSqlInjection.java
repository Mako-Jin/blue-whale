package com.bluewhale.common.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SQL 注入参数校验注解：拒绝包含注入特征片段（注释符、联合查询、恒等式条件等）的文本。
 *
 * <p>迁移自参考基座 {@code sts-common-web} 的 {@code CheckSqlInjection}。</p>
 *
 * @author BlueWhale
 */
@Documented
@Repeatable(CheckSqlInjection.List.class)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SqlInjectionValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
public @interface CheckSqlInjection {

    /**
     * 校验失败提示
     *
     * @return 提示消息
     */
    String message() default "参数疑似包含 SQL 注入内容";

    /**
     * 校验分组
     *
     * @return 分组类型
     */
    Class<?>[] groups() default {};

    /**
     * 负载
     *
     * @return 负载类型
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * 重复注解容器
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
            ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
    @interface List {

        /**
         * 重复注解集合
         *
         * @return 注解数组
         */
        CheckSqlInjection[] value();
    }
}
