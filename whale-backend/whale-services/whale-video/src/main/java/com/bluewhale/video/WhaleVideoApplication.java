package com.bluewhale.video;

import com.bluewhale.video.config.VideoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 视频平台服务启动类（Wave 5.77）。
 *
 * <p>一个服务承载「短视频 / 视频 / 电影」三条内容线，共享内容注册、播放票据、
 * Range 流式、互动、审核与检索底座。M0 只提供模块骨架与运行元信息接口；
 * M1 起接入共享媒体底座，M2 交付短视频闭环。视频字节统一由 {@code whale-file}
 * 托管，本服务只保存 {@code file_id} 与元数据，公开 VO 不暴露文件 ID。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties(VideoProperties.class)
public class WhaleVideoApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleVideoApplication.class, args);
    }
}
