package com.bluewhale.i18n;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.i18n.admin.I18nLocalMessageController;
import com.bluewhale.i18n.admin.I18nLocalMessageService;
import com.bluewhale.i18n.admin.impl.I18nLocalMessageServiceImpl;
import com.bluewhale.i18n.mapper.SysI18nMessageMapper;
import com.bluewhale.i18n.spi.DbI18nDatabaseProvider;
import com.bluewhale.i18n.spi.I18nDatabaseProvider;
import com.bluewhale.i18n.support.I18nModuleScanner;
import com.bluewhale.i18n.support.I18nSchemaInitializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * whale-i18n 自动装配
 *
 * <p>装配条件矩阵：
 * <ul>
 *   <li>whale.i18n.enabled=false → 整个 SDK 不装配（默认 enabled=true）</li>
 *   <li>无 DataSource / 无 MyBatis → 不装配数据库 Provider，退化为属性文件级（启动日志提示）</li>
 *   <li>有 DataSource + MyBatis → 装配 DbI18nDatabaseProvider（应用已自定义 I18nDatabaseProvider 时让位）</li>
 *   <li>whale.i18n.auto-init=true 且有 DataSource → 启动时幂等创建 sys_i18n_message</li>
 *   <li>whale.i18n.admin.enabled=true 且有 Mapper → 装配服务本地文案 CRUD 接口（默认关闭）</li>
 *   <li>无 StringRedisTemplate → 不启用缓存，每次直查数据库</li>
 *   <li>Redis 运行时断连 → 降级直查数据库，告警日志限流（每分钟至多一条）</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Slf4j
@AutoConfiguration
@AutoConfigureBefore(MessageSourceAutoConfiguration.class)
@AutoConfigureAfter(name = {
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "com.bluewhale.common.mybatis.config.MybatisPlusConfig"
})
@ConditionalOnProperty(prefix = "whale.i18n", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WhaleI18nProperties.class)
public class WhaleI18nAutoConfiguration {

    /**
     * 属性文件级 MessageSource（第二级回退源）
     *
     * <p>basename 由「各 jar 的 i18n/&lt;模块&gt;/messages」扫描结果 + 全局兜底 basename 组成，
     * 使每个服务可自带属性文件而无需集中维护。</p>
     *
     * @param properties i18n 配置
     * @return 属性文件消息源
     */
    @Bean
    @ConditionalOnMissingBean(ReloadableResourceBundleMessageSource.class)
    public ReloadableResourceBundleMessageSource i18nPropertiesMessageSource(WhaleI18nProperties properties) {
        I18nModuleScanner scanner = new I18nModuleScanner(properties.getModuleScanPattern(),
                properties.getBasename(), properties.isModuleScanEnabled());
        List<String> basenames = scanner.scan();
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(basenames.toArray(new String[0]));
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return messageSource;
    }

    /**
     * 三级回退 MessageSource（数据库 > 属性文件 > 默认值），注册为主 MessageSource，
     * 使 Spring Boot 默认的 MessageSourceAutoConfiguration 退位
     *
     * @param properties                  i18n 配置
     * @param i18nPropertiesMessageSource 属性文件消息源
     * @param databaseProvider            数据库文案提供者
     * @param redisTemplate               Redis 模板
     * @return 三级回退消息源
     */
    @Bean
    @Primary
    public MessageSource messageSource(WhaleI18nProperties properties,
                                       ReloadableResourceBundleMessageSource i18nPropertiesMessageSource,
                                       ObjectProvider<I18nDatabaseProvider> databaseProvider,
                                       ObjectProvider<StringRedisTemplate> redisTemplate) {
        I18nDatabaseProvider dbProvider = databaseProvider.getIfAvailable();
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (dbProvider == null) {
            log.info("[whale-i18n] 未检测到数据源或 I18nDatabaseProvider 实现，i18n 退化为属性文件级");
        } else if (redis == null) {
            log.info("[whale-i18n] 未检测到 StringRedisTemplate，i18n 缓存关闭，将直查数据库");
        }
        return new WhaleMessageSource(properties, i18nPropertiesMessageSource, dbProvider, redis);
    }

    /**
     * 缓存清理助手
     *
     * @param properties    i18n 配置
     * @param redisTemplate Redis 模板
     * @return 缓存清理助手
     */
    @Bean
    @ConditionalOnMissingBean
    public I18nCacheEvictHelper i18nCacheEvictHelper(WhaleI18nProperties properties,
                                                     ObjectProvider<StringRedisTemplate> redisTemplate) {
        return new I18nCacheEvictHelper(properties, redisTemplate.getIfAvailable());
    }

