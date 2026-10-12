package com.bluewhale.sts.auth.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限域三套标识的形状约束。
 *
 * <p>本域有**三个各自独立、形状相近、极易混用**的概念：
 * <pre>
 *   资源标识   auth:user           （{@link AuthResources}）—— 这个对象是什么
 *   权限码     auth:user:create    （{@link AuthPermissions}）—— 谁被允许做什么
 *   动作标识   auth.user.create    （{@link AuthAuditActions}）—— 实际发生了什么
 * </pre>
 *
 * <p>混用的代价都是**静默**的：{@code @Audited} 的 {@code resource} 若填成权限码，
 * 不报错、不告警，只是"按资源过滤审计"这个查询永远返回空 —— 而它正是审计面板的
 * 主要入口。这类错误靠评审很难稳定拦住（三者的字面量看起来就该长这样），
 * 因此把形状与归属关系钉成自动断言。
 *
 * <p>本类只做**结构性**断言，不断言"哪个动作对应哪条权限"：
 * 那层对应关系不是一一映射 —— {@code auth.user.reset_password} 对应的是
 * {@code auth:user:update} 权限，而 {@code auth:user:read} 根本没有对应的审计动作
 * （查询不记审计）。硬把映射写成测试，只会逼着后来者去"补齐"那些本不该存在的条目。
 */
class AuthTaxonomyTest {

    /** 动作标识：三段小写点分，如 auth.user.reset_password */
    private static final Pattern ACTION_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){2}$");

    /** 资源标识：两段小写冒号分隔，如 auth:user */
    private static final Pattern RESOURCE_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$");

    /** 权限码：三段小写冒号分隔，如 auth:user:create */
    private static final Pattern PERMISSION_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$");

    @Test
    @DisplayName("动作标识一律三段点分：域.资源.动作")
    void actionNamesShouldBeThreeSegments() {
        assertThat(constantsOf(AuthAuditActions.class))
                .as("动作标识是永久契约，形状必须统一 —— 落库之后改名等于把历史数据劈成两半")
                .allMatch(value -> ACTION_PATTERN.matcher(value).matches());
    }

    @Test
    @DisplayName("资源标识一律两段冒号分隔：域:资源")
    void resourceNamesShouldBeTwoSegments() {
        assertThat(constantsOf(AuthResources.class))
                .allMatch(value -> RESOURCE_PATTERN.matcher(value).matches());
    }

    @Test
    @DisplayName("权限码一律三段冒号分隔：域:资源:动作")
    void permissionCodesShouldBeThreeSegments() {
        assertThat(AuthPermissions.ALL)
                .allMatch(value -> PERMISSION_PATTERN.matcher(value).matches());
    }

    @Test
    @DisplayName("每个权限码都能归到某个已声明的资源标识下")
    void everyPermissionShouldBelongToADeclaredResource() {
        List<String> resources = constantsOf(AuthResources.class);

        assertThat(AuthPermissions.ALL)
                .as("权限码写出来了却没有对应的资源标识 —— 按资源过滤时这条权限会静默查不到")
                .allMatch(permission -> resources.stream().anyMatch(r -> permission.startsWith(r + ":")));
    }

    @Test
    @DisplayName("每个动作标识都能归到某个已声明的资源标识下")
    void everyActionShouldBelongToADeclaredResource() {
        List<String> resources = constantsOf(AuthResources.class);

        assertThat(constantsOf(AuthAuditActions.class))
                .as("动作标识的资源段与资源标识对不上 —— 审计里按资源过滤会漏掉这个动作")
                .allMatch(action -> resources.stream()
                        .anyMatch(r -> action.startsWith(r.replace(':', '.') + ".")));
    }

    /** 取出某个常量类里所有 public static final String 的值 */
    private static List<String> constantsOf(Class<?> type) {
        List<String> values = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)
                    || field.getType() != String.class) {
                continue;
            }
            try {
                values.add((String) field.get(null));
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("无法读取常量：" + field.getName(), ex);
            }
        }
        assertThat(values).as("%s 里应当至少有一个常量", type.getSimpleName()).isNotEmpty();
        return values;
    }

}
