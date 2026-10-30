package com.bluewhale.pay.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 支付服务分层依赖守护（T-18 试点）。
 *
 * <p>本服务采用单模块分包结构（{@code controller / service / mapper / entity / dto / vo}），
 * 依赖方向固化如下：{@code controller → service → mapper → entity}，反向依赖一律禁止。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.pay", importOptions = ImportOption.DoNotIncludeTests.class)
class PayArchitectureTest {

    /** 控制器不得越过 service 直接依赖持久化细节 */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper_or_entity = noClasses()
            .that().resideInAPackage("com.bluewhale.pay.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.pay.mapper..",
                    "com.bluewhale.pay.entity..");

    /** Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAPackage("com.bluewhale.pay.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.pay.controller..",
                    "com.bluewhale.pay.service..");

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.pay.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.pay.controller..",
                    "com.bluewhale.pay.service..",
                    "com.bluewhale.pay.mapper..");
}
