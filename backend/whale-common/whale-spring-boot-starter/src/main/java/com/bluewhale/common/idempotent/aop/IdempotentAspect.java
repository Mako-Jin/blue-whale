package com.bluewhale.common.idempotent.aop;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.idempotent.annotation.Idempotent;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

/**
 * {@link Idempotent} 切面：以 Redisson {@code RBucket.trySet}（SETNX 语义）实现「一次一消费」。
 * <p>执行顺序：解析 token → {@code trySet} 抢占 Key → 抢占成功则执行目标方法并缓存返回体 JSON；
 * 抢占失败则读取已有值：为 {@code PROCESSING} 哨兵时抛 {@code BizException(REPEAT_SUBMIT)}，
 * 否则反序列化为方法声明的返回类型并直接返回。</p>
 * <p>异常处理：目标方法抛异常时删除 Key 允许重试；token 为空时不做幂等约束直接放行。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Aspect
@Order(IdempotentAspect.IDEMPOTENT_ASPECT_ORDER)
public class IdempotentAspect {

    /**
     * 切面优先级：数值越小越先执行（外层）。须高于事务切面
     * （{@code @Transactional} 默认 {@code Ordered.LOWEST_PRECEDENCE}），
     * 使幂等判定先于事务提交，避免"事务未提交即重复提交"。
     */
    public static final int IDEMPOTENT_ASPECT_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    /** 处理中占位值：首次抢占成功后写入，标识该幂等键尚未完成 */
    static final String PROCESSING_SENTINEL = "__PROCESSING__";

    /** 空结果占位值：目标方法返回 null 或 void 时写入，区别于「处理中」哨兵 */
    static final String NULL_SENTINEL = "null";

    /** Key 分段分隔符，Key 形如 {@code {prefix}:{biz}:{token}} */
    private static final String KEY_SEPARATOR = ":";

    private final RedissonClient redissonClient;

    /** SpEL 解析器（线程安全，复用单例） */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /** 方法参数名发现器，支撑 {@code #request.username} 形式的 SpEL */
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /** Jackson 3 序列化器，用于缓存返回体的 JSON 文本（线程安全） */
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 构造幂等切面
     *
     * @param redissonClient Redisson 客户端，用于获取幂等键桶对象
     */
    public IdempotentAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 环绕通知：以幂等键抢占实现「一次一消费」，抢占失败则返回缓存结果
     *
     * @param joinPoint 连接点
     * @param idempotent 方法上的幂等注解
     * @return 目标方法返回值或首次调用缓存的结果
     * @throws Throwable 目标方法抛出的异常，或重复提交时的 {@code BizException}
     */
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String token = resolveToken(joinPoint, idempotent);
        if (token == null || token.isEmpty()) {
            log.debug("幂等 token 缺失，不做幂等约束直接放行: biz={}", idempotent.biz());
            return joinPoint.proceed();
        }
        String cacheKey = buildCacheKey(idempotent, token);
        RBucket<String> bucket = redissonClient.getBucket(cacheKey);
        if (!bucket.trySet(PROCESSING_SENTINEL, idempotent.expireSeconds(), TimeUnit.SECONDS)) {
            return resolveRepeated(bucket, cacheKey, joinPoint);
        }
        try {
            Object result = joinPoint.proceed();
            writeResult(bucket, result, idempotent);
            return result;
        } catch (Throwable ex) {
            bucket.delete();
            log.warn("幂等目标方法执行异常，已释放幂等键以允许重试: key={}", cacheKey);
            throw ex;
        }
    }

    /**
     * 解析幂等 token：优先使用注解 SpEL，其次读取请求头
     *
     * @param joinPoint 连接点
     * @param idempotent 幂等注解
     * @return token 字符串；无法解析时返回 {@code null}
     */
    private String resolveToken(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        if (!idempotent.key().isEmpty()) {
            return evaluateKey(joinPoint, idempotent.key());
        }
        return readTokenFromHeader(idempotent.tokenHeader());
    }

    /**
     * 以方法参数为根对象求值 SpEL 表达式
     *
     * @param joinPoint 连接点
     * @param expression SpEL 表达式
     * @return 求值结果的字符串形式；结果为 {@code null} 时返回 {@code null}
     */
    private String evaluateKey(ProceedingJoinPoint joinPoint, String expression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        EvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
        Object value = expressionParser.parseExpression(expression).getValue(context);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 从当前请求头读取幂等 token
     *
     * @param headerName 请求头名称
     * @return 请求头值；非 Web 上下文或请求头缺失时返回 {@code null}
     */
    private String readTokenFromHeader(String headerName) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(headerName);
        }
        return null;
    }

    /**
     * 拼接幂等键：{@code {prefix}:{biz}:{token}}
     *
     * @param idempotent 幂等注解
     * @param token 幂等 token
     * @return Redis 幂等键
     */
    private String buildCacheKey(Idempotent idempotent, String token) {
        return idempotent.prefix() + KEY_SEPARATOR + idempotent.biz() + KEY_SEPARATOR + token;
    }

    /**
     * 缓存目标方法返回值：非空序列化为 JSON，空结果写入空值哨兵
     *
     * @param bucket 幂等键桶对象
     * @param result 目标方法返回值，可为 {@code null}
     * @param idempotent 幂等注解
     */
    private void writeResult(RBucket<String> bucket, Object result, Idempotent idempotent) {
        String payload = result == null ? NULL_SENTINEL : jsonMapper.writeValueAsString(result);
        bucket.set(payload, idempotent.expireSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 处理重复提交：处理中哨兵时拒绝，否则反序列化缓存结果返回
     *
     * @param bucket 幂等键桶对象
     * @param cacheKey Redis 幂等键
     * @param joinPoint 连接点，用于获取方法声明的返回类型
     * @return 首次调用缓存的结果；返回类型为 {@code void} 时返回 {@code null}
     * @throws BizException 幂等键处于处理中或状态缺失时抛出 {@code REPEAT_SUBMIT}
     */
    private Object resolveRepeated(RBucket<String> bucket, String cacheKey, ProceedingJoinPoint joinPoint) {
        String existing = bucket.get();
        if (existing == null || PROCESSING_SENTINEL.equals(existing)) {
            log.warn("幂等键处理中或状态缺失，拒绝重复提交: key={}", cacheKey);
            throw new BizException(ErrorCode.REPEAT_SUBMIT);
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        if (method.getReturnType() == void.class) {
            return null;
        }
        Type genericReturnType = method.getGenericReturnType();
        return jsonMapper.readValue(existing, jsonMapper.constructType(genericReturnType));
    }
}
