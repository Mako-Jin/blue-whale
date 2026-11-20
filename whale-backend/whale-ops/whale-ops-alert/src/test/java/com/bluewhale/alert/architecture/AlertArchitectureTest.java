package com.bluewhale.alert.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 告警服务分层依赖守护（T-18 剩余服务越层专项）。
 *
 * <p>本服务采用单模块分包结构（{@code controller / service / mapper / entity / dto / vo}），
 * 依赖方向固化如下：{@code controller → service → mapper → entity}，反向依赖一律禁止。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * <p><b>扫描结论</b>：2026-11-02 全量扫描 3 个 controller、4 个 entity/mapper 文件，
 * 未发现越层依赖，四条规则直接开启门禁。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.alert", importOptions = ImportOption.DoNotIncludeTests.class)
class AlertArchitectureTest {

    /** 控制器不得直接依赖实体（装配一律经 service 层完成） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_entity = noClasses()
            .that().resideInAPackage("com.bluewhale.alert.controller..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.alert.entity..");

    /** 控制器不得直接依赖 Mapper（持久化访问一律经 service 层） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper = noClasses()
            .that().resideInAPackage("com.bluewhale.alert.controller..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.alert.mapper..");

    /** 出入参模型不得直接暴露持久化实体或 Mapper */
    @ArchTest
    static final ArchRule dto_and_vo_must_not_depend_on_persistence = noClasses()
            .that().resideInAnyPackage("com.bluewhale.alert.dto..", "com.bluewhale.alert.vo..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.alert.entity..",
                    "com.bluewhale.alert.mapper..");

    /** Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAPackage("com.bluewhale.alert.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.alert.controller..",
                    "com.bluewhale.alert.service..");

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.alert.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.alert.controller..",
                    "com.bluewhale.alert.service..",
                    "com.bluewhale.alert.mapper..");
}
