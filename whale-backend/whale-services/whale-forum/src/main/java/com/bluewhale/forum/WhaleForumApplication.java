package com.bluewhale.forum;

import com.bluewhale.forum.config.ForumProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 员工论坛服务启动类（Wave 6.43，鲸议台）。
 *
 * <p>服务端口 9011（8081–8100 已满、8101–8199 归前端子应用段，故借用业务应用段
 * 并在 docs/service-catalog.md §七 登记为例外），对外一级路径前缀 {@code /forum}
 * 经网关 {@code lb://whale-forum} 转发，<b>不剥离前缀</b>（REQ-ARCH-003）。</p>
 *
 * <p>职责边界：版块 / 帖子 / 楼层 / 互动 / 审核与举报 / 热榜 / 检索。附件字节归
 * whale-file，站内信归 whale-message，@提及与接收人检索走 auth 内网通讯录，
 * 可兑换积分账本归 whale-mall，本服务只记论坛经验与流水引用。</p>
 *
 * <p>本类为 M0 骨架：只提供门面接口 {@code GET /forum/meta}（枚举与上限一次性下发），
 * 业务能力按 M1（发帖回帖）/ M2（审核治理）/ M3（检索）/ M4（热榜收口）推进。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(ForumProperties.class)
public class WhaleForumApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleForumApplication.class, args);
    }
}
