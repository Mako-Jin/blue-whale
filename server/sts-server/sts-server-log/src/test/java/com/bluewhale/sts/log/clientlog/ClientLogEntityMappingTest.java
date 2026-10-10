package com.bluewhale.sts.log.clientlog;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实体到表结构的映射约束。
 *
 * <p><b>为什么要单独测"生成的 SQL"而不是跑一次查询：</b>
 * 本项目默认跑在 H2 上，而 H2 <b>不认</b> {@code RELEASE} 是保留字 ——
 * 于是"列名或别名用了 release"这个错误在 H2 上完全查不出来，
 * 所有接口测试都是绿的，直到换成 MySQL 才 1064 报错。这类差异不可能靠
 * 跑通业务用例发现，只能对**生成物**本身做断言。
 *
 * <p>背景：最初 {@code release} 列名踩了两层坑 ——
 * 第一层是列名直接撞 MySQL 保留字；把列名改成 {@code app_release} 后，
 * MyBatis-Plus 又因为"列名与属性名不一致"自动补上 {@code app_release AS release}，
 * 别名把保留字原样带了回来。<b>别名是隐式的</b>，只看实体源码根本看不出来，
 * 所以这里把不变量钉死在测试上。
 */
class ClientLogEntityMappingTest {

    /**
     * 按运行期的实际配置构造 TableInfo。
     *
     * <p>关键是 {@code setMapUnderscoreToCamelCase(true)} —— 与
     * {@code application.yml} 里的 {@code mybatis-plus.configuration.map-underscore-to-camel-case}
     * 一致。列名如何从属性名推导，取决于这个开关，测的时候必须对齐，
     * 否则测的是一个与实际运行不同名的环境。
     */
    private static TableInfo tableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);

        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(ClientLogEntity.class.getName());
        return TableInfoHelper.initTableInfo(assistant, ClientLogEntity.class);
    }

    @Test
    @DisplayName("选择列表用 app_release，而不是 MySQL 保留字 release")
    void shouldSelectAppReleaseColumn() {
        String sqlSelect = tableInfo().getAllSqlSelect();

        assertThat(sqlSelect).contains("app_release");
    }

    @Test
    @DisplayName("不生成 AS release 别名：别名会把保留字原样带回来，MySQL 照样报 1064")
    void shouldNotAliasToReservedWord() {
        String sqlSelect = tableInfo().getAllSqlSelect();

        // 这一条的失败信息值得说清楚：如果它红了，多半是有人给 release 字段
        // 加回了 @TableField("app_release")。那样列名虽然对，但 MP 会补
        // `app_release AS release`，MySQL 在别名上同样拒绝未加引号的保留字。
        // 正确做法是让属性名与列名同构（appRelease → app_release），不产生别名。
        assertThat(sqlSelect)
                .as("选择列表不得出现未加引号的保留字别名（MySQL 会报 1064）")
                .doesNotContain(" AS release");
    }

}
