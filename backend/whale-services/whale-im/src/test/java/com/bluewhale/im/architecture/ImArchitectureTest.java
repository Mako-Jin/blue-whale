package com.bluewhale.im.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * IM 服务分层依赖守护（T-18 扩展）。
 *
 * <p>本服务采用单模块分包结构（{@code controller / service / mapper / entity / ws}），
 * 依赖方向固化如下：{@code controller → service → mapper → entity}，反向依赖一律禁止。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * <p><b>已知越层依赖（暂不纳入门禁）</b>：{@code ImConversationController} 直接引用
 * {@code ImConversation / ImMessage} 实体进行响应装配，controller 依赖 entity，
 * 按 T-18 约定不改业务代码，故此处仅约束 controller 不得依赖 mapper。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.im", importOptions = ImportOption.DoNotIncludeTests.class)
class ImArchitectureTest {

    /** 控制器不得直接依赖 Mapper（controller→entity 已存在真实依赖，暂不门禁） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper = noClasses()
            .that().resideInAPackage("com.bluewhale.im.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.im.mapper..");

    /** Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAPackage("com.bluewhale.im.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.im.controller..",
                    "com.bluewhale.im.service..");

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.im.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.im.controller..",
                    "com.bluewhale.im.service..",
                    "com.bluewhale.im.mapper..");
}
