package com.bluewhale.job.scheduler;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 执行器注册表：按 Bean 名称解析 {@link JobHandler}。
 *
 * <p>集中处理器解析与存在性判定，供调度器执行时解析、供服务层在创建/更新任务时校验
 * {@code handler_bean} 是否指向容器中真实存在的执行器。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class JobHandlerRegistry {

    private final ApplicationContext applicationContext;

    /**
     * 按 Bean 名称解析执行器。
     *
     * @param beanName 执行器 Bean 名称
     * @return 执行器实例
     * @throws BizException Bean 名称为空、容器中不存在或未实现 {@link JobHandler} 时抛出 {@code PARAM_ERROR}
     */
    public JobHandler resolve(String beanName) {
        if (!exists(beanName)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return (JobHandler) applicationContext.getBean(beanName);
    }

    /**
     * 判断指定 Bean 名称是否为已注册的合法执行器。
     *
     * @param beanName 执行器 Bean 名称
     * @return 存在且实现 {@link JobHandler} 返回 {@code true}
     */
    public boolean exists(String beanName) {
        if (!StringUtils.hasText(beanName) || !applicationContext.containsBean(beanName)) {
            return false;
        }
        return applicationContext.getBean(beanName) instanceof JobHandler;
    }
}
