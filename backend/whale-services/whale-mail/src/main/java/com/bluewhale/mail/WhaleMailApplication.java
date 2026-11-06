package com.bluewhale.mail;

import com.bluewhale.mail.config.MailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 邮箱服务启动类。
 *
 * <p>邮箱域与消息管控域（{@code whale-message}）严格分离：本服务拥有邮箱账号、文件夹、
 * 邮件索引、附件与草稿；{@code whale-message} 只负责模板、渠道路由与投递记录。
 * 两者共享的只有「一封邮件已发出」这一事实，通过内网事件传递，不共享数据表。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties(MailProperties.class)
public class WhaleMailApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleMailApplication.class, args);
    }
}
