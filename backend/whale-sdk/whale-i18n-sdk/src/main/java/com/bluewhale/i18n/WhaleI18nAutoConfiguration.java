package com.bluewhale.i18n;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.i18n.mapper.SysI18nMessageMapper;
import com.bluewhale.i18n.spi.DbI18nDatabaseProvider;
import com.bluewhale.i18n.spi.I18nDatabaseProvider;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * whale-i18n 自动装配
 * <p>
 * 装配条件矩阵：
 * <ul>
 *   <li>whale.i18n.enabled=false → 整个 SDK 不装配（默认 enabled=true）</li>
 *   <li>无 DataSource / 无 MyBatis → 不装配数据库 Provider，退化为属性文件级（启动日志提示）</li>
 *   <li>有 DataSource + MyBatis → 装配 DbI18nDatabaseProvider（应用已自定义 I18nDatabaseProvider 时让位）</li>
 *   <li>无 StringRedisTemplate → 不启用缓存，每次直查数据库</li>
 *   <li>Redis 运行时断连 → 降级直查数据库，告警日志限流（每分钟至多一条）</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Slf4j
@AutoConfiguration
@AutoConfigureBefore(MessageSourceAutoConfiguration.class)
@AutoConfigureAfter(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnProperty(prefix = "whale.i18n", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WhaleI18nProperties.class)
public class WhaleI18nAutoConfiguration {

    /**
     * 属性文件级 MessageSource（第二级回退源）
     */
    @Bean
    @ConditionalOnMissingBean(ReloadableResourceBundleMessageSource.class)
    public ReloadableResourceBundleMessageSource i18nPropertiesMessageSource(WhaleI18nProperties properties) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename(properties.getBasename());
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return messageSource;
    }

    /**
     * 三级回退 MessageSource（数据库 > 属性文件 > 默认值），注册为主 MessageSource，
     * 使 Spring Boot 默认的 MessageSourceAutoConfiguration 退位
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
     */
    @Bean
    @ConditionalOnMissingBean
    public I18nCacheEvictHelper i18nCacheEvictHelper(WhaleI18nProperties properties,
                                                     ObjectProvider<StringRedisTemplate> redisTemplate) {
        return new I18nCacheEvictHelper(properties, redisTemplate.getIfAvailable());
    }

    /**
     * 数据库级 Provider 装配（仅当类路径存在 MyBatis 且容器中存在 SqlSessionFactory 时生效）。
     * 嵌套配置类通过 @ConditionalOnClass 守卫，避免无 MyBatis 环境下的类加载失败。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({BaseMapper.class, SqlSessionFactory.class, MapperFactoryBean.class})
    static class WhaleI18nDbConfig {

        /**
         * 注册 SysI18nMessageMapper（应用自身 @MapperScan 已扫描该接口时自动让位）
         */
        @Bean
        @ConditionalOnBean(SqlSessionFactory.class)
        @ConditionalOnMissingBean(SysI18nMessageMapper.class)
        public MapperFactoryBean<SysI18nMessageMapper> sysI18nMessageMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<SysI18nMessageMapper> factoryBean = new MapperFactoryBean<>(SysI18nMessageMapper.class);
            factoryBean.setSqlSessionFactory(sqlSessionFactory);
            return factoryBean;
        }

        /**
         * 默认数据库 Provider：无数据源时不装配（SDK 优雅退化到属性文件级）
         */
        @Bean
        @ConditionalOnBean({DataSource.class, SqlSessionFactory.class})
        @ConditionalOnMissingBean(I18nDatabaseProvider.class)
        public I18nDatabaseProvider dbI18nDatabaseProvider(SysI18nMessageMapper mapper) {
            log.info("[whale-i18n] 装配默认数据库 I18nDatabaseProvider（sys_i18n_message）");
            return new DbI18nDatabaseProvider(mapper);
        }
    }
}
