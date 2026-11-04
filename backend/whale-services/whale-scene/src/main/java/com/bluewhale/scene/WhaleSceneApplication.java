package com.bluewhale.scene;

import com.bluewhale.scene.config.SceneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 城池场景导航服务启动类（Wave 5.77）。
 *
 * <p>一个服务承载「场景（城池）/ 节点树 / 入口绑定 / 可见性 / 布局版本」五类模型，
 * 对外提供管理接口与 {@code /scene/navigation} 只读导航接口。M1/M2 以 2D 为主生产力
 * 界面；3D 为 M3 可选增强层，由前端渲染层实现，本服务只保存布局坐标与入口元数据，
 * 不引入后端场景渲染能力。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(SceneProperties.class)
public class WhaleSceneApplication {

    /**
     * 应用启动入口。
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleSceneApplication.class, args);
    }
}
