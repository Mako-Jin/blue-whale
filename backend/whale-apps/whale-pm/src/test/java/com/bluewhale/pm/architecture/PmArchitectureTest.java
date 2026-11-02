package com.bluewhale.pm.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 项目管理应用分层依赖守护（T-18 剩余服务越层专项）。
 *
 * <p>本服务采用 DDD 分包结构（{@code interfaces(controller/dto/vo) → application →
 * domain ⇢ infrastructure(entity/mapper/client)}），与其余单模块服务不同，
 * 实体与 Mapper 位于 {@code infrastructure} 下，因此规则按 DDD 层级命名：</p>
 *
 * <ul>
 *   <li>接口层控制器不得依赖基础设施层（实体 / Mapper / 外部客户端）；</li>
 *   <li>出入参模型（dto/vo）不得依赖基础设施层；</li>
 *   <li>Mapper 不得反向依赖控制器、应用服务或领域层；</li>
 *   <li>实体不得依赖控制器、应用服务、领域层或 Mapper。</li>
 * </ul>
 *
 * <p><b>扫描结论</b>：2026-11-02 全量扫描 7 个 controller、24 个 entity/mapper 文件，
 * 未发现越层依赖，四条规则直接开启门禁。规则只扫描生产类
 * （{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.pm", importOptions = ImportOption.DoNotIncludeTests.class)
class PmArchitectureTest {

    /** 接口层控制器不得依赖基础设施层（实体 / Mapper / 外部客户端一律经应用层获取） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.pm.interfaces.controller..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.pm.infrastructure..");

    /** 请求/响应模型不得依赖基础设施层（避免持久化细节泄漏到接口契约） */
    @ArchTest
    static final ArchRule dto_and_vo_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.pm.interfaces.dto..",
                    "com.bluewhale.pm.interfaces.vo..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.pm.infrastructure..");

    /** Mapper 只依赖实体与框架，不得反向依赖控制器、应用层或领域层 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.pm.infrastructure.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.pm.interfaces..",
                    "com.bluewhale.pm.application..",
                    "com.bluewhale.pm.domain..");

    /** 实体为纯持久化模型，不得依赖控制器、应用层、领域层或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.pm.infrastructure.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.pm.interfaces..",
                    "com.bluewhale.pm.application..",
                    "com.bluewhale.pm.domain..",
                    "com.bluewhale.pm.infrastructure.mapper..");
}
