package com.bluewhale.mes.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * MES 应用分层依赖守护（T-18 剩余服务越层专项）。
 *
 * <p>本服务采用单模块分包结构，存量代码位于 {@code controller / service / mapper / entity /
 * converter / dto / vo}，M0 起新增上下文按 {@code 上下文.层级} 分包（如
 * {@code masterdata.controller / masterdata.service / masterdata.mapper}）。依赖方向统一为
 * {@code controller → service → mapper → entity}，反向依赖一律禁止；规则只扫描生产类
 * （{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * <p><b>扫描结论</b>：2026-11-04 M0 引入 {@code masterdata} 与 {@code common.outbox} 两个包后，
 * 分层规则覆盖存量顶层包与新增上下文包，未发现越层依赖。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.mes", importOptions = ImportOption.DoNotIncludeTests.class)
class MesArchitectureTest {

    /** 控制器所在包（存量顶层 + M0/M1 新增上下文） */
    private static final String[] CONTROLLER_PACKAGES = {
            "com.bluewhale.mes.controller..",
            "com.bluewhale.mes.masterdata.controller..",
            "com.bluewhale.mes.dispatch.controller..",
            "com.bluewhale.mes.execution.controller.."
    };

    /** 业务服务所在包（存量顶层 + M0/M1 新增上下文） */
    private static final String[] SERVICE_PACKAGES = {
            "com.bluewhale.mes.service..",
            "com.bluewhale.mes.masterdata.service..",
            "com.bluewhale.mes.common.outbox.service..",
            "com.bluewhale.mes.dispatch.service..",
            "com.bluewhale.mes.execution.service.."
    };

    /** Mapper 所在包（存量顶层 + M0/M1 新增上下文） */
    private static final String[] MAPPER_PACKAGES = {
            "com.bluewhale.mes.mapper..",
            "com.bluewhale.mes.masterdata.mapper..",
            "com.bluewhale.mes.common.outbox.mapper..",
            "com.bluewhale.mes.dispatch.mapper..",
            "com.bluewhale.mes.execution.mapper.."
    };

    /** 实体所在包（存量顶层 + M0/M1 新增上下文） */
    private static final String[] ENTITY_PACKAGES = {
            "com.bluewhale.mes.entity..",
            "com.bluewhale.mes.masterdata.entity..",
            "com.bluewhale.mes.common.outbox.entity..",
            "com.bluewhale.mes.dispatch.entity..",
            "com.bluewhale.mes.execution.entity.."
    };

    /** 出入参模型所在包（存量顶层 + M0/M1 新增上下文） */
    private static final String[] MODEL_PACKAGES = {
            "com.bluewhale.mes.dto..",
            "com.bluewhale.mes.vo..",
            "com.bluewhale.mes.masterdata.dto..",
            "com.bluewhale.mes.masterdata.vo..",
            "com.bluewhale.mes.dispatch.dto..",
            "com.bluewhale.mes.dispatch.vo..",
            "com.bluewhale.mes.execution.dto..",
            "com.bluewhale.mes.execution.vo.."
    };

    /** 控制器不得直接依赖实体（装配一律经 converter/service 层完成） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_entity = noClasses()
            .that().resideInAnyPackage(CONTROLLER_PACKAGES)
            .should().dependOnClassesThat().resideInAnyPackage(ENTITY_PACKAGES);

    /** 控制器不得直接依赖 Mapper（持久化访问一律经 service 层） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper = noClasses()
            .that().resideInAnyPackage(CONTROLLER_PACKAGES)
            .should().dependOnClassesThat().resideInAnyPackage(MAPPER_PACKAGES);

    /** 出入参模型不得直接暴露持久化实体或 Mapper */
    @ArchTest
    static final ArchRule dto_and_vo_must_not_depend_on_persistence = noClasses()
            .that().resideInAnyPackage(MODEL_PACKAGES)
            .should().dependOnClassesThat().resideInAnyPackage(ENTITY_PACKAGES)
            .orShould().dependOnClassesThat().resideInAnyPackage(MAPPER_PACKAGES);

    /** Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAnyPackage(MAPPER_PACKAGES)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER_PACKAGES)
            .orShould().dependOnClassesThat().resideInAnyPackage(SERVICE_PACKAGES);

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAnyPackage(ENTITY_PACKAGES)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER_PACKAGES)
            .orShould().dependOnClassesThat().resideInAnyPackage(SERVICE_PACKAGES)
            .orShould().dependOnClassesThat().resideInAnyPackage(MAPPER_PACKAGES);

    /** 车间主数据上下文不得反向依赖存量顶层 controller/service（上下文边界） */
    @ArchTest
    static final ArchRule masterdata_must_not_depend_on_legacy_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.mes.masterdata..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.mes.controller..",
                    "com.bluewhale.mes.service..",
                    "com.bluewhale.mes.mapper..",
                    "com.bluewhale.mes.entity..");
}
