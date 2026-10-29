package com.bluewhale.doc;

import com.bluewhale.doc.config.DocProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 在线文档 / Wiki 服务启动类。
 *
 * <p>M1 提供文档空间、空间成员、页面树、版本历史、附件、评论、搜索与收藏；
 * M2 增加回收站与 Yjs 实时协作（WS 中继 + Redis 跨实例广播）。页面正文以
 * TipTap / ProseMirror JSON 存储，服务端提取纯文本供搜索使用。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties(DocProperties.class)
public class WhaleDocApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleDocApplication.class, args);
    }
}
