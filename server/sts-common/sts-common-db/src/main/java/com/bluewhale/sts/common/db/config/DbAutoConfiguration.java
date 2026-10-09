package com.bluewhale.sts.common.db.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 数据访问公共能力的自动装配入口。
 *
 * <p>被 {@code AutoConfiguration.imports} 登记，依赖 {@code sts-common-db} 的服务模块
 * 无需自己声明拦截器 —— 少写一遍，也就少漏一遍。
 *
 * <p><b>条件装配的必要性：</b>{@link ConditionalOnClass} 保证本模块在没有 MyBatis-Plus
 * 的环境（例如纯 Web 的测试切片）下安静地不生效，而不是抛 {@code NoClassDefFoundError}。
 */
@AutoConfiguration
@ConditionalOnClass({SqlSessionFactory.class, MybatisPlusInterceptor.class})
@ConditionalOnSingleCandidate(DataSource.class)
public class DbAutoConfiguration {

    /**
     * 注册 MyBatis-Plus 拦截器链。
     *
     * <p>三个内拦截器各自守住一类事故：
     * <ul>
     *   <li>{@link PaginationInnerInterceptor} —— 分页。不注册它，{@code Page} 查询会
     *       静默退化成全量查询（不报错、只变慢），是最容易被忽略的一处。</li>
     *   <li>{@link OptimisticLockerInnerInterceptor} —— 乐观锁，{@code @Version} 字段
     *       自动参与更新条件，避免并发覆盖。</li>
     *   <li>{@link BlockAttackInnerInterceptor} —— 拦截没有 where 条件的 update/delete，
     *       挡住"一次误操作清空全表"。</li>
     * </ul>
     *
     * <p>分页拦截器**不指定 {@code DbType}**：交给 MyBatis-Plus 按实际 DataSource 自动判定。
     * 写死 DbType 会让测试用内存库、生产用 MySQL 的组合同上线才暴露方言错误。
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }

}
