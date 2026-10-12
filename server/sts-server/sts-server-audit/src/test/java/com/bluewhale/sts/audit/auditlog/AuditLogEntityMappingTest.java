package com.bluewhale.sts.audit.auditlog;

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
 * 审计实体到表结构的映射约束。
 *
 * <p><b>为什么要对"生成的 SQL"本身做断言，而不是跑一次查询：</b>
 * 本仓在这件事上吃过两次亏，都是只在 MySQL 上暴露、H2 上完全看不出来的那种：
 * <ol>
 *   <li>列名直接撞 MySQL 保留字（{@code release} → ERROR 1064）；</li>
 *   <li>把列名改成 {@code app_release} 后，MyBatis-Plus 又因为"列名与属性名不一致"
 *       自动补了 {@code app_release AS release} —— <b>别名是隐式的</b>，
 *       只看实体源码根本看不出来，而别名把保留字原样带了回来，照样 1064。</li>
 * </ol>
 * 审计表用了 {@code action} / {@code resource} / {@code result} 三个在别的库里
 * 属于"非保留关键字"的列名，风险等级与当年那个 {@code release} 类似，
 * 因此这里把"不产生任何别名"这条不变量钉死 —— 只要有人给某个字段加回
 * {@code @TableField("别的列名")}，别名就会重新出现，测试立刻变红。
 */
class AuditLogEntityMappingTest {

    /**
     * 按运行期的实际配置构造 TableInfo。
     *
     * <p>关键是 {@code setMapUnderscoreToCamelCase(true)} —— 与
     * {@code application.yml} 里的 {@code mybatis-plus.configuration.map-underscore-to-camel-case}
     * 一致。列名如何从属性名推导取决于这个开关，测的时候必须对齐，
     * 否则测的是一个与实际运行不同名的环境。
     */
    private static TableInfo tableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);

        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(AuditLogEntity.class.getName());
        return TableInfoHelper.initTableInfo(assistant, AuditLogEntity.class);
    }

    @Test
    @DisplayName("表名与列名：属性名与列名同构，全部由下划线转驼峰推导")
    void shouldMapEveryColumnByConvention() {
        String sqlSelect = tableInfo().getAllSqlSelect();

        // 逐个钉住最容易出问题的那几个：它们的列名与 Java 属性名之间
        // 一旦出现"人工指定"，别名就会跟着出现
        assertThat(sqlSelect).contains("action");
        assertThat(sqlSelect).contains("resource");
        assertThat(sqlSelect).contains("result");
        assertThat(sqlSelect).contains("target_id");
        assertThat(sqlSelect).contains("operator_name");
        assertThat(sqlSelect).contains("error_code");
        assertThat(sqlSelect).contains("request_uri");
        assertThat(sqlSelect).contains("trace_id");
        assertThat(sqlSelect).contains("duration_ms");
        assertThat(sqlSelect).contains("server_time");
    }

    @Test
    @DisplayName("选择列表不产生任何别名 —— 别名会把保留字隐式带回来")
    void shouldNotGenerateAnyAlias() {
        String sqlSelect = tableInfo().getAllSqlSelect();

        // 这条断言失败时的含义：有人给某个字段加了 @TableField("自定义列名")。
        // 那样列名虽然能对上，但 MP 会补 `列名 AS 属性名`，而别名一旦撞上
        // 某个数据库的保留字，就复现了当年 release 的 1064 ——
        // 而且错误只在真实库上出现，H2 一切正常。
        // 正确做法是让属性名与列名同构（targetId → target_id），不产生别名。
        assertThat(sqlSelect)
                .as("审计表的列名必须由属性名推导得出，不得出现人工指定的列名（会产生隐式别名）")
                .doesNotContain(" AS ");
    }

    @Test
    @DisplayName("实体不含任何自动填充字段：审计的字段全部由拦截器一次填好")
    void shouldNotDeclareAutoFillColumns() {
        TableInfo info = tableInfo();

        // 本模块刻意没有 MetaObjectHandler（见 AuditRecorder 的注释）。
        // 若将来有人给实体加上 createTime / updateTime 这类 @TableField(fill = ...)，
        // 说明审计域被套用了业务表的那一套约定 —— 那是需要重新讨论的架构变化，
        // 而不是一次顺手补字段。这条断言让那次讨论必须发生。
        assertThat(info.getFieldList())
                .as("审计实体不应含自动填充字段")
                .noneMatch(field -> field.getFieldFill() != null
                        && field.getFieldFill() != com.baomidou.mybatisplus.annotation.FieldFill.DEFAULT);
    }

}
