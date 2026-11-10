package com.bluewhale.audit.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 审计服务分层依赖守护（T-18 剩余服务越层专项）。
 *
 * <p>本服务为多子域分包结构：查询子域 {@code controller / service / dto / vo}，
 * MQ 汇聚子域 {@code mq.(entity/mapper/service)}，留存子域 {@code retention.(entity/mapper)}，
 * 另有 {@code aspect / config / ratelimit / remote} 支撑包。依赖方向固化如下：
 * {@code controller → service → mapper → entity}，反向依赖一律禁止。</p>
 *
 * <p><b>例外（有意保留）</b>：{@code AuditLogController#ingest} 的入参使用
 * {@code com.bluewhale.common.audit.entity.AuditLogRecord}——它是跨库汇聚的内网传输契约，
 * 由 {@code whale-common} 提供并与其他服务共享，不属于本服务持久化实体，
 * 因此不在本规则的约束范围（规则只限定 {@code com.bluewhale.audit} 内部包）。</p>
 *
 * <p><b>扫描结论</b>：2026-11-02 全量扫描 2 个 controller、4 个 entity/mapper 文件，
 * 本服务内部越层依赖为 0，四条规则直接开启门禁。规则只扫描生产类
 * （{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.audit", importOptions = ImportOption.DoNotIncludeTests.class)
class AuditArchitectureTest {

    /** 控制器不得直接依赖本服务实体（查询响应一律经 service 装配为 VO） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_entity = noClasses()
            .that().resideInAPackage("com.bluewhale.audit.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.audit.mq.entity..",
                    "com.bluewhale.audit.retention.entity..");

    /** 控制器不得直接依赖 Mapper（持久化访问一律经 service 层） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_mapper = noClasses()
            .that().resideInAPackage("com.bluewhale.audit.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.audit.mq.mapper..",
                    "com.bluewhale.audit.retention.mapper..");

    /** 出入参模型不得直接暴露持久化实体或 Mapper */
    @ArchTest
    static final ArchRule dto_and_vo_must_not_depend_on_persistence = noClasses()
            .that().resideInAnyPackage("com.bluewhale.audit.dto..", "com.bluewhale.audit.vo..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.audit.mq.entity..",
                    "com.bluewhale.audit.retention.entity..",
                    "com.bluewhale.audit.mq.mapper..",
                    "com.bluewhale.audit.retention.mapper..");

    /** Mapper 只依赖实体与框架，不得反向依赖控制器或业务服务 */
    @ArchTest
    static final ArchRule mapper_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.audit.mq.mapper..",
                    "com.bluewhale.audit.retention.mapper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.audit.controller..",
                    "com.bluewhale.audit.service..",
                    "com.bluewhale.audit.mq.service..");

    /** 实体为纯持久化模型，不得依赖控制器、业务服务或 Mapper */
    @ArchTest
    static final ArchRule entity_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAnyPackage(
                    "com.bluewhale.audit.mq.entity..",
                    "com.bluewhale.audit.retention.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.audit.controller..",
                    "com.bluewhale.audit.service..",
                    "com.bluewhale.audit.mq.service..",
                    "com.bluewhale.audit.mq.mapper..",
                    "com.bluewhale.audit.retention.mapper..");
}
