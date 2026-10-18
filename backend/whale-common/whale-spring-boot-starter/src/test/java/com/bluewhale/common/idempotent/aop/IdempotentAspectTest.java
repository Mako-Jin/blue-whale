package com.bluewhale.common.idempotent.aop;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.idempotent.annotation.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 幂等切面单测：覆盖首次成功缓存结果、重复命中缓存、处理中拒绝、异常释放 Key、token 缺失放行、空结果哨兵。
 *
 * @author BlueWhale
 */
class IdempotentAspectTest {

    /** 首次调用期望的 Redis 幂等键 */
    private static final String CREATE_BIZ_KEY = "whale:auth:user:create:alice";

    private final RedissonClient redissonClient = mock(RedissonClient.class);

    private final IdempotentAspect aspect = new IdempotentAspect(redissonClient);

    /**
     * 清理 Spring 请求上下文，避免用例间串扰
     */
    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 首次调用：抢占幂等键成功、执行目标方法并把返回体序列化写入缓存
     */
    @Test
    void shouldCacheAndReturnResultOnFirstInvocation() throws Throwable {
        AtomicReference<String> store = new AtomicReference<>();
        RBucket<String> bucket = mockBucket(store);
        when(redissonClient.<String>getBucket(CREATE_BIZ_KEY)).thenReturn(bucket);
        ProceedingJoinPoint joinPoint = joinPoint("createUser", new Object[]{new CreatePayload("alice")});
        Result<String> expected = Result.ok("created");
        when(joinPoint.proceed()).thenReturn(expected);

        Object result = aspect.around(joinPoint, annotation("createUser", CreatePayload.class));

        assertThat(result).isSameAs(expected);
        assertThat(store.get()).isNotNull().contains("\"created\"");
        verify(redissonClient).getBucket(CREATE_BIZ_KEY);
        verify(joinPoint).proceed();
    }

    /**
     * 重复调用：命中首次缓存的结果，返回相同业务结果且不再执行目标方法
     */
    @Test
    void shouldReturnCachedResultOnRepeatedInvocation() throws Throwable {
        AtomicReference<String> store = new AtomicReference<>();
        RBucket<String> bucket = mockBucket(store);
        when(redissonClient.<String>getBucket(CREATE_BIZ_KEY)).thenReturn(bucket);
        Result<String> expected = Result.ok("created");
        ProceedingJoinPoint firstCall = joinPoint("createUser", new Object[]{new CreatePayload("alice")});
        when(firstCall.proceed()).thenReturn(expected);

        aspect.around(firstCall, annotation("createUser", CreatePayload.class));

        ProceedingJoinPoint secondCall = joinPoint("createUser", new Object[]{new CreatePayload("alice")});
        when(secondCall.proceed()).thenThrow(new IllegalStateException("重复调用不应执行目标方法"));

        Object secondResult = aspect.around(secondCall, annotation("createUser", CreatePayload.class));

        assertThat(secondResult).isEqualTo(expected);
        assertThat(secondResult).isNotSameAs(expected);
        verify(secondCall, never()).proceed();
    }

