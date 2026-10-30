package com.bluewhale.doc.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 文档服务分层依赖守护（T-18 扩展）。
 *
 * <p>本服务采用单模块分包结构（{@code application / config / domain / infrastructure /
 * interfaces / scheduler / support / ws}）。当前真实代码的依赖方向为：
 * {@code interfaces(controller) → application → infrastructure(persistence/client)}，
 * 且 application 与 interfaces.dto/vo 双向耦合（服务层直接收发 DTO/VO）。
 * 因此本测试只固化「确定成立且不应被破坏」的反向依赖，未把尚未达成的方向写成规则：</p>
 *
 * <ul>
 *   <li>已知越层点（留待后续治理，未写成规则）：application → infrastructure 与
 *       application → interfaces.dto/vo；interfaces.dto → domain.model；
 *       ws → domain.exception；infrastructure.mapper → interfaces.vo；
 *       config → ws；support → infrastructure/domain。</li>
 * </ul>
 *
 * <p>规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.doc", importOptions = ImportOption.DoNotIncludeTests.class)
class DocArchitectureTest {

    /** 领域层不得依赖任何外层分包 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.doc.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.doc.application..",
                    "com.bluewhale.doc.config..",
                    "com.bluewhale.doc.infrastructure..",
                    "com.bluewhale.doc.interfaces..",
                    "com.bluewhale.doc.scheduler..",
                    "com.bluewhale.doc.support..",
                    "com.bluewhale.doc.ws..");

    /** 领域层不得被 Spring / MyBatis / Jakarta 等框架侵蚀 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks = noClasses()
            .that().resideInAPackage("com.bluewhale.doc.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "org.apache.ibatis..",
                    "com.baomidou..",
                    "jakarta..");

    /** 控制器不得越过应用层直接依赖持久化/领域细节 */
    @ArchTest
    static final ArchRule controller_must_not_depend_on_infrastructure_or_domain = noClasses()
            .that().resideInAPackage("com.bluewhale.doc.interfaces.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.doc.infrastructure..",
                    "com.bluewhale.doc.domain..");

    /** 出入参模型不得依赖持久化实体、Mapper 或查询行对象 */
    @ArchTest
    static final ArchRule dto_and_vo_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAnyPackage("com.bluewhale.doc.interfaces.dto..", "com.bluewhale.doc.interfaces.vo..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.doc.infrastructure..");

    /** 实时协作接入层不得依赖持久化/外部客户端实现 */
    @ArchTest
    static final ArchRule ws_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.doc.ws..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.doc.infrastructure..");

    /** 应用层不得反向依赖控制器或 WebSocket 接入层 */
    @ArchTest
    static final ArchRule application_must_not_depend_on_controller_or_ws = noClasses()
            .that().resideInAPackage("com.bluewhale.doc.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.doc.interfaces.controller..",
                    "com.bluewhale.doc.ws..");

    /** 持久化与外部客户端不得反向依赖控制器或 WebSocket 接入层 */
    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_controller_or_ws = noClasses()
            .that().resideInAPackage("com.bluewhale.doc.infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.doc.interfaces.controller..",
                    "com.bluewhale.doc.ws..");

    /** 定时任务只可编排应用服务，不得依赖接口层、协作接入层或领域模型 */
    @ArchTest
    static final ArchRule scheduler_must_not_depend_on_interfaces_ws_or_domain = noClasses()
            .that().resideInAPackage("com.bluewhale.doc.scheduler..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.doc.interfaces..",
                    "com.bluewhale.doc.ws..",
                    "com.bluewhale.doc.domain..");
}
