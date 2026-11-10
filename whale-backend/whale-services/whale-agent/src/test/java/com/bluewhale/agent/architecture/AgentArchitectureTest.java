package com.bluewhale.agent.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 知识 Agent 服务分层依赖守护（T-18 扩展）。
 *
 * <p>本服务采用单模块分包结构（{@code controller / service / mapper / entity / dto}），
 * 依赖方向固化如下：{@code controller → service → mapper → entity}，反向依赖一律禁止。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * <p><b>越层治理进展（2026-10-31）</b>：{@code AgentFactController} 响应装配下沉到
 * {@code FactStoreService#queryVo}，{@code AgentIndexController} 改用
 * {@code AgentIndexMetaService#snapshot}，{@code AgentKnowledgeController} 分页映射
 * 下沉到 {@code AgentKnowledgeFileService#pageVo / AgentKnowledgeDocService#pageVo}；
 * 至此 controller 不再依赖任何实体，controller→entity 门禁全量开启。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.agent", importOptions = ImportOption.DoNotIncludeTests.class)
class AgentArchitectureTest {

    /** 控制器不得直接依赖 Mapper（持久化访问一律经 service 层） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper = noClasses()
            .that().resideInAPackage("com.bluewhale.agent.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.agent.mapper..");

    /** 控制器不得直接依赖实体（响应装配已全部下沉到 service 层，T-18） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_entity = noClasses()
            .that().resideInAPackage("com.bluewhale.agent.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.agent.entity..");

    /** Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAPackage("com.bluewhale.agent.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.agent.controller..",
                    "com.bluewhale.agent.service..");

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.agent.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.agent.controller..",
                    "com.bluewhale.agent.service..",
                    "com.bluewhale.agent.mapper..");
}
