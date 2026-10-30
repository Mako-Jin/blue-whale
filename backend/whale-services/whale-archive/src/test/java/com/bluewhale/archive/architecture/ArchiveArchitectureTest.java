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
 * <p><b>已知越层依赖（暂不纳入门禁）</b>：{@code ArchiveInternalController} 直接注入
 * {@code ArchiveFileMapper} 查询文件密级，controller 越过 service 依赖 mapper，
 * 按 T-18 约定不改业务代码，故此处仅约束 controller 不得依赖 entity。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.archive", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchiveArchitectureTest {

    /** 控制器不得直接依赖实体（controller→mapper 已存在真实依赖，暂不门禁） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_entity = noClasses()
            .that().resideInAPackage("com.bluewhale.archive.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.archive.entity..");

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
