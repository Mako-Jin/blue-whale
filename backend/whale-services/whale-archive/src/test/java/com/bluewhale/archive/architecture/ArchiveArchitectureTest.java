package com.bluewhale.archive.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 档案服务分层依赖守护（T-18 扩展）。
 *
 * <p>本服务采用单模块分包结构（{@code controller / service / mapper / entity / client / remote / scheduler}），
 * 依赖方向固化如下：{@code controller → service → mapper → entity}，反向依赖一律禁止。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * <p><b>越层治理进展</b>：{@code ArchiveInternalController} 曾直接注入 {@code ArchiveFileMapper}
 * 查询文件密级，现已下沉到 {@code ArchiveItemService#security}，controller→mapper 门禁随之开启。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.archive", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchiveArchitectureTest {

    /** 控制器不得直接依赖实体（controller→mapper 已于 2026-10-31 下沉后开启门禁） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_entity = noClasses()
            .that().resideInAPackage("com.bluewhale.archive.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.archive.entity..");

    /** 控制器不得直接依赖 Mapper（一律经 service 层访问持久化） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper = noClasses()
            .that().resideInAPackage("com.bluewhale.archive.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.archive.mapper..");

    /** Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAPackage("com.bluewhale.archive.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.archive.controller..",
                    "com.bluewhale.archive.service..");

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.archive.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.archive.controller..",
                    "com.bluewhale.archive.service..",
                    "com.bluewhale.archive.mapper..");
}
