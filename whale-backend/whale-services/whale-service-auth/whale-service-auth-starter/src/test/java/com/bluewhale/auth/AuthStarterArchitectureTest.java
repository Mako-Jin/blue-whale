package com.bluewhale.auth;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Web 适配层依赖守护：{@code starter} 只做协议适配与装配，不得绕过应用层直接操作领域聚合。
 *
 * <p>为何不放在 {@code whale-service-auth-architecture-test}：那里以 test 作用域依赖本模块，
 * 拿到的构件是 Spring Boot 可执行包（repackage 后类位于 {@code BOOT-INF/classes}），
 * ArchUnit 的 importer 一个类都读不到，规则会以 "failed to check any classes" 失败
 * （Backend CI run#36 实证）。放在本模块自己的测试里，扫描的是 {@code target/classes}，
 * 断言的对象与原来完全一致。</p>
 *
 * <p>其余分层规则仍在 {@code AuthArchitectureTest}（domain / application / infrastructure
 * 三个模块都是普通 jar，不存在上述问题）。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.auth", importOptions = ImportOption.DoNotIncludeTests.class)
class AuthStarterArchitectureTest {

    @ArchTest
    static final ArchRule starter_must_not_depend_on_domain = noClasses()
            .that().resideInAPackage("com.bluewhale.auth.starter..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.auth.domain..");
}
