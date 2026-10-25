package com.bluewhale.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.common.tenant.TenantSqlProperties;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * MyBatis-Plus 通用配置：mapper 扫描、租户行级插件、分页插件、公共字段自动填充
 *
 * @author BlueWhale
 */
@AutoConfiguration
@Configuration
@MapperScan("com.bluewhale.**.mapper")
@EnableConfigurationProperties(TenantSqlProperties.class)
public class MybatisPlusConfig {

    /**
     * 租户列名（与 Phase 1 的 RBAC 表及日志表保持一致）
     */
    private static final String TENANT_COLUMN = "tenant_id";

    /**
     * 分页与租户插件：租户插件须先于分页插件注册（官方推荐顺序）
     *
     * @param tenantSqlProperties 租户 SQL 隔离配置
     * @return MyBatis-Plus 插件集合
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantSqlProperties tenantSqlProperties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (tenantSqlProperties.isEnabled()) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(
                    new ConfiguredTenantLineHandler(tenantSqlProperties.getTables())));
        }
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 公共字段自动填充：insert 填充 createTime/updateTime，update 填充 updateTime
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }

    /**
     * 基于显式表清单的租户处理器。
     *
     * <p>未配置的表与无租户上下文的线程（启动期/未传播的后台任务）一律忽略，
     * 避免对尚未铺开 {@code tenant_id} 的业务表生成非法 SQL。</p>
     *
     * @author BlueWhale
     */
    private static final class ConfiguredTenantLineHandler implements TenantLineHandler {

        private final List<String> tables;

        /**
         * 构造租户处理器
         *
         * @param tables 需要自动追加租户条件的表名（大小写不敏感）
         */
        private ConfiguredTenantLineHandler(List<String> tables) {
            this.tables = tables == null ? List.of() : tables.stream()
                    .map(table -> table.toLowerCase(Locale.ROOT)).toList();
        }

        @Override
        public Expression getTenantId() {
            Long tenantId = TenantContext.get();
            return new LongValue(tenantId == null ? TenantContext.UNKNOWN_TENANT_ID : tenantId);
        }

        @Override
        public String getTenantIdColumn() {
            return TENANT_COLUMN;
        }

        @Override
        public boolean ignoreTable(String tableName) {
            if (tableName == null || TenantContext.get() == null) {
                return true;
            }
            return !tables.contains(tableName.toLowerCase(Locale.ROOT));
        }
    }
}
