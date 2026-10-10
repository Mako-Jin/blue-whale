package com.bluewhale.sts.app.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.toolkit.JdbcUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据源 URL 与数据库方言的对应关系。
 *
 * <p><b>这个测试在防什么：</b>{@code DbAutoConfiguration} 里的分页拦截器**刻意不指定
 * {@code DbType}**，交给 MyBatis-Plus 按实际 DataSource 自动判定。这个设计是对的，
 * 但它把正确性押在"URL 能被识别出来"这一件事上 —— 而识别失败的表现极不友好：
 * 分页拦截器会**静默跳过**，{@code Page} 查询退化成全量查询，不报错、只在数据量
 * 上来之后表现为"越来越慢"，且慢得没有明显触发条件。
 *
 * <p>所以这里把三个 profile 的 URL 都钉住：它们一旦被改坏（改协议名、加错前缀），
 * 测试立刻红，而不是等到生产环境某张表长到几十万行才被发现。
 *
 * <p><b>本测试的边界：</b>它只证明 URL 能被识别成正确的 {@code DbType}，
 * 即"MyBatis-Plus 会选择达梦方言"。它**不能**证明达梦方言生成的 SQL 在真实实例上
 * 执行正确 —— 那需要连上真实数据库，属于 docs/decisions/0010 里"未验证清单"的范围。
 */
class DatasourceDialectTest {

    /**
     * 从各 profile 的 yml 里读数据源 URL，而不是在测试里另写一份。
     *
     * <p>另写一份的话，配置改了测试不会跟着红 —— 那就成了"测的是测试自己"，
     * 恰好丧失了这个测试的全部意义。
     */
    @SuppressWarnings("unchecked")
    private static String datasourceUrl(String profile) throws Exception {
        try (InputStream in = new ClassPathResource("application-" + profile + ".yml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
            return (String) datasource.get("url");
        }
    }

    @Test
    @DisplayName("三个 profile 的数据源 URL 都能被识别出方言")
    void shouldResolveDialectForEveryProfile() throws Exception {
        assertThat(JdbcUtils.getDbType(datasourceUrl("h2")))
                .as("h2 profile（默认）")
                .isEqualTo(DbType.H2);

        assertThat(JdbcUtils.getDbType(datasourceUrl("mysql")))
                .as("mysql profile")
                .isEqualTo(DbType.MYSQL);

        assertThat(JdbcUtils.getDbType(datasourceUrl("dameng")))
                .as("dameng profile")
                .isEqualTo(DbType.DM);
    }

    @Test
    @DisplayName("达梦 URL 必须解析成 DbType.DM：识别不出会让分页静默退化为全量查询")
    void damengUrlMustResolveToDmDialect() throws Exception {
        String url = datasourceUrl("dameng");

        // MyBatis-Plus 的匹配规则是正则 ":dm\d*:"，所以 jdbc:dm:// 与 jdbc:dm8:// 都能命中。
        // 但若有人把协议名改成 jdbc:dameng:// 之类，就会落到"识别不出"分支 ——
        // 那时 MyBatis-Plus 不报错，只是不再为达梦生成分页 SQL。
        assertThat(JdbcUtils.getDbType(url))
                .as("达梦 URL 未被识别为 DbType.DM，分页拦截器会静默失效")
                .isEqualTo(DbType.DM);
    }

}
