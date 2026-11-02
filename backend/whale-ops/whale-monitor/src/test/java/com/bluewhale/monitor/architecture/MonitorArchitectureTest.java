package com.bluewhale.monitor.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 监控服务分层依赖守护（T-18 剩余服务越层专项）。
 *
 * <p>本服务为纯查询型服务（{@code controller / service / vo}），当前<b>没有</@b>
 * 持久化实体与 Mapper（数据来自 Nacos 注册中心与 actuator 探测），因此越层规则
 * 以「接口层 ↔ 服务层」边界为准，并为未来可能引入的持久化层预留门禁：</p>
 *
 * <ul>
 *   <li>控制器不得直接依赖服务实现（{@code service.impl}），必须面向接口编程；</li>
 *   <li>出入参模型不得依赖控制器或服务层；</li>
 *   <li>预留：控制器不得依赖 {@code entity/mapper}（当前为空包，未来落地即生效）。</li>
 * </ul>
 *
 * <p><b>扫描结论</b>：2026-11-02 全量扫描 1 个 controller、0 个 entity/mapper 文件，
 * 未发现越层依赖。规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），
 * 避免测试替身触发误报。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.monitor", importOptions = ImportOption.DoNotIncludeTests.class)
class MonitorArchitectureTest {

    /** 控制器必须面向服务接口编程，不得直接依赖服务实现 */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_service_impl = noClasses()
            .that().resideInAPackage("com.bluewhale.monitor.controller..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.monitor.service.impl..");

    /** 控制器不得直接依赖持久化层（当前无实体/Mapper，作为未来新增时的门禁） */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_persistence = noClasses()
            .that().resideInAPackage("com.bluewhale.monitor.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.monitor.entity..",
                    "com.bluewhale.monitor.mapper..");

    /** 出入参模型不得依赖控制器或业务服务层 */
    @ArchTest
    static final ArchRule vo_must_not_depend_on_controller_or_service = noClasses()
            .that().resideInAPackage("com.bluewhale.monitor.vo..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.monitor.controller..",
                    "com.bluewhale.monitor.service..");
}