    /**
     * 数据库级 Provider 装配（仅当类路径存在 MyBatis 且容器中存在 SqlSessionFactory 时生效）。
     *
     * <p><b>不在本类注册 SysI18nMessageMapper</b>：接入方（whale-spring-boot-starter 的
     * {@code MybatisPlusConfig}）以 {@code @MapperScan("com.bluewhale.**.mapper")} 已统一扫描本 SDK 的
     * {@code com.bluewhale.i18n.mapper} 包，重复注册会触发
     * {@code ConflictingBeanDefinitionException}（同名同类的 Mapper Bean）。</p>
     *
     * <p><b>Mapper 必须用 {@link ObjectProvider} 在实例化期延迟解析</b>：{@code @ConditionalOnBean}
     * 在配置类解析期求值，早于 {@code MapperScannerConfigurer} 注册 Mapper Bean 定义，条件恒不成立。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({BaseMapper.class, SqlSessionFactory.class, MapperFactoryBean.class})
    static class WhaleI18nDbConfig {

        /**
         * 默认数据库 Provider：无数据源或无 Mapper 时不装配（SDK 优雅退化到属性文件级）
         *
         * @param mapperProvider 延迟解析的消息 Mapper
         * @return 数据库文案提供者；mapper 不可用时返回 null（不装配）
         */
        @Bean
        @ConditionalOnBean(DataSource.class)
        @ConditionalOnMissingBean(I18nDatabaseProvider.class)
        public I18nDatabaseProvider dbI18nDatabaseProvider(ObjectProvider<SysI18nMessageMapper> mapperProvider) {
            SysI18nMessageMapper mapper = mapperProvider.getIfAvailable();
            if (mapper == null) {
                log.info("[whale-i18n] 未检测到 SysI18nMessageMapper，i18n 数据库级装配跳过");
                return null;
            }
            log.info("[whale-i18n] 装配默认数据库 I18nDatabaseProvider（sys_i18n_message）");
            return new DbI18nDatabaseProvider(mapper);
        }
    }

    /**
     * 启动自动建表装配：每个服务在自己的数据源上幂等创建 sys_i18n_message
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "whale.i18n", name = "auto-init", havingValue = "true", matchIfMissing = true)
    static class WhaleI18nSchemaConfig {

        /**
         * 国际化表初始化器
         *
         * @param dataSource 服务数据源
         * @param properties i18n 配置
         * @return 初始化器
         */
        @Bean
        @ConditionalOnMissingBean(I18nSchemaInitializer.class)
        public I18nSchemaInitializer i18nSchemaInitializer(DataSource dataSource, WhaleI18nProperties properties) {
            Resource ddl = new DefaultResourceLoader().getResource(properties.getDdlLocation());
            log.info("[whale-i18n] 启用启动自动建表：{}", properties.getDdlLocation());
            return new I18nSchemaInitializer(dataSource, ddl);
        }
    }

    /**
     * 本地文案管理接口装配（默认关闭，由服务显式开启）
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "whale.i18n.admin", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = {
            "org.springframework.web.bind.annotation.RestController",
            "com.bluewhale.common.core.result.Result",
            "com.bluewhale.auth.sdk.annotation.RequirePermission"
    })
    static class WhaleI18nAdminConfig {

        /**
         * 本地文案管理服务（Mapper 在实例化期经 {@link ObjectProvider} 延迟解析，
         * 规避 {@code @ConditionalOnBean} 早于 {@code @MapperScan} 的时序陷阱）
         *
         * @param mapperProvider 延迟解析的消息 Mapper
         * @param evictHelper    缓存清理助手
         * @return 本地文案管理服务；mapper 不可用时返回 null（不装配）
         */
        @Bean
        @ConditionalOnMissingBean
        public I18nLocalMessageService i18nLocalMessageService(ObjectProvider<SysI18nMessageMapper> mapperProvider,
                                                               I18nCacheEvictHelper evictHelper) {
            SysI18nMessageMapper mapper = mapperProvider.getIfAvailable();
            if (mapper == null) {
                log.warn("[whale-i18n] 未检测到 SysI18nMessageMapper，本地文案管理接口不装配");
                return null;
            }
            log.info("[whale-i18n] 装配服务本地文案管理接口（sys:i18n:* 权限保护）");
            return new I18nLocalMessageServiceImpl(mapper, evictHelper);
        }

        /**
         * 本地文案管理控制器
         *
         * @param service 本地文案管理服务
         * @return 控制器
         */
        @Bean
        @ConditionalOnBean(I18nLocalMessageService.class)
        @ConditionalOnMissingBean(I18nLocalMessageController.class)
        public I18nLocalMessageController i18nLocalMessageController(I18nLocalMessageService service) {
            return new I18nLocalMessageController(service);
        }
    }
}
