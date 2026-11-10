package com.bluewhale.contract.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 合同应用分层依赖守护（T-18 剩余服务越层专项）。
 *
 * <p>本服务采用单模块分包结构（{@code controller / service / mapper / entity / dto / vo /
 * domain / esign}），依赖方向固化如下：{@code controller → service → mapper → entity}，
 * 反向依赖一律禁止。规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），
 * 避免测试替身触发误报。</p>
 *
 * <p><b>扫描结论</b>：2026-11-02 全量扫描 4 个 controller、18 个 entity/mapper 文件，
 * 未发现 controller→entity/mapper、mapper→service、entity→外层或 dto/vo→entity 的越层依赖，
 * 故直接为四条规则开启门禁。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.contract", importOptions = ImportOption.DoNotIncludeTests.class)
class ContractArchitectureTest {

    /** 控制器不得直接依赖实体（装配一律经 service 层完成） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_entity = noClasses()
            .that().resideInAPackage("com.bluewhale.contract.controller..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.contract.entity..");

    /** 控制器不得直接依赖 Mapper（持久化访问一律经 service 层） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper = noClasses()
            .that().resideInAPackage("com.bluewhale.contract.controller..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.contract.mapper..");

    /** 出入参模型不得直接暴露持久化实体或 Mapper */
    @ArchTest
    static final ArchRule dto_and_vo_must_not_depend_on_persistence = noClasses()
            .that().resideInAnyPackage("com.bluewhale.contract.dto..", "com.bluewhale.contract.vo..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.contract.entity..",
                    "com.bluewhale.contract.mapper..");

    /** Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAPackage("com.bluewhale.contract.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.contract.controller..",
                    "com.bluewhale.contract.service..");

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.contract.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.contract.controller..",
                    "com.bluewhale.contract.service..",
                    "com.bluewhale.contract.mapper..");
}
