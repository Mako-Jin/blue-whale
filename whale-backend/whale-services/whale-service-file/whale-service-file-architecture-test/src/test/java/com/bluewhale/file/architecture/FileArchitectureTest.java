package com.bluewhale.file.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 文件服务分层依赖守护：以 ArchUnit 固化 DDD 依赖方向
 * {@code interfaces → application → domain ← infrastructure}。
 *
 * <p>规则仅扫描生产类（{@link ImportOption.DoNotIncludeTests}），防止后续演进中
 * 领域层被框架/上层反向依赖侵蚀，以及应用层重新直连持久化或文件系统细节。</p>
 *
 * @author BlueWhale
 */
@AnalyzeClasses(packages = "com.bluewhale.file", importOptions = ImportOption.DoNotIncludeTests.class)
class FileArchitectureTest {

    /** 领域层禁止依赖的包：其它文件服务层、插件实现、持久化与 Web 框架 */
    private static final String[] DOMAIN_FORBIDDEN_PACKAGES = {
            "com.bluewhale.file.infrastructure..",
            "com.bluewhale.file.application..",
            "com.bluewhale.file.service..",
            "com.bluewhale.file.dto..",
            "com.bluewhale.file.vo..",
            "com.bluewhale.file.controller..",
            "com.bluewhale.file.entity..",
            "com.bluewhale.file.mapper..",
            "com.bluewhale.file.storage.plugin..",
            "org.springframework..",
            "org.apache.ibatis..",
            "com.baomidou..",
            "jakarta.."
    };

    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks_or_outer_layers = noClasses()
            .that().resideInAPackage("com.bluewhale.file.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(DOMAIN_FORBIDDEN_PACKAGES);

    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure_or_web = noClasses()
            .that().resideInAnyPackage("com.bluewhale.file.service..",
                    "com.bluewhale.file.dto..",
                    "com.bluewhale.file.vo..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.file.infrastructure..",
                    "com.bluewhale.file.entity..",
                    "com.bluewhale.file.mapper..",
                    "com.bluewhale.file.controller..",
                    "com.bluewhale.file.storage.plugin..",
                    "com.baomidou..",
                    "org.apache.ibatis..",
                    "org.springframework.web..",
                    "org.springframework.core.io..",
                    "org.springframework.data.redis..",
                    "org.springframework.security..");

    @ArchTest
    static final ArchRule application_must_not_handle_file_system_details = noClasses()
            .that().resideInAPackage("com.bluewhale.file.service..")
            .should().dependOnClassesThat().resideInAPackage("java.nio.file..");

    @ArchTest
    static final ArchRule interfaces_must_not_depend_on_infrastructure_or_persistence = noClasses()
            .that().resideInAPackage("com.bluewhale.file.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bluewhale.file.infrastructure..",
                    "com.bluewhale.file.entity..",
                    "com.bluewhale.file.mapper..",
                    "com.bluewhale.file.storage.plugin..",
                    "com.baomidou..",
                    "org.apache.ibatis..");
}
