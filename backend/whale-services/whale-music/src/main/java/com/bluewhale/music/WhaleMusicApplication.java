package com.bluewhale.music;

import com.bluewhale.music.config.MusicProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 音乐平台服务启动类。
 *
 * <p>M0 提供模块骨架与运行元信息接口；M1 起按计划逐步接入曲库、版权协议、
 * 播放票据与 Range 流式、歌单、发现与评论。音频与封面字节统一由
 * {@code whale-file} 托管，本服务只保存 {@code file_id} 与元数据。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties(MusicProperties.class)
public class WhaleMusicApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleMusicApplication.class, args);
    }
}
