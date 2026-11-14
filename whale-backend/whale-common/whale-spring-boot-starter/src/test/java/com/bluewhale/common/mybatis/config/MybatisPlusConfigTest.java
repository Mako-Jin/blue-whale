package com.bluewhale.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.tenant.TenantSqlProperties;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MybatisPlusConfig} 单元测试：插件装配顺序、租户处理器语义、公共字段自动填充。
 *
 * @author BlueWhale
 */
class MybatisPlusConfigTest {

    private final MybatisPlusConfig config = new MybatisPlusConfig();

    /**
     * 注册填充实体元信息：{@code strictInsertFill} 依赖 TableInfo 判断字段是否声明了填充策略，
     * 未注册时直接 NPE（Cannot invoke "TableInfo.isWithInsertFill()" because "tableInfo" is null）。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AuditPojo.class);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("租户插件启用：顺序为 租户行级 → 分页 → 乐观锁")
    void shouldRegisterThreeInterceptorsInOrderWhenTenantEnabled() {
        TenantSqlProperties tenantSql = new TenantSqlProperties();
        tenantSql.setEnabled(true);

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(tenantSql, new DatabaseProperties());
        List<InnerInterceptor> inners = interceptor.getInterceptors();

        assertThat(inners).hasSize(3);
        assertThat(inners.get(0)).isInstanceOf(TenantLineInnerInterceptor.class);
        assertThat(inners.get(1)).isInstanceOf(PaginationInnerInterceptor.class);
        assertThat(inners.get(2)).isInstanceOf(OptimisticLockerInnerInterceptor.class);
    }

    @Test
    @DisplayName("租户插件关闭：只注册 分页 → 乐观锁")
    void shouldRegisterTwoInterceptorsWhenTenantDisabled() {
        TenantSqlProperties tenantSql = new TenantSqlProperties();
        tenantSql.setEnabled(false);

        List<InnerInterceptor> inners = config.mybatisPlusInterceptor(tenantSql, new DatabaseProperties())
                .getInterceptors();

        assertThat(inners).hasSize(2);
        assertThat(inners.get(0)).isInstanceOf(PaginationInnerInterceptor.class);
        assertThat(inners.get(1)).isInstanceOf(OptimisticLockerInnerInterceptor.class);
    }

    @Test
    @DisplayName("租户处理器：列名为 tenant_id，无上下文用 UNKNOWN_TENANT_ID，有上下文用实际租户")
    void shouldResolveTenantIdFromContext() {
        TenantLineHandler handler = tenantHandler(List.of("sys_tenant"));

        assertThat(handler.getTenantIdColumn()).isEqualTo("tenant_id");

        TenantContext.clear();
        assertThat(((LongValue) handler.getTenantId()).getValue())
                .isEqualTo(TenantContext.UNKNOWN_TENANT_ID);

        TenantContext.set(1001L);
        assertThat(((LongValue) handler.getTenantId()).getValue()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("租户处理器忽略规则：无上下文/空表名一律忽略，白名单大小写不敏感")
    void shouldIgnoreTableByContextAndWhitelist() {
        TenantLineHandler handler = tenantHandler(List.of("SYS_TENANT"));

        TenantContext.clear();
        assertThat(handler.ignoreTable(null)).isTrue();
        assertThat(handler.ignoreTable("biz_order")).isTrue();

        TenantContext.set(1001L);
        assertThat(handler.ignoreTable(null)).isTrue();
        assertThat(handler.ignoreTable("SYS_TENANT")).isTrue();
        assertThat(handler.ignoreTable("Sys_Tenant")).isTrue();
        assertThat(handler.ignoreTable("biz_order")).isFalse();
    }

    @Test
    @DisplayName("租户处理器：忽略表清单为空列表时不 NPE，未命中的表不忽略")
    void shouldHandleEmptyIgnoreTables() {
        TenantLineHandler handler = tenantHandler(List.of());

        TenantContext.set(2L);

        assertThat(handler.ignoreTable("biz_order")).isFalse();
    }

    @Test
    @DisplayName("公共字段填充：insert 同时填 createTime/updateTime，update 只覆盖 updateTime 且不重复赋值")
    void shouldFillAuditFields() {
        MetaObjectHandler handler = config.metaObjectHandler();
        AuditPojo pojo = new AuditPojo();
        MetaObject metaObject = SystemMetaObject.forObject(pojo);

        handler.insertFill(metaObject);

        assertThat(pojo.getCreateTime()).isNotNull();
        assertThat(pojo.getUpdateTime()).isNotNull();

        LocalDateTime created = pojo.getCreateTime();
        LocalDateTime updatedBefore = pojo.getUpdateTime();
        handler.insertFill(metaObject);
        assertThat(pojo.getCreateTime()).isEqualTo(created);
        assertThat(pojo.getUpdateTime()).isEqualTo(updatedBefore);

        pojo.setUpdateTime(null);
        handler.updateFill(metaObject);
        assertThat(pojo.getUpdateTime()).isNotNull();
        assertThat(pojo.getCreateTime()).isEqualTo(created);
    }

    /**
     * 从真实装配出的插件里取出租户处理器
     *
     * @param ignoreTables 忽略表白名单
     * @return 租户处理器
     */
    private TenantLineHandler tenantHandler(List<String> ignoreTables) {
        TenantSqlProperties tenantSql = new TenantSqlProperties();
        tenantSql.setEnabled(true);
        tenantSql.setIgnoreTables(ignoreTables);

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(tenantSql, new DatabaseProperties());
        return ((TenantLineInnerInterceptor) interceptor.getInterceptors().get(0)).getTenantLineHandler();
    }

    /**
     * 仅用于公共字段填充验证的实体桩（需声明填充策略，否则 strictFill 不会写入）
     */
    @TableName("t_audit_pojo")
    static class AuditPojo {

        /** 主键 */
        @TableId(type = IdType.ASSIGN_ID)
        private Long id;

        /** 创建时间 */
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createTime;

        /** 更新时间 */
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updateTime;

        /**
         * 获取主键
         *
         * @return 主键
         */
        public Long getId() {
            return id;
        }

        /**
         * 设置主键
         *
         * @param id 主键
         */
        public void setId(Long id) {
            this.id = id;
        }

        /**
         * 获取创建时间
         *
         * @return 创建时间
         */
        public LocalDateTime getCreateTime() {
            return createTime;
        }

        /**
         * 设置创建时间
         *
         * @param createTime 创建时间
         */
        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        /**
         * 获取更新时间
         *
         * @return 更新时间
         */
        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        /**
         * 设置更新时间
         *
         * @param updateTime 更新时间
         */
        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }
    }
}
