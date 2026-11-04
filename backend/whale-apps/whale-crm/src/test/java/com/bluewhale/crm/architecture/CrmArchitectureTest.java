package com.bluewhale.crm.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 客户关系应用分层依赖守护（DDD 四层 + Ports &amp; Adapters）。
 *
 * <p>门禁通过后新增的领域 / 应用 / 持久化代码必须遵守同一依赖方向：
 * {@code interfaces → application → domain}，{@code infrastructure} 实现端口。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.crm", importOptions = ImportOption.DoNotIncludeTests.class)
class CrmArchitectureTest {

    /** 领域层不得依赖框架、持久化实现、应用层与接口层（门禁通过前无领域类，允许空集） */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.crm.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.baomidou..",
                    "com.bluewhale.crm.infrastructure..",
                    "com.bluewhale.crm.application..",
                    "com.bluewhale.crm.interfaces..")
            .allowEmptyShould(true);

    /** 应用层不得依赖持久化实现与接口层（门禁通过前无应用类，允许空集） */
    @ArchTest
    static final ArchRule application_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.crm.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.crm.infrastructure..",
                    "com.bluewhale.crm.interfaces..")
            .allowEmptyShould(true);

    /** 接口层不得直连领域对象与持久化实现 */
    @ArchTest
    static final ArchRule interfaces_must_not_depend_on_domain_or_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.crm.interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.crm.domain..",
                    "com.bluewhale.crm.infrastructure..");
}
