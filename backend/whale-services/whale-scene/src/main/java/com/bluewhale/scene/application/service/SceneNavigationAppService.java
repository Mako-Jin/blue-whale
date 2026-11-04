package com.bluewhale.scene.application.service;

import com.bluewhale.scene.application.support.NavigationUser;
import com.bluewhale.scene.application.view.NavigationSceneView;

import java.util.List;

/**
 * 场景导航只读用例服务。
 *
 * @author BlueWhale
 */
public interface SceneNavigationAppService {

    /**
     * 查询当前访问者可见的场景导航。
     *
     * @param user 访问者身份
     * @return 场景导航列表
     */
    List<NavigationSceneView> navigation(NavigationUser user);
}
