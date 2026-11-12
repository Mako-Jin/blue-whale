package com.bluewhale.forum.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 论坛服务分层依赖守护（对齐 whale-twin / whale-iot）。
 *
 * <p>本服务采用单模块分包结构（{@code controller / service / mapper / entity / dto / common}），
 * 依赖方向固化如下：{@code controller → service → mapper → entity}，反向依赖一律禁止。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * <p>M0 阶段 {@code service / mapper / entity} 三个包为空，规则以
 * {@code allowEmptyShould(true)} 允许空扫描；M1 落实体层后自动生效，无需改本测试。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.forum", importOptions = ImportOption.DoNotIncludeTests.class)
class ForumArchitectureTest {

    /** 控制器不得直接依赖 Mapper（持久化访问一律经 service 层） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper = noClasses()
            .that().resideInAPackage("com.bluewhale.forum.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.forum.mapper..")
            .allowEmptyShould(true);

    /** 控制器不得直接依赖实体（响应装配一律下沉到 service 层） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_entity = noClasses()
            .that().resideInAPackage("com.bluewhale.forum.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.forum.entity..")
            .allowEmptyShould(true);

    /** Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAPackage("com.bluewhale.forum.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.forum.controller..",
                    "com.bluewhale.forum.service..")
            .allowEmptyShould(true);

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.forum.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.forum.controller..",
                    "com.bluewhale.forum.service..",
                    "com.bluewhale.forum.mapper..")
            .allowEmptyShould(true);

    /**
     * 门面 DTO 只承载数据，不得反向依赖业务层。
     *
     * <p>DTO 若引用了 service，序列化时会把整条依赖链拖进响应模型，
     * 后续拆分查询侧时先被 DTO 卡住。</p>
     */
    @ArchTest
    static final ArchRule dto_must_not_depend_on_service_or_mapper = noClasses()
            .that().resideInAPackage("com.bluewhale.forum.dto..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.forum.service..",
                    "com.bluewhale.forum.mapper..",
                    "com.bluewhale.forum.controller..")
            .allowEmptyShould(true);
}
