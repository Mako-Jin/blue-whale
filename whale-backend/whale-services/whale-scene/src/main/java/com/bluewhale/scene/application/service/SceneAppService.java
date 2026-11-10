package com.bluewhale.scene.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.scene.application.command.NodeBindingSaveCommand;
import com.bluewhale.scene.application.command.SceneNodeSaveCommand;
import com.bluewhale.scene.application.command.SceneQueryCommand;
import com.bluewhale.scene.application.command.SceneSaveCommand;
import com.bluewhale.scene.application.command.SceneVisibilityCommand;
import com.bluewhale.scene.application.view.NodeBindingView;
import com.bluewhale.scene.application.view.SceneDetailView;
import com.bluewhale.scene.application.view.SceneNodeView;
import com.bluewhale.scene.application.view.SceneView;
import com.bluewhale.scene.application.view.SceneVisibilityView;

import java.util.List;

/**
 * 场景管理用例服务。
 *
 * <p>写用例（新建 / 编辑 / 发布 / 下线 / 节点 / 绑定 / 可见性）在此声明，
 * 读用例（分页 / 详情）一并提供，避免为了读模型拆出额外服务。</p>
 *
 * @author BlueWhale
 */
public interface SceneAppService {

    /**
     * 场景分页。
     *
     * @param command 查询命令
     * @return 分页结果
     */
    PageResult<SceneView> page(SceneQueryCommand command);

    /**
     * 场景详情（节点 / 绑定 / 可见性 / 最近快照）。
     *
     * @param id 场景 ID
     * @return 详情
     */
    SceneDetailView detail(Long id);

    /**
     * 新建场景（草稿态）。
     *
     * @param command 保存命令
     * @return 场景视图
     */
    SceneView create(SceneSaveCommand command);

    /**
     * 编辑场景。
     *
     * @param id 场景 ID
     * @param command 保存命令
     * @return 场景视图
     */
    SceneView update(Long id, SceneSaveCommand command);

    /**
     * 发布场景：校验节点树与入口后生成布局快照。
     *
     * @param id 场景 ID
     * @return 场景视图
     */
    SceneView publish(Long id);

    /**
     * 下线场景。
     *
     * @param id 场景 ID
     * @return 场景视图
     */
    SceneView offline(Long id);

    /**
     * 新增 / 更新节点。
     *
     * @param sceneId 场景 ID
     * @param nodeId 节点 ID；为空表示新增
     * @param command 保存命令
     * @return 节点视图
     */
    SceneNodeView saveNode(Long sceneId, Long nodeId, SceneNodeSaveCommand command);

    /**
     * 删除节点（存在子节点时拒绝）。
     *
     * @param nodeId 节点 ID
     */
    void deleteNode(Long nodeId);

    /**
     * 设置节点入口绑定。
     *
     * @param nodeId 节点 ID
     * @param command 绑定命令
     * @return 绑定视图
     */
    NodeBindingView saveBinding(Long nodeId, NodeBindingSaveCommand command);

    /**
     * 替换场景可见性规则。
     *
     * @param sceneId 场景 ID
     * @param commands 规则列表
     * @return 最新规则
     */
    List<SceneVisibilityView> replaceVisibility(Long sceneId, List<SceneVisibilityCommand> commands);
}
