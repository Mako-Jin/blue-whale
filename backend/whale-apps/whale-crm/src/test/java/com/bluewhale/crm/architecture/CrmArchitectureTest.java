package com.bluewhale.crm.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 客户关系应用分层依赖守护（DDD 四层 + Ports &amp; Adapters）。
 *
 * <p>Wave 5.83 起 CRM 采用六业务上下文（customer / sales / commerce / service / marketing /
 * analytics）加 integration / common 的模块化单体。依赖方向固化为
 * {@code interfaces → application → domain}，{@code infrastructure} 实现 domain 端口；
 * 业务上下文之间只允许经 domain 端口或应用服务协作，禁止反向依赖 integration 实现。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * <p>尚未落地的上下文允许空集：M2–M6 交付后规则自动生效。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.crm", importOptions = ImportOption.DoNotIncludeTests.class)
class CrmArchitectureTest {

    /** 六个业务上下文 */
    private static final String[] CONTEXTS = {
            "customer", "sales", "commerce", "service", "marketing", "analytics"
    };

    /** 领域层不得依赖框架、应用层、接口层与基础设施层 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.crm.customer.domain..",
                    "com.bluewhale.crm.sales.domain..",
                    "com.bluewhale.crm.commerce.domain..",
                    "com.bluewhale.crm.service.domain..",
                    "com.bluewhale.crm.marketing.domain..",
                    "com.bluewhale.crm.analytics.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.crm.customer.application..",
                    "com.bluewhale.crm.customer.interfaces..",
                    "com.bluewhale.crm.customer.infrastructure..",
                    "com.bluewhale.crm.sales.application..",
                    "com.bluewhale.crm.sales.interfaces..",
                    "com.bluewhale.crm.sales.infrastructure..",
                    "com.bluewhale.crm.commerce.application..",
                    "com.bluewhale.crm.commerce.interfaces..",
                    "com.bluewhale.crm.commerce.infrastructure..",
                    "com.bluewhale.crm.service.application..",
                    "com.bluewhale.crm.service.interfaces..",
                    "com.bluewhale.crm.service.infrastructure..",
                    "com.bluewhale.crm.marketing.application..",
                    "com.bluewhale.crm.marketing.interfaces..",
                    "com.bluewhale.crm.marketing.infrastructure..",
                    "com.bluewhale.crm.analytics.application..",
                    "com.bluewhale.crm.analytics.interfaces..",
                    "com.bluewhale.crm.analytics.infrastructure..")
            .allowEmptyShould(true);

    /** 领域层不依赖 Spring / MyBatis，保持可独立单测 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.crm.customer.domain..",
                    "com.bluewhale.crm.sales.domain..",
                    "com.bluewhale.crm.commerce.domain..",
                    "com.bluewhale.crm.service.domain..",
                    "com.bluewhale.crm.marketing.domain..",
                    "com.bluewhale.crm.analytics.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.baomidou..",
                    "jakarta.persistence..")
            .allowEmptyShould(true);

    /** 应用层不得依赖持久化实现与接口层（协作一律经 domain 端口） */
    @ArchTest
    static final ArchRule application_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.crm.customer.application..",
                    "com.bluewhale.crm.sales.application..",
                    "com.bluewhale.crm.commerce.application..",
                    "com.bluewhale.crm.service.application..",
                    "com.bluewhale.crm.marketing.application..",
                    "com.bluewhale.crm.analytics.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.crm.customer.infrastructure..",
                    "com.bluewhale.crm.customer.interfaces..",
                    "com.bluewhale.crm.sales.infrastructure..",
                    "com.bluewhale.crm.sales.interfaces..",
                    "com.bluewhale.crm.commerce.infrastructure..",
                    "com.bluewhale.crm.commerce.interfaces..",
                    "com.bluewhale.crm.service.infrastructure..",
                    "com.bluewhale.crm.service.interfaces..",
                    "com.bluewhale.crm.marketing.infrastructure..",
                    "com.bluewhale.crm.marketing.interfaces..",
                    "com.bluewhale.crm.analytics.infrastructure..",
                    "com.bluewhale.crm.analytics.interfaces..")
            .allowEmptyShould(true);

    /** 接口层不得直接依赖持久化实现（装配一律经应用服务） */
    @ArchTest
    static final ArchRule interfaces_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.crm.interfaces..",
                    "com.bluewhale.crm.customer.interfaces..",
                    "com.bluewhale.crm.sales.interfaces..",
                    "com.bluewhale.crm.commerce.interfaces..",
                    "com.bluewhale.crm.service.interfaces..",
                    "com.bluewhale.crm.marketing.interfaces..",
                    "com.bluewhale.crm.analytics.interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.crm.customer.infrastructure..",
                    "com.bluewhale.crm.sales.infrastructure..",
                    "com.bluewhale.crm.commerce.infrastructure..",
                    "com.bluewhale.crm.service.infrastructure..",
                    "com.bluewhale.crm.marketing.infrastructure..",
                    "com.bluewhale.crm.analytics.infrastructure..")
            .allowEmptyShould(true);

    /** 客户上下文不得反向依赖其他业务上下文与集成实现（商机摘要只走 domain 端口） */
    @ArchTest
    static final ArchRule customer_must_not_depend_on_other_contexts = noClasses()
            .that().resideInAPackage("com.bluewhale.crm.customer..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.crm.sales..",
                    "com.bluewhale.crm.commerce..",
                    "com.bluewhale.crm.service..",
                    "com.bluewhale.crm.marketing..",
                    "com.bluewhale.crm.analytics..",
                    "com.bluewhale.crm.integration..")
            .allowEmptyShould(true);

    /** 业务上下文不得直接依赖 integration 实现（跨上下文只走 domain 端口） */
    @ArchTest
    static final ArchRule business_contexts_must_not_depend_on_integration = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.crm.customer..",
                    "com.bluewhale.crm.sales..",
                    "com.bluewhale.crm.commerce..",
                    "com.bluewhale.crm.service..",
                    "com.bluewhale.crm.marketing..",
                    "com.bluewhale.crm.analytics..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.crm.integration..")
            .allowEmptyShould(true);

    /** 集成上下文实现端口，但不得反向依赖业务上下文的持久化实现 */
    @ArchTest
    static final ArchRule integration_must_not_depend_on_context_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.crm.integration..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.crm.customer.infrastructure..",
                    "com.bluewhale.crm.sales.infrastructure..",
                    "com.bluewhale.crm.commerce.infrastructure..",
                    "com.bluewhale.crm.service.infrastructure..",
                    "com.bluewhale.crm.marketing.infrastructure..",
                    "com.bluewhale.crm.analytics.infrastructure..")
            .allowEmptyShould(true);

    /** 共享内核（common / config）不得依赖任何业务上下文 */
    @ArchTest
    static final ArchRule common_must_not_depend_on_business_contexts = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.crm.common..",
                    "com.bluewhale.crm.config..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.crm.customer..",
                    "com.bluewhale.crm.sales..",
                    "com.bluewhale.crm.commerce..",
                    "com.bluewhale.crm.service..",
                    "com.bluewhale.crm.marketing..",
                    "com.bluewhale.crm.analytics..",
                    "com.bluewhale.crm.integration..")
            .allowEmptyShould(true);

    /** 六个业务上下文必须按四层分包（防止新代码退回 controller / service 平铺结构） */
    @ArchTest
    static final ArchRule context_layers_must_be_reserved = classes()
            .that().resideInAnyPackage(
                    "com.bluewhale.crm.customer..",
                    "com.bluewhale.crm.sales..",
                    "com.bluewhale.crm.commerce..",
                    "com.bluewhale.crm.service..",
                    "com.bluewhale.crm.marketing..",
                    "com.bluewhale.crm.analytics..")
            .should().resideInAnyPackage(
                    "com.bluewhale.crm..domain..",
                    "com.bluewhale.crm..application..",
                    "com.bluewhale.crm..interfaces..",
                    "com.bluewhale.crm..infrastructure..")
            .allowEmptyShould(true);
}
