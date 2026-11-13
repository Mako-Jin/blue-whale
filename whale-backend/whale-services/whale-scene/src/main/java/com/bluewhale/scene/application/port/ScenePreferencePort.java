package com.bluewhale.scene.application.port;

import com.bluewhale.scene.application.view.ScenePreferenceView;

import java.util.Optional;

/**
 * 场景偏好存储端口：读写用户跨端一致的场景偏好（视图模式 / 默认场景）。
 *
 * <p>应用层只依赖本端口，具体存储（auth 统一配置 {@code sys_user_config} 的内网调用）
 * 由基础设施层实现——{@code com.bluewhale.scene.infrastructure.client.AuthScenePreferenceClient}。
 * 这样 {@code SceneArchitectureTest#application_must_not_depend_on_outer_layers} 才成立：
 * 应用层不得直接依赖 {@code infrastructure..}。</p>
 *
 * <p>失败语义由实现决定，约定为：<b>读 fail-open</b>（读不到按无配置返回 empty，
 * 由调用方回落默认偏好）、<b>写 fail-closed</b>（写失败抛业务异常，不假装成功）。</p>
 *
 * @author BlueWhale
 */
public interface ScenePreferencePort {

    /**
     * 读取用户场景偏好。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 偏好视图；未配置或读取失败返回 {@link Optional#empty()}
     */
    Optional<ScenePreferenceView> read(Long tenantId, Long userId);

    /**
     * 写入（覆盖）用户场景偏好。
     *
     * @param tenantId   租户 ID
     * @param userId     用户 ID
     * @param preference 偏好视图（viewMode 必须已校验）
     * @param operatorId 操作人用户 ID
     */
    void write(Long tenantId, Long userId, ScenePreferenceView preference, Long operatorId);
}
