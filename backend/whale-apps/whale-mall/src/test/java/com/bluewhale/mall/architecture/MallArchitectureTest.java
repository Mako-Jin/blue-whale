package com.bluewhale.mall.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 商城应用分层依赖守护（DDD 四层 + Ports &amp; Adapters）。
 *
 * <p>依赖方向固化为 {@code interfaces → application → domain}，
 * {@code infrastructure} 实现 domain 端口。规则只扫描生产类，
 * 避免测试替身触发误报。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.mall", importOptions = ImportOption.DoNotIncludeTests.class)
class MallArchitectureTest {

    /** 四个业务上下文 */
    private static final String[] CONTEXTS = {"catalog", "channel", "cart", "order"};

    /** 领域层不得依赖应用层、接口层与基础设施层 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAnyPackage(contextPackages("domain"))
            .should().dependOnClassesThat()
            .resideInAnyPackage(contextPackages("application", "interfaces", "infrastructure"))
            .allowEmptyShould(true);

    /** 领域层不依赖 Spring / MyBatis，保持可独立单测 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks = noClasses()
            .that().resideInAnyPackage(contextPackages("domain"))
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.baomidou..",
                    "jakarta.persistence..")
            .allowEmptyShould(true);

    /** 接口层不得直接依赖持久化实现 */
    @ArchTest
    static final ArchRule interfaces_must_not_depend_on_infrastructure_persistence = noClasses()
            .that().resideInAnyPackage(contextPackages("interfaces"))
            .should().dependOnClassesThat().resideInAnyPackage(contextPackages("infrastructure.persistence"))
            .allowEmptyShould(true);

    /** 应用服务实现必须位于 application.service.impl 包 */
    @ArchTest
    static final ArchRule app_service_impl_should_reside_in_impl = classes()
            .that().haveSimpleNameEndingWith("AppServiceImpl")
            .should().resideInAPackage("..application.service.impl..")
            .allowEmptyShould(true);

    /**
     * 拼接各上下文指定层的包名模式。
     *
     * @param layers 层名
     * @return 包名模式数组
     */
    private static String[] contextPackages(String... layers) {
        List<String> patterns = new ArrayList<>();
        for (String context : CONTEXTS) {
            for (String layer : layers) {
                patterns.add("com.bluewhale.mall." + context + "." + layer + "..");
            }
        }
        return patterns.toArray(String[]::new);
    }
}
