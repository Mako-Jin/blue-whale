package com.bluewhale.common.audit.annotation;

import com.bluewhale.common.audit.enums.OperateType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解：标注在需要留痕的方法（Controller / Service 均可）上，
 * 由 {@code AuditLogAspect} 采集「谁 / 何时 / 对什么 / 做了什么 / 结果如何」后异步落库。
 *
 * <p>使用约束：</p>
 * <ol>
 *   <li>仅拦显式标注的方法，不按包/类批量拦截，避免日志风暴；</li>
 *   <li>参数含凭据（密码、token）的接口必须置 {@code recordParams = false}，凭据绝不落库；</li>
 *   <li>审计写入失败只告警，不影响业务主流程。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 业务模块，默认取 {@code spring.application.name} */
    String module() default "";

    /** 操作描述，如「创建用户」（字面量，不做 SpEL 求值） */
    String operation();

    /** 操作类型，默认 {@link OperateType#OTHER} */
    OperateType type() default OperateType.OTHER;

    /** 操作对象类型，如 {@code sys_user} / {@code sys_role} */
    String targetType() default "";

    /** 操作对象 ID 的 SpEL 表达式，如 {@code #id} / {@code #request.userId}；为空则忽略 */
    String targetId() default "";

    /** 是否记录请求参数（脱敏后写入 {@code request_params}） */
    boolean recordParams() default true;

    /** 是否记录返回体（默认关闭；当前 {@code audit_log} 无返回体列，暂不落库，预留能力） */
    boolean recordResult() default false;
}
