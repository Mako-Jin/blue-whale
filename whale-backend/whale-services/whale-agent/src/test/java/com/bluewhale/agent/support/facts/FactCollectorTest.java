package com.bluewhale.agent.support.facts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构化事实采集器测试：服务清单、网关路由、DDL、依赖版本。
 *
 * @author BlueWhale
 */
class FactCollectorTest {

    /**
     * 服务清单与网关路由解析。
     *
     * @param root 临时仓库根
     * @throws IOException 写文件失败
     */
    @Test
    @DisplayName("服务清单产出 SERVICE/PORT，网关配置产出 ROUTE")
    void shouldCollectServiceAndRouteFacts(@TempDir Path root) throws IOException {
        write(root.resolve("docs/service-catalog.md"), """
                | 序号 | 服务 | 分组 | 模块 | 端口 | 对外前缀 | 网关目标 | 数据库 | 启动类 | 职责 | 状态 |
                | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
                | 1 | `whale-demo` | whale-services | `whale-services/whale-demo` | 8099 | `/demo` | `lb://whale-demo` | `bluewhale_demo` | `WhaleDemoApplication` | 演示服务 | 已运行 |
                """);
        write(root.resolve("whale-backend/whale-services/whale-gateway/src/main/resources/application.yml"), """
                spring:
                  cloud:
                    gateway:
                      server:
                        webflux:
                          routes:
                            - id: whale-demo
                              uri: lb://whale-demo
                              predicates:
                                - Path=/demo/**
                """);

        ServiceCatalogFactCollector collector = new ServiceCatalogFactCollector();
        List<FactRecord> facts = collector.collect(root);

        FactRecord port = find(facts, "PORT", "whale-demo");
        assertNotNull(port);
        assertEquals(8099, port.value().get("port"));
        assertEquals("docs/service-catalog.md", port.sourcePath());

        FactRecord service = find(facts, "SERVICE", "whale-demo");
        assertNotNull(service);
        assertEquals("bluewhale_demo", service.value().get("database"));

        FactRecord route = find(facts, "ROUTE", "whale-demo");
        assertNotNull(route);
        assertEquals("lb://whale-demo", route.value().get("uri"));
    }

    /**
     * DDL 解析。
     *
     * @param root 临时仓库根
     * @throws IOException 写文件失败
     */
    @Test
    @DisplayName("DDL 产出 TABLE 与 COLUMN 事实")
    void shouldCollectTableAndColumnFacts(@TempDir Path root) throws IOException {
        write(root.resolve("whale-backend/whale-services/whale-demo/src/main/resources/db/init.sql"), """
                CREATE TABLE sys_user (
                    id BIGINT NOT NULL COMMENT '主键ID',
                    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB COMMENT='用户表';
                """);

        List<FactRecord> facts = new DdlFactCollector().collect(root);

        FactRecord table = find(facts, "TABLE", "sys_user");
        assertNotNull(table);
        assertEquals(2, table.value().get("columnCount"));
        assertEquals("用户表", table.value().get("comment"));

        FactRecord column = find(facts, "COLUMN", "sys_user.email");
        assertNotNull(column);
        assertEquals("VARCHAR(128)", column.value().get("type"));
        assertEquals(Boolean.TRUE, column.value().get("nullable"));
    }

    /**
     * 依赖版本解析。
     *
     * @param root 临时仓库根
     * @throws IOException 写文件失败
     */
    @Test
    @DisplayName("POM parent 与依赖、package.json 依赖均产出 DEPENDENCY")
    void shouldCollectDependencyFacts(@TempDir Path root) throws IOException {
        write(root.resolve("whale-backend/whale-demo/pom.xml"), """
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>4.0.8</version>
                    </parent>
                    <artifactId>whale-demo</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                    <properties>
                        <redisson.version>4.8.0</redisson.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.redisson</groupId>
                            <artifactId>redisson-spring-boot-starter</artifactId>
                            <version>${redisson.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>com.bluewhale</groupId>
                            <artifactId>whale-spring-boot-starter</artifactId>
                            <version>${project.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        write(root.resolve("whale-backend/whale-ops/whale-demo-ops/pom.xml"), """
                <project>
                    <parent>
                        <groupId>com.bluewhale</groupId>
                        <artifactId>whale-parent</artifactId>
                        <version>2.0.0-SNAPSHOT</version>
                    </parent>
                    <artifactId>whale-demo-ops</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>com.bluewhale</groupId>
                            <artifactId>whale-inherited-starter</artifactId>
                            <version>${project.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        write(root.resolve("whale-frontend/app/package.json"), """
                {"name": "whale-demo-ui", "dependencies": {"react": "^19.0.0"}}
                """);

        List<FactRecord> facts = new DependencyFactCollector().collect(root);

        FactRecord parent = find(facts, "DEPENDENCY", "org.springframework.boot:spring-boot-starter-parent");
        assertNotNull(parent);
        assertEquals("4.0.8", parent.value().get("version"));

        FactRecord redisson = find(facts, "DEPENDENCY", "org.redisson:redisson-spring-boot-starter");
        assertNotNull(redisson);
        assertEquals("4.8.0", redisson.value().get("version"));

        FactRecord starter = find(facts, "DEPENDENCY", "com.bluewhale:whale-spring-boot-starter");
        assertNotNull(starter);
        assertEquals("1.0.0-SNAPSHOT", starter.value().get("version"));

        FactRecord react = find(facts, "DEPENDENCY", "react");
        assertNotNull(react);
        assertEquals("npm", react.value().get("type"));

        FactRecord inherited = find(facts, "DEPENDENCY", "com.bluewhale:whale-inherited-starter");
        assertNotNull(inherited);
        assertEquals("2.0.0-SNAPSHOT", inherited.value().get("version"));
    }

    /**
     * V-04 性能回归：服务目录与网关路由重建必须稳定在 5 秒内。
     *
     * <p>直接读取本仓 {@code docs/service-catalog.md} 与网关配置，防止后续新增大量
     * 服务/路由后采集器退化。</p>
     */
    @Test
    @DisplayName("服务目录重建 <5s 且事实非空")
    void shouldRebuildServiceCatalogWithinFiveSeconds() {
        Path root = findRepoRoot();
        long start = System.nanoTime();
        List<FactRecord> facts = new ServiceCatalogFactCollector().collect(root);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(facts.isEmpty(), "服务目录事实不应为空");
        assertTrue(elapsedMs < 5000, "服务目录重建耗时 " + elapsedMs + "ms，超过 5s 预算");
    }

    /**
     * 向上查找仓库根（以 {@code docs/service-catalog.md} 为标志）。
     *
     * @return 仓库根
     */
    private static Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("docs/service-catalog.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未找到仓库根：docs/service-catalog.md");
    }

    /**
     * 写入文件（自动创建父目录）。
     *
     * @param file    文件
     * @param content 内容
     * @throws IOException 写文件失败
     */
    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /**
     * 按类型与键查找事实。
     *
     * @param facts 事实列表
     * @param type  事实类型
     * @param key   事实键
     * @return 事实；不存在返回 {@code null}
     */
    private static FactRecord find(List<FactRecord> facts, String type, String key) {
        return facts.stream()
                .filter(fact -> type.equals(fact.factType()) && key.equals(fact.factKey()))
                .findFirst()
                .orElse(null);
    }
}
