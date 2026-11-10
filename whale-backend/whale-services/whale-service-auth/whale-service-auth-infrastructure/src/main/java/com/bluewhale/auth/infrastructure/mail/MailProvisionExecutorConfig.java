package com.bluewhale.auth.infrastructure.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 邮箱代开线程池。
 *
 * <p><b>为什么要独立线程池</b>：建用户是高频且必须快的操作，而开邮箱要跨服务调 Mailcow，
 * 一次可能等数秒。放主线程会把「新建员工账号」的响应时间绑到邮件服务的健康度上。</p>
 *
 * <p><b>为什么队列有界</b>：批量导入员工时任务会瞬间堆积。无界队列的后果是内存涨到 OOM，
 * 有界队列 + 拒绝策略至少能保住主流程，并把「漏开邮箱」暴露在日志里。</p>
 *
 * @author BlueWhale
 */
@Configuration
public class MailProvisionExecutorConfig {

    /** 线程池核心线程数：开通是低频 IO，1 个线程足够 */
    private static final int CORE_POOL_SIZE = 1;

    /** 最大线程数 */
    private static final int MAX_POOL_SIZE = 2;

    /** 队列容量：批量导入 500 人以内不会丢任务 */
    private static final int QUEUE_CAPACITY = 500;

    /**
     * 邮箱代开线程池
     *
     * @return 线程池执行器
     */
    @Bean(name = "mailProvisionExecutor")
    public ThreadPoolTaskExecutor mailProvisionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("mail-provision-");
        // 拒绝而非在调用线程里跑：宁可记一条「未开通」日志，也不要让建用户接口卡住
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
