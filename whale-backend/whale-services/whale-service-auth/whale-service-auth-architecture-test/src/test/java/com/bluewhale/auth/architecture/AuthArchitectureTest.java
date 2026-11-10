package com.bluewhale.auth.architecture;

import com.bluewhale.auth.infrastructure.security.PermissionCacheMutex;
import com.bluewhale.common.lock.annotation.DistributedLock;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 权限中心分层依赖守护：以 ArchUnit 固化 DDD 依赖方向
 * {@code interfaces → application → domain ← infrastructure}。
 *
 * <p>规则仅扫描生产类（{@link ImportOption.DoNotIncludeTests}），防止领域层被框架侵蚀，
 * 以及应用层重新直连 Redis、Spring Security 加密器、持久化等基础设施细节。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.auth", importOptions = ImportOption.DoNotIncludeTests.class)
class AuthArchitectureTest {

    /** 领域层禁止依赖的包：外层、视图对象与框架 */
    private static final String[] DOMAIN_FORBIDDEN_PACKAGES = {
            "com.bluewhale.auth.infrastructure..",
            "com.bluewhale.auth.application..",
            "com.bluewhale.auth.interfaces..",
            "org.springframework..",
            "org.apache.ibatis..",
            "com.baomidou..",
            "jakarta.."
    };

    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks_or_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.auth.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(DOMAIN_FORBIDDEN_PACKAGES);

    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure_or_within_interfaces = noClasses()
            .that().resideInAPackage("com.bluewhale.auth.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.auth.infrastructure..",
                    "com.bluewhale.auth.interfaces..",
                    "com.baomidou..",
                    "org.apache.ibatis..",
                    "org.springframework.data.redis..",
                    "org.springframework.security..");

    @ArchTest
    static final ArchRule interfaces_must_not_depend_on_infrastructure_or_domain = noClasses()
            .that().resideInAPackage("com.bluewhale.auth.interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.auth.infrastructure..",
                    "com.bluewhale.auth.domain..",
                    "com.baomidou..",
                    "org.apache.ibatis..");

    /**
     * API 契约隔离：{@code interfaces.api} 只允许声明 HTTP 契约与 DTO，不得反向依赖
     * application / domain / infrastructure / starter（实现位于 starter 层）。
     */
    @ArchTest
    static final ArchRule api_contracts_must_not_depend_on_implementation_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.auth.interfaces.api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.auth.application..",
                    "com.bluewhale.auth.domain..",
                    "com.bluewhale.auth.infrastructure..",
                    "com.bluewhale.auth.starter..");

    /**
     * Web 适配层只做协议适配与装配：允许依赖 interfaces / application / infrastructure，
     * 但不得绕过应用层直接操作领域聚合与仓储。
     */
    @ArchTest
    static final ArchRule starter_must_not_depend_on_domain = noClasses()
            .that().resideInAPackage("com.bluewhale.auth.starter..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.auth.domain..");

    /**
     * 根包白名单：{@code com.bluewhale.auth} 下除启动类外，只允许分层的固定包，
     * 防止再次出现 {@code entity/}、{@code mapper/}、{@code util/} 这类"第五层"。
     * <p>{@code starter} 为 Web 适配层（接口实现 + MapStruct 装配）。</p>
     */
    @ArchTest
    static final ArchRule only_layered_packages_at_root = classes()
            .that().resideInAPackage("com.bluewhale.auth..")
            .and().resideOutsideOfPackage("com.bluewhale.auth")
            .and().resideOutsideOfPackage("com.bluewhale.auth.sdk..")
            .should().resideInAnyPackage(
                    "com.bluewhale.auth.domain..",
                    "com.bluewhale.auth.application..",
                    "com.bluewhale.auth.infrastructure..",
                    "com.bluewhale.auth.interfaces..",
                    "com.bluewhale.auth.starter..");

    /**
     * 分布式锁放行语义守护（T-14）：{@code @DistributedLock} 的 {@code failFast} 默认必须为 true，
     * 显式声明 {@code failFast = false} 只允许出现在「幂等可退化」的 {@link PermissionCacheMutex}
     * （权限缓存重建互斥）。新增任何放行点都会让本规则失败，须先评审并同步更新白名单。
     */
    @ArchTest
    static final ArchRule distributed_lock_fail_fast_false_only_on_cache_mutex = classes()
            .that().resideInAPackage("com.bluewhale.auth..")
            .should(new ArchCondition<JavaClass>("除 PermissionCacheMutex 外不得显式关闭 failFast") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    if (javaClass.getName().equals(PermissionCacheMutex.class.getName())) {
                        return;
                    }
                    for (JavaMethod method : javaClass.getMethods()) {
                        for (JavaAnnotation<JavaMethod> annotation : method.getAnnotations()) {
                            if (!annotation.getRawType().getName().equals(DistributedLock.class.getName())) {
                                continue;
                            }
                            annotation.tryGetExplicitlyDeclaredProperty("failFast")
                                    .filter(Boolean.FALSE::equals)
                                    .ifPresent(value -> events.add(SimpleConditionEvent.violated(method,
                                            method.getFullName() + " 显式设置 failFast=false，仅 PermissionCacheMutex 允许")));
                        }
                    }
                }
            });
}
