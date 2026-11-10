package com.bluewhale.video.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 视频服务分层依赖守护。
 *
 * <p>本服务采用单模块四层分包结构（{@code interfaces / application / domain /
 * infrastructure}，与 {@code whale-doc} / {@code whale-music} 同口径）。规则固化
 * 计划约定的依赖方向：{@code interfaces → application → domain}，
 * {@code infrastructure} 只实现领域端口，领域层不依赖任何框架。规则只扫描生产类
 * （{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.video", importOptions = ImportOption.DoNotIncludeTests.class)
class VideoArchitectureTest {

    /** 领域层不得依赖任何外层分包 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.video.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.video.application..",
                    "com.bluewhale.video.config..",
                    "com.bluewhale.video.infrastructure..",
                    "com.bluewhale.video.interfaces..");

    /** 领域层不得被 Spring / MyBatis / Jakarta 等框架侵蚀 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks = noClasses()
            .that().resideInAPackage("com.bluewhale.video.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "org.apache.ibatis..",
                    "com.baomidou..",
                    "jakarta..");

    /** 应用层不得反向依赖接口层或基础设施实现 */
    @ArchTest
    static final ArchRule application_must_not_depend_on_interfaces_or_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.video.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.video.interfaces..",
                    "com.bluewhale.video.infrastructure..");

    /** 接口层不得越过应用层直接依赖领域模型或基础设施 */
    @ArchTest
    static final ArchRule interfaces_must_not_depend_on_domain_or_infrastructure = noClasses()
            .that().resideInAPackage("com.bluewhale.video.interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.video.domain..",
                    "com.bluewhale.video.infrastructure..");

    /** 基础设施层不得反向依赖接口层 */
    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_interfaces = noClasses()
            .that().resideInAPackage("com.bluewhale.video.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("com.bluewhale.video.interfaces..");
}
