package com.bluewhale.scene.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 场景服务运行参数（配置前缀 {@code whale.scene}）。
 *
 * <p>所有参数均可通过环境变量覆盖；默认值面向本机开发环境。发布校验的节点数 / 深度
 * 上限是 2D 首屏预算的硬卡口（见 docs/design/scene-service.md），3D 相关参数仅在
 * M3 解冻后生效，M1/M2 保持关闭。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.scene")
public class SceneProperties {

    /** 单个场景发布时允许的最大节点数（2D 首屏预算） */
    private int maxNodesPerScene = 200;

    /** 节点树最大深度（根节点深度为 1） */
    private int maxNodeDepth = 5;

    /** 3D 增强开关：M1/M2 必须为 false，M3 解冻评审通过后再打开 */
    private boolean threeDEnabled = false;

    /** 导航接口单场景节点返回上限（防御性截断，避免超大布局拖垮首屏） */
    private int navigationNodeLimit = 200;

    /** 允许外部入口的协议白名单（内部路径入口另行放行） */
    private String allowedSchemes = "http,https";

    /**
     * auth 内网基地址：用户偏好读写走 auth 统一配置（Wave 6.25）。
     *
     * <p>容器内必须为 {@code http://bw-auth:8081}（compose 已注入）；
     * 默认值面向本机直接起进程的调试场景。读失败按默认偏好处理（fail-open），
     * 写失败直接报错（fail-closed）。</p>
     */
    private String authBaseUrl = "http://127.0.0.1:8081";
}
