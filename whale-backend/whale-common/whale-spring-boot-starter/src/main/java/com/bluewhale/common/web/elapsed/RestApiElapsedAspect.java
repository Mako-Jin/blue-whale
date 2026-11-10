package com.bluewhale.common.web.elapsed;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 接口耗时日志切面：统计控制器方法执行耗时，超过阈值时降级为 WARN 日志。
 *
 * <p>迁移自参考基座 {@code sts-common-web} 的 {@code RestApiElapsedAspect}，改为由配置项
 * {@code whale.web.api.elapsed.enabled} 控制启用，去除了启动注解的属性解析逻辑。</p>
 *
 * @author BlueWhale
 */
@Aspect
public class RestApiElapsedAspect {

    /** 日志 */
    private static final Logger LOG = LoggerFactory.getLogger(RestApiElapsedAspect.class);

    /** 耗时配置 */
    private final RestApiElapsedProperties properties;

    /**
     * 构造器
     *
     * @param properties 耗时配置
     */
    public RestApiElapsedAspect(RestApiElapsedProperties properties) {
        this.properties = properties;
    }

    /**
     * 统计控制器方法耗时并记录日志
     *
     * @param point 连接点
     * @return 方法返回值
     * @throws Throwable 方法执行异常原样抛出
     */
    @Around("execution(public * com.bluewhale..controller..*.*(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long start = System.nanoTime();
        String target = describe(point);
        try {
            Object result = point.proceed();
            logElapsed(target, start, null);
            return result;
        } catch (Throwable ex) {
            logElapsed(target, start, ex);
            throw ex;
        }
    }

    /**
     * 输出耗时日志：超过阈值用 WARN，否则 DEBUG
     *
     * @param target 调用目标描述
     * @param startNanos 开始时间（纳秒）
     * @param error 异常；正常返回时为 {@code null}
     */
    private void logElapsed(String target, long startNanos, Throwable error) {
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        boolean slow = elapsedMillis >= properties.getThreshold();
        if (error == null) {
            if (slow) {
                LOG.warn("{} cost = {}ms (threshold {}ms)", target, elapsedMillis, properties.getThreshold());
            } else {
                LOG.debug("{} cost = {}ms", target, elapsedMillis);
            }
            return;
        }
        LOG.warn("{} failed, cost = {}ms, error = {}", target, elapsedMillis, error.getMessage());
    }

    /**
     * 构造调用目标描述：{@code 类名#方法名 [METHOD /path]}
     *
     * @param point 连接点
     * @return 调用目标描述
     */
    private static String describe(ProceedingJoinPoint point) {
        String target = point.getSignature().getDeclaringType().getSimpleName()
                + "#" + point.getSignature().getName();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return target + " [" + attributes.getRequest().getMethod() + " "
                    + attributes.getRequest().getRequestURI() + "]";
        }
        return target;
    }
}