    /**
     * 处理中重复提交：命中处理中哨兵时抛 {@code BizException(REPEAT_SUBMIT)}
     */
    @Test
    void shouldRejectWhileProcessing() throws Throwable {
        AtomicReference<String> store = new AtomicReference<>(IdempotentAspect.PROCESSING_SENTINEL);
        RBucket<String> bucket = mockBucket(store);
        when(redissonClient.<String>getBucket(CREATE_BIZ_KEY)).thenReturn(bucket);
        ProceedingJoinPoint joinPoint = joinPoint("createUser", new Object[]{new CreatePayload("alice")});

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation("createUser", CreatePayload.class)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.REPEAT_SUBMIT.getCode());
        verify(joinPoint, never()).proceed();
    }

    /**
     * 目标方法抛异常：删除幂等键释放占用，异常原样向上抛出以允许重试
     */
    @Test
    void shouldReleaseKeyWhenTargetThrows() throws Throwable {
        AtomicReference<String> store = new AtomicReference<>();
        RBucket<String> bucket = mockBucket(store);
        when(redissonClient.<String>getBucket(CREATE_BIZ_KEY)).thenReturn(bucket);
        ProceedingJoinPoint joinPoint = joinPoint("createUser", new Object[]{new CreatePayload("alice")});
        BizException failure = new BizException(ErrorCode.SYSTEM_ERROR);
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.around(joinPoint, annotation("createUser", CreatePayload.class)))
                .isSameAs(failure);

        assertThat(store.get()).isNull();
        verify(bucket).delete();
    }

    /**
     * token 缺失：无 SpEL 且无请求上下文时不做幂等约束，直接放行且不触碰 Redis
     */
    @Test
    void shouldProceedWhenTokenMissing() throws Throwable {
        ProceedingJoinPoint joinPoint =
                joinPoint("createWithoutToken", new Object[]{new CreatePayload("alice")});
        Result<String> expected = Result.ok("created");
        when(joinPoint.proceed()).thenReturn(expected);

        Object result = aspect.around(joinPoint, annotation("createWithoutToken", CreatePayload.class));

        assertThat(result).isSameAs(expected);
        verify(joinPoint).proceed();
        verifyNoInteractions(redissonClient);
    }

    /**
     * 空结果：目标方法返回 null 时写入空值哨兵，区别于「处理中」哨兵
     */
    @Test
    void shouldStoreNullSentinelWhenResultIsNull() throws Throwable {
        AtomicReference<String> store = new AtomicReference<>();
        RBucket<String> bucket = mockBucket(store);
        when(redissonClient.<String>getBucket(CREATE_BIZ_KEY)).thenReturn(bucket);
        ProceedingJoinPoint joinPoint = joinPoint("deleteUser", new Object[]{new CreatePayload("alice")});
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.around(joinPoint, annotation("deleteUser", CreatePayload.class));

        assertThat(result).isNull();
        assertThat(store.get()).isEqualTo(IdempotentAspect.NULL_SENTINEL);
    }

    /**
     * 构造模拟 SETNX 语义的桶对象：值为 null 时 trySet 成功并写入，否则失败
     *
     * @param store 模拟的 Redis 存储单元
     * @return 与 {@code store} 联动的桶对象
     */
    private RBucket<String> mockBucket(AtomicReference<String> store) {
        RBucket<String> bucket = mock(RBucket.class);
        when(bucket.trySet(anyString(), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            if (store.get() != null) {
                return false;
            }
            store.set(invocation.getArgument(0));
            return true;
        });
        when(bucket.get()).thenAnswer(invocation -> store.get());
        doAnswer(invocation -> {
            store.set(invocation.getArgument(0));
            return null;
        }).when(bucket).set(anyString(), anyLong(), any(TimeUnit.class));
        when(bucket.delete()).thenAnswer(invocation -> store.getAndSet(null) != null);
        return bucket;
    }

    /**
     * 构造被切面拦截的连接点
     *
     * @param methodName 目标方法名
     * @param args 方法参数数组
     * @return 连接点模拟对象
     */
    private ProceedingJoinPoint joinPoint(String methodName, Object[] args) {
        Method method = findMethod(SampleService.class, methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getReturnType()).thenReturn(method.getReturnType());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.getTarget()).thenReturn(new SampleService());
        return joinPoint;
    }

    /**
     * 反射查找样例服务的指定方法
     *
     * @param declaringType 声明类型
     * @param methodName 方法名
     * @return 方法对象
     */
    private static Method findMethod(Class<?> declaringType, String methodName) {
        return List.of(declaringType.getMethods()).stream()
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("测试方法不存在: " + methodName));
    }

    /**
     * 从样例服务方法上读取幂等注解
     *
     * @param methodName 方法名
     * @param parameterType 首个参数类型
     * @return 幂等注解实例
     * @throws NoSuchMethodException 方法不存在时抛出
     */
    private static Idempotent annotation(String methodName, Class<?> parameterType) throws NoSuchMethodException {
        return SampleService.class.getMethod(methodName, parameterType).getAnnotation(Idempotent.class);
    }

    /** 被幂等切面拦截的样例服务 */
    static class SampleService {

        /**
         * 创建用户（SpEL 取请求用户名作为幂等 token）
         *
         * @param request 创建请求
         * @return 创建结果
         */
        @Idempotent(biz = "auth:user:create", key = "#request.username")
        public Result<String> createUser(CreatePayload request) {
            return Result.ok("created");
        }

        /**
         * 删除用户（无 token 来源，用于验证缺失放行）
         *
         * @param request 创建请求
         * @return 创建结果
         */
        @Idempotent(biz = "auth:user:create")
        public Result<String> createWithoutToken(CreatePayload request) {
            return Result.ok("created");
        }

        /**
         * 删除用户（返回 void，用于验证空结果哨兵）
         *
         * @param request 创建请求
         */
        @Idempotent(biz = "auth:user:create", key = "#request.username")
        public void deleteUser(CreatePayload request) {
            // 仅用于测试 void 返回类型的幂等缓存语义
        }
    }

    /** 创建类请求样例（SpEL {@code #request.username} 依赖 getter 访问属性） */
    static class CreatePayload {

        private final String username;

        /**
         * 构造创建请求样例
         *
         * @param username 用户名
         */
        CreatePayload(String username) {
            this.username = username;
        }

        /**
         * 获取用户名
         *
         * @return 用户名
         */
        public String getUsername() {
            return username;
        }
    }
}
