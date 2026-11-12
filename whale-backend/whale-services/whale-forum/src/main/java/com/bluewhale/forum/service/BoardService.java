package com.bluewhale.forum.service;

import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.BoardSaveRequest;
import com.bluewhale.forum.dto.BoardVO;
import com.bluewhale.forum.entity.ForumBoard;

import java.util.List;

/**
 * 版块服务：可见性裁剪、发帖准入、版块治理。
 *
 * @author BlueWhale
 */
public interface BoardService {

    /**
     * 列出当前用户可见的版块（含停用的管理端视角差异：普通用户只看启用）。
     *
     * @param actor 当前操作者
     * @return 平铺版块列表（按 parentId、sortNo 排序，前端组树）
     */
    List<BoardVO> listVisible(ForumActor actor);

    /**
     * 按 ID 取可见版块；不可见与不存在同样返回 30001，不泄露版块存在性。
     *
     * @param boardId 版块ID
     * @param actor 当前操作者
     * @return 版块实体
     * @throws com.bluewhale.common.core.exception.BizException 版块不存在或不可见
     */
    ForumBoard requireVisibleBoard(Long boardId, ForumActor actor);

    /**
     * 取可发帖版块：额外校验启用状态与发帖策略。
     *
     * @param boardId 版块ID
     * @param actor 当前操作者
     * @return 版块实体
     * @throws com.bluewhale.common.core.exception.BizException 版块只读、仅管理发或不可见
     */
    ForumBoard requirePostableBoard(Long boardId, ForumActor actor);

    /**
     * 判断版块对当前用户是否可见（可见性规则唯一实现处）。
     *
     * @param board 版块
     * @param actor 当前操作者
     * @return 可见返回 {@code true}
     */
    boolean canView(ForumBoard board, ForumActor actor);

    /**
     * 新建版块（需治理权限）。
     *
     * @param request 保存请求
     * @param actor 当前操作者
     * @return 新建后的版块
     */
    BoardVO create(BoardSaveRequest request, ForumActor actor);

    /**
     * 更新版块（编码不可改）。
     *
     * @param id 版块ID
     * @param request 保存请求
     * @param actor 当前操作者
     * @return 更新后的版块
     */
    BoardVO update(Long id, BoardSaveRequest request, ForumActor actor);

    /**
     * 删除版块（软删 + 改写编码释放唯一键）。
     *
     * @param id 版块ID
     * @param actor 当前操作者
     */
    void delete(Long id, ForumActor actor);
}
