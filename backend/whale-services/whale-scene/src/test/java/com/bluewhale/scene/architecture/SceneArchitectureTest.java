package com.bluewhale.scene.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 场景服务分层依赖守护（DDD 四层 + Ports &amp; Adapters）。
 *
 * <p>依赖方向：{@code interfaces → application → domain}，{@code infrastructure}
 * 实现 {@code domain} 端口；反向依赖与跨层直连一律禁止。规则只扫描生产类。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.scene", importOptions = ImportOption.DoNotIncludeTests.class)
class SceneArchitectureTest {

    /** 领域层不得依赖框架、持久化实现、应用层与接口层 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.scene.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.baomidou..",
                    "com.bluewhale.scene.infrastructure..",
                    "com.bluewhale.scene.application..",
                    "com.bluewhale.scene.interfaces..");

    /** 应用层不得依赖持久化实现与接口层 */
    @ArchTest
    static final ArchRule application_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.scene.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.scene.infrastructure..",
                    "com.bluewhale.scene.interfaces..");

    /** 接口层只依赖应用层，不得直连领域对象与持久化实现 */
    @ArchTest
    static final ArchRule interfaces_must_not_depend_on_domain_or_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.scene.interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.scene.domain..",
                    "com.bluewhale.scene.infrastructure..");

    /** 持久化层不得反向依赖接口层 */
    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_interfaces = noClasses()
            .that().resideInAPackage("com.bluewhale.scene.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.scene.interfaces..");
}
