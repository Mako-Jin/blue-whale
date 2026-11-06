package com.bluewhale.party.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 党政工会应用分层依赖守护（DDD 四层 + Ports &amp; Adapters）。
 *
 * <p>Wave 5.88 起 {@code whale-party} 采用六业务上下文（branch / member / activity / fee /
 * content / union）加 integration / common 的模块化单体。依赖方向固化为
 * {@code interfaces → application → domain}，{@code infrastructure} 实现 domain 端口。
 * 规则只扫描生产类（{@link ImportOption.DoNotIncludeTests}），避免测试替身触发误报。</p>
 *
 * <p>M-P0 只有 {@code interfaces} 与 {@code common}、{@code config}，
 * 其余上下文随 M-P1–M-P3 落地；{@code allowEmptyShould(true)} 让规则在上下文为空时也生效，
 * 避免「先写规则、后建包」的波次被门禁挡住。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.party", importOptions = ImportOption.DoNotIncludeTests.class)
class PartyArchitectureTest {

    /** 七个业务上下文（六业务 + integration） */
    private static final String[] CONTEXTS = {
            "branch", "member", "activity", "fee", "content", "union", "integration"
    };

    /** 领域层不得依赖应用层、接口层与基础设施层 */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAnyPackage(contextPackages("domain"))
            .should().dependOnClassesThat().resideInAnyPackage(contextPackages("application", "interfaces", "infrastructure"))
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
     * @param layers 层名（可变参数）
     * @return 包名模式数组
     */
    private static String[] contextPackages(String... layers) {
        java.util.List<String> patterns = new java.util.ArrayList<>();
        for (String context : CONTEXTS) {
            for (String layer : layers) {
                patterns.add("com.bluewhale.party." + context + "." + layer + "..");
            }
        }
        return patterns.toArray(String[]::new);
    }
}
