package com.bluewhale.erp.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ERP 应用分层依赖守护（T-18 剩余服务越层专项）。
 *
 * <p>存量单模块分包结构（{@code controller / service / mapper / entity / dto / vo}）沿用
 * {@code controller → service → mapper → entity} 方向；Wave 5.78 新增的
 * {@code masterdata / finance} 两个上下文采用 DDD 四层分包，依赖方向固化为
 * {@code interfaces → application → domain}，{@code infrastructure} 实现 domain 端口。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.erp", importOptions = ImportOption.DoNotIncludeTests.class)
class ErpArchitectureTest {

    /** 控制器不得直接依赖实体（装配一律经 service 层完成） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_entity = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.controller..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.erp.entity..");

    /** 控制器不得直接依赖 Mapper（持久化访问一律经 service 层） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.controller..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.erp.mapper..");

    /** 出入参模型不得直接暴露持久化实体或 Mapper */
    @ArchTest
    static final ArchRule dto_and_vo_must_not_depend_on_persistence = noClasses()
            .that().resideInAnyPackage("com.bluewhale.erp.dto..", "com.bluewhale.erp.vo..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.entity..",
                    "com.bluewhale.erp.mapper..");

    /** Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.controller..",
                    "com.bluewhale.erp.service..");

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.controller..",
                    "com.bluewhale.erp.service..",
                    "com.bluewhale.erp.mapper..");

    /** 新分层接口层不得直接依赖持久化实现（装配一律经应用服务） */
    @ArchTest
    static final ArchRule interfaces_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.erp.masterdata.interfaces..",
                    "com.bluewhale.erp.finance.interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.masterdata.infrastructure..",
                    "com.bluewhale.erp.finance.infrastructure..");

    /** 新分层应用层不得直接依赖持久化实现（只能经 domain 端口） */
    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.erp.masterdata.application..",
                    "com.bluewhale.erp.finance.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.masterdata.infrastructure..",
                    "com.bluewhale.erp.finance.infrastructure..");

    /** 领域层不得依赖应用层、接口层与基础设施层 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.erp.masterdata.domain..",
                    "com.bluewhale.erp.finance.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.masterdata.application..",
                    "com.bluewhale.erp.finance.application..",
                    "com.bluewhale.erp.masterdata.interfaces..",
                    "com.bluewhale.erp.finance.interfaces..",
                    "com.bluewhale.erp.masterdata.infrastructure..",
                    "com.bluewhale.erp.finance.infrastructure..");

    /** 领域层不依赖 Spring / MyBatis，保持可独立单测 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.erp.masterdata.domain..",
                    "com.bluewhale.erp.finance.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.baomidou..");

    /** 主数据上下文不得反向依赖财务上下文 */
    @ArchTest
    static final ArchRule masterdata_must_not_depend_on_finance = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.masterdata..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.erp.finance..");

    /** 财务领域层与基础设施层不得依赖主数据基础设施（跨上下文只走 domain 端口） */
    @ArchTest
    static final ArchRule finance_must_not_depend_on_masterdata_infrastructure = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.erp.finance.domain..",
                    "com.bluewhale.erp.finance.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.erp.masterdata.infrastructure..");

    /** 供应链接口层不得直接依赖持久化实现 */
    @ArchTest
    static final ArchRule scm_interfaces_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.scm.interfaces..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.erp.scm.infrastructure..");

    /** 供应链应用层不得直接依赖持久化实现与存量分包 */
    @ArchTest
    static final ArchRule scm_application_must_not_depend_on_infrastructure_or_legacy = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.scm.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.scm.infrastructure..",
                    "com.bluewhale.erp.mapper..",
                    "com.bluewhale.erp.entity..",
                    "com.bluewhale.erp.service..");

    /** 供应链领域层不得依赖应用层、接口层、基础设施层与框架 */
    @ArchTest
    static final ArchRule scm_domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.scm.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.scm.application..",
                    "com.bluewhale.erp.scm.interfaces..",
                    "com.bluewhale.erp.scm.infrastructure..",
                    "com.bluewhale.erp.finance..",
                    "com.bluewhale.erp.masterdata..",
                    "org.springframework..",
                    "com.baomidou..");

    /** 财务与主数据上下文不得反向依赖供应链上下文 */
    @ArchTest
    static final ArchRule finance_and_masterdata_must_not_depend_on_scm = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.erp.finance..",
                    "com.bluewhale.erp.masterdata..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.erp.scm..");

    /** 生产计划接口层不得直接依赖持久化实现 */
    @ArchTest
    static final ArchRule mfg_interfaces_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.mfg.interfaces..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.erp.mfg.infrastructure..");

    /** 生产计划应用层不得直接依赖持久化实现与存量分包 */
    @ArchTest
    static final ArchRule mfg_application_must_not_depend_on_infrastructure_or_legacy = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.mfg.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.mfg.infrastructure..",
                    "com.bluewhale.erp.mapper..",
                    "com.bluewhale.erp.entity..",
                    "com.bluewhale.erp.service..");

    /** 生产计划领域层不得依赖应用层、接口层、基础设施层、其他上下文与框架 */
    @ArchTest
    static final ArchRule mfg_domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.mfg.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.mfg.application..",
                    "com.bluewhale.erp.mfg.interfaces..",
                    "com.bluewhale.erp.mfg.infrastructure..",
                    "com.bluewhale.erp.finance..",
                    "com.bluewhale.erp.masterdata..",
                    "com.bluewhale.erp.scm..",
                    "org.springframework..",
                    "com.baomidou..");

    /** 主数据、财务与供应链上下文不得反向依赖生产计划上下文 */
    @ArchTest
    static final ArchRule masterdata_finance_scm_must_not_depend_on_mfg = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.erp.masterdata..",
                    "com.bluewhale.erp.finance..",
                    "com.bluewhale.erp.scm..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.erp.mfg..");

    /** 人力接口层不得直接依赖持久化实现 */
    @ArchTest
    static final ArchRule hr_interfaces_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.hr.interfaces..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.erp.hr.infrastructure..");

    /** 人力应用层不得直接依赖持久化实现与存量分包 */
    @ArchTest
    static final ArchRule hr_application_must_not_depend_on_infrastructure_or_legacy = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.hr.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.hr.infrastructure..",
                    "com.bluewhale.erp.mapper..",
                    "com.bluewhale.erp.entity..",
                    "com.bluewhale.erp.service..");

    /** 人力领域层不得依赖应用层、接口层、基础设施层、其他上下文与框架 */
    @ArchTest
    static final ArchRule hr_domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.erp.hr.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.erp.hr.application..",
                    "com.bluewhale.erp.hr.interfaces..",
                    "com.bluewhale.erp.hr.infrastructure..",
                    "com.bluewhale.erp.finance..",
                    "com.bluewhale.erp.masterdata..",
                    "com.bluewhale.erp.scm..",
                    "com.bluewhale.erp.mfg..",
                    "org.springframework..",
                    "com.baomidou..");

    /** 其他上下文不得反向依赖人力上下文 */
    @ArchTest
    static final ArchRule masterdata_finance_scm_mfg_must_not_depend_on_hr = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.erp.masterdata..",
                    "com.bluewhale.erp.finance..",
                    "com.bluewhale.erp.scm..",
                    "com.bluewhale.erp.mfg..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.erp.hr..");
}
