package com.bluewhale.common.lock.aop;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.lock.annotation.DistributedLock;
import com.bluewhale.common.tenant.TenantKeys;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * {@link DistributedLock} 切面：以 Redisson {@code RLock} 实现方法级互斥。
 * <p>执行顺序：解析 Key → {@code tryLock(waitTime, leaseTime, SECONDS)} → 成功则执行目标方法，
 * {@code finally} 中仅当 {@code isLocked() && isHeldByCurrentThread()} 时解锁（防误删他人锁）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Aspect
@Order(DistributedLockAspect.LOCK_ASPECT_ORDER)
public class DistributedLockAspect {

    /**
     * 切面优先级：数值越小越先执行（外层）。须高于事务切面
     * （{@code @Transactional} 默认 {@code Ordered.LOWEST_PRECEDENCE}），
     * 使加锁包裹整个事务，避免"锁已释放但事务未提交"。
     */
    public static final int LOCK_ASPECT_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    /** Key 分段分隔符 */
    private static final String KEY_SEPARATOR = ":";

    /** 无 SpEL 时的退化 Key 连接符 */
    private static final String METHOD_KEY_SEPARATOR = ".";

    private final RedissonClient redissonClient;

    /** SpEL 解析器（线程安全，复用单例） */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /** 方法参数名发现器，支撑 {@code #phone} 形式的 SpEL */
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 构造分布式锁切面
     *
     * @param redissonClient Redisson 客户端，用于获取锁对象
     */
    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 环绕通知：尝试加锁后执行目标方法，finally 中释放当前线程持有的锁
     *
     * @param joinPoint 连接点
     * @param distributedLock 方法上的分布式锁注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(distributedLock)")
    public Object lockAround(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = buildLockKey(joinPoint, distributedLock);
        RLock lock = redissonClient.getLock(lockKey);
        if (!acquire(lock, distributedLock, lockKey)) {
            // failFast=false：获取失败仅告警放行，不阻断业务主线
            return joinPoint.proceed();
        }
        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 尝试获取锁
     *
     * @return true 表示已持有锁；false 表示未获取且 {@code failFast=false}
     */
    private boolean acquire(RLock lock, DistributedLock annotation, String lockKey) {
        boolean acquired;
        try {
            acquired = lock.tryLock(annotation.waitTime(), annotation.leaseTime(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // 恢复中断标志，避免吞掉中断信号（B9：不吞异常）
            Thread.currentThread().interrupt();
            log.warn("分布式锁获取被中断: key={}", lockKey);
            throw new BizException(ErrorCode.LOCK_ACQUIRE_FAILED);
        }
        if (acquired) {
            return true;
        }
        if (annotation.failFast()) {
            log.warn("分布式锁获取失败（快速失败）: key={}", lockKey);
            throw new BizException(ErrorCode.LOCK_ACQUIRE_FAILED);
        }
        log.warn("分布式锁获取失败，按 failFast=false 放行: key={}", lockKey);
        return false;
    }

    /**
     * 解析锁 Key：{@code whale:{tenantId}:{prefix}:{biz}:{SpEL 求值}}；SpEL 为空时退化为
     * {@code whale:{tenantId}:{prefix}:{biz}:{类名}.{方法名}}
     */
    private String buildLockKey(ProceedingJoinPoint joinPoint, DistributedLock annotation) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        StringBuilder keyBuilder = new StringBuilder(
                TenantKeys.lockPrefix(annotation.prefix(), annotation.biz()));
        if (annotation.key().isEmpty()) {
            keyBuilder.append(KEY_SEPARATOR)
                    .append(signature.getDeclaringType().getSimpleName())
                    .append(METHOD_KEY_SEPARATOR)
                    .append(signature.getName());
        } else {
            Method method = signature.getMethod();
            EvaluationContext context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
            Object value = expressionParser.parseExpression(annotation.key()).getValue(context);
            keyBuilder.append(KEY_SEPARATOR).append(value);
        }
        return keyBuilder.toString();
    }
}
