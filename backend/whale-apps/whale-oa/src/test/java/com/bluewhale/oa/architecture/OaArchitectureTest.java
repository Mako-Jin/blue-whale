package com.bluewhale.oa.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * OA 应用 DDD-lite 分层与上下文边界守护。
 *
 * <p>依赖方向：{@code interfaces → application → domain → infrastructure}。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.oa", importOptions = ImportOption.DoNotIncludeTests.class)
class OaArchitectureTest {

    /** 接口层只依赖应用层与领域模型，不得直接依赖基础设施 */
    @ArchTest
    static final ArchRule interfaces_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.oa..interfaces..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.oa..infrastructure..");

    /** 应用层只依赖领域模型与端口，不得直接依赖基础设施实现 */
    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.oa..application..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.oa..infrastructure..");

    /** 领域层不得依赖应用层、接口层或基础设施 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.oa..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.oa..application..",
                    "com.bluewhale.oa..interfaces..",
                    "com.bluewhale.oa..infrastructure..");

    /** 领域层为纯模型，不得依赖 Spring 或 MyBatis */
    @ArchTest
    static final ArchRule domain_must_be_framework_free = noClasses()
            .that().resideInAPackage("com.bluewhale.oa..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.baomidou..",
                    "jakarta.persistence..");

    /** 基础设施层只实现领域端口，不得反向依赖接口层或应用层 */
    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.oa..infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.oa..interfaces..",
                    "com.bluewhale.oa..application..");

    /** 审批核心上下文不得依赖任何业务上下文（业务上下文单向依赖审批） */
    @ArchTest
    static final ArchRule approval_must_not_depend_on_business_contexts = noClasses()
            .that().resideInAPackage("com.bluewhale.oa.approval..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.oa.hr..",
                    "com.bluewhale.oa.attendance..",
                    "com.bluewhale.oa.expense..",
                    "com.bluewhale.oa.administrative..",
                    "com.bluewhale.oa.collab..",
                    "com.bluewhale.oa.document..",
                    "com.bluewhale.oa.culture..",
                    "com.bluewhale.oa.analytics..");

    /** 共享内核不得依赖审批或业务上下文 */
    @ArchTest
    static final ArchRule common_must_not_depend_on_business_contexts = noClasses()
            .that().resideInAPackage("com.bluewhale.oa.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.oa.approval..",
                    "com.bluewhale.oa.hr..",
                    "com.bluewhale.oa.attendance..",
                    "com.bluewhale.oa.expense..",
                    "com.bluewhale.oa.administrative..",
                    "com.bluewhale.oa.collab..",
                    "com.bluewhale.oa.document..",
                    "com.bluewhale.oa.culture..",
                    "com.bluewhale.oa.analytics..");
}
