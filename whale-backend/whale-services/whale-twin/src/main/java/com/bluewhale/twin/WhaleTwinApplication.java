package com.bluewhale.twin;

import com.bluewhale.twin.config.TwinProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 数字孪生平台服务启动类（Wave 6.03）。
 *
 * <p>服务端口 8089，对外一级路径前缀 {@code /twin}（经网关 {@code lb://whale-twin}
 * 转发，不剥离前缀）。</p>
 *
 * <p>职责边界：3D 场景管理（场景/实体/场景树）、孪生实体与数据源绑定（绑到
 * whale-iot 设备或任意 internal 接口）、SSE 实时驱动 3D 呈现、标注/热点、
 * 发布快照与回滚。与 whale-scene（8097，2D 导航底座）互不修改对方表结构，
 * {@code WHALE_SCENE_3D_ENABLED} 开关保持不动，两条 3D 路线互不污染。</p>
 *
 * <p>模型资产 GLB/GLTF 存 whale-file（8085），本服务不自建对象存储；实时数据
 * 来源为 whale-iot internal 只读接口（M2 起轮询拉取），本服务不直接连 MQTT。</p>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(TwinProperties.class)
public class WhaleTwinApplication {

    /**
     * 应用启动入口
     *
     * @param args 启动命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleTwinApplication.class, args);
    }
}
