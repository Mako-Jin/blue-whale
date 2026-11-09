package com.bluewhale.assistant.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * AI 助手平台服务分层依赖守护（T-18 扩展，对齐 whale-agent）。
 *
 * <p>本服务采用单模块分包结构（{@code controller / service / mapper / entity / dto}），
 * 依赖方向固化如下：{@code controller → service → mapper → entity}，反向依赖一律禁止。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.assistant", importOptions = ImportOption.DoNotIncludeTests.class)
class AssistantArchitectureTest {

    /** 控制器不得直接依赖 Mapper（持久化访问一律经 service 层） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper = noClasses()
            .that().resideInAPackage("com.bluewhale.assistant.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.assistant.mapper..");

    /** 控制器不得直接依赖实体（响应装配一律下沉到 service 层，T-18） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_entity = noClasses()
            .that().resideInAPackage("com.bluewhale.assistant.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.assistant.entity..");

    /**
     * Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务。
     *
     * <p>M0 骨架期 mapper/entity 包暂无类，{@code allowEmptyShould(true)} 允许空规则；
     * 包内出现类后规则自动生效（后续 M1 落地实体层，无需改本测试）。</p>
     */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAPackage("com.bluewhale.assistant.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.assistant.controller..",
                    "com.bluewhale.assistant.service..")
            .allowEmptyShould(true);

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper（空包期允许空规则） */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.assistant.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.assistant.controller..",
                    "com.bluewhale.assistant.service..",
                    "com.bluewhale.assistant.mapper..")
            .allowEmptyShould(true);
}
