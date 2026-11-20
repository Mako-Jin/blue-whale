package com.bluewhale.twin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.twin.dto.EntitySaveRequest;
import com.bluewhale.twin.dto.EntityVO;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 孪生实体服务：场景树节点的增删改查。
 *
 * <p>树规则：{@code parentId} 指向同场景实体；移动节点时沿父链上溯做
 * <b>环检测</b>（不允许把节点挂到自己的子孙下）；删除要求节点下无子实体。
 * 场景已发布时禁止结构变更（发布语义由 {@link TwinSceneService} 把守）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TwinEntityService {

    private final TwinEntityMapper entityMapper;

    private final TwinSceneService sceneService;

    /**
     * 创建实体（挂在指定父节点下；父节点须属于同一场景）。
     *
     * @param sceneId 场景ID
     * @param request 保存请求
     * @return 视图对象
     */
    public EntityVO create(Long sceneId, EntitySaveRequest request) {
        sceneService.requireExists(sceneId);
        assertParentInScene(sceneId, request.getParentId(), null);
        assertCodeUnique(sceneId, request.getEntityCode(), null);
        TwinEntity entity = new TwinEntity();
        applyRequest(entity, request);
        entity.setSceneId(sceneId);
        entityMapper.insert(entity);
        return toVo(entity);
    }

    /**
     * 更新实体（编码不可改；可移动父节点，移动时做环检测）。
     *
     * @param sceneId 场景ID
     * @param id      实体ID
     * @param request 保存请求
     * @return 视图对象
     */
    public EntityVO update(Long sceneId, Long id, EntitySaveRequest request) {
        sceneService.requireExists(sceneId);
        TwinEntity entity = requireInScene(sceneId, id);
        assertParentInScene(sceneId, request.getParentId(), id);
        assertCodeUnique(sceneId, request.getEntityCode(), id);
        applyRequest(entity, request);
        entityMapper.updateById(entity);
        return toVo(entity);
    }

    /**
     * 删除实体（逻辑删除；要求无子实体）。
     *
     * @param sceneId 场景ID
     * @param id      实体ID
     */
    public void delete(Long sceneId, Long id) {
        TwinEntity entity = requireInScene(sceneId, id);
        Long childCount = entityMapper.selectCount(new LambdaQueryWrapper<TwinEntity>()
                .eq(TwinEntity::getParentId, entity.getId()));
        if (childCount != null && childCount > 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.entity.children-remaining",
                    "实体下仍有 " + childCount + " 个子节点，请先删除子节点", childCount);
        }
        entityMapper.deleteById(entity.getId());
    }

    /**
     * 实体详情。
     *
     * @param sceneId 场景ID
     * @param id      实体ID
     * @return 视图对象
     */
    public EntityVO get(Long sceneId, Long id) {
        return toVo(requireInScene(sceneId, id));
    }

    /**
     * 场景实体树（children 按 sortOrder 升序递归装配；空场景返回空列表）。
     *
     * @param sceneId 场景ID
     * @return 根节点列表
     */
    public List<EntityVO> tree(Long sceneId) {
        List<TwinEntity> all = listByScene(sceneId);
        Map<Long, EntityVO> voById = new HashMap<>(all.size() * 2);
        for (TwinEntity entity : all) {
            voById.put(entity.getId(), toVo(entity));
        }
        List<EntityVO> roots = new ArrayList<>();
        for (TwinEntity entity : all) {
            EntityVO vo = voById.get(entity.getId());
            if (entity.getParentId() == null) {
                roots.add(vo);
            } else {
                EntityVO parent = voById.get(entity.getParentId());
                if (parent == null) {
                    // 父节点缺失（数据异常），按根节点兜底，避免子树丢失
                    roots.add(vo);
                } else {
                    parent.getChildren().add(vo);
                }
            }
        }
        sortRecursively(roots);
        return roots;
    }

    /**
     * 场景实体平铺列表（按 sortOrder 升序）。
     *
     * @param sceneId 场景ID
     * @return 实体列表
     */
    public List<EntityVO> list(Long sceneId) {
        List<EntityVO> result = new ArrayList<>();
        for (TwinEntity entity : listByScene(sceneId)) {
            result.add(toVo(entity));
        }
        return result;
    }

    /**
     * 沿父链上溯做环检测：移动 target 到 newParent 下不得形成回路。
     *
     * <p>同时校验 newParent 存在且属于同一场景；target 为根（null）直接放行。</p>
     *
     * @param sceneId   场景ID
     * @param newParent 目标父节点（可空）
     * @param targetId  被移动节点ID（新建时为 null，仅做归属校验）
     */
    private void assertParentInScene(Long sceneId, Long newParent, Long targetId) {
        if (newParent == null) {
            return;
        }
        TwinEntity parent = entityMapper.selectById(newParent);
        if (parent == null || !sceneId.equals(parent.getSceneId())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.entity.parent-not-found",
                    "父节点不存在或不属于当前场景");
        }
        if (targetId == null) {
            return;
        }
        if (targetId.equals(newParent)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.entity.self-parent", "不能把实体挂到自己下面");
        }
        Deque<Long> pending = new ArrayDeque<>();
        pending.push(parent.getId());
        while (!pending.isEmpty()) {
            Long currentId = pending.pop();
            if (currentId.equals(targetId)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.entity.cyclic-parent",
                        "不能把实体挂到自己的子孙节点下（会形成环）");
            }
            TwinEntity current = entityMapper.selectById(currentId);
            if (current != null && current.getParentId() != null) {
                pending.push(current.getParentId());
            }
        }
    }

    /**
     * 实体编码场景内唯一校验（排除自身）。
     *
     * @param sceneId 场景ID
     * @param code    实体编码
     * @param selfId  自身ID（新建时为 null）
     */
    private void assertCodeUnique(Long sceneId, String code, Long selfId) {
        List<TwinEntity> all = listByScene(sceneId);
        for (TwinEntity entity : all) {
            if (entity.getEntityCode().equals(code) && !entity.getId().equals(selfId)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.entity.code-exists",
                        "实体编码已存在：" + code, code);
            }
        }
    }

    /**
     * 取场景内实体（存在性与场景归属双重校验）。
     *
     * @param sceneId 场景ID
     * @param id      实体ID
     * @return 实体
     */
    private TwinEntity requireInScene(Long sceneId, Long id) {
        TwinEntity entity = entityMapper.selectById(id);
        if (entity == null || !sceneId.equals(entity.getSceneId())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.entity.not-found-in-scene",
                    "实体不存在或不属于当前场景");
        }
        return entity;
    }

    /**
     * 查询场景内全部实体（sortOrder 升序、id 兜底）。
     *
     * @param sceneId 场景ID
     * @return 实体列表
     */
    private List<TwinEntity> listByScene(Long sceneId) {
        return entityMapper.selectList(new LambdaQueryWrapper<TwinEntity>()
                .eq(TwinEntity::getSceneId, sceneId)
                .orderByAsc(TwinEntity::getSortOrder)
                .orderByAsc(TwinEntity::getId));
    }

    /**
     * 请求字段拷贝到实体（编码/场景外字段全量覆盖）。
     *
     * @param entity  实体
     * @param request 保存请求
     */
    private static void applyRequest(TwinEntity entity, EntitySaveRequest request) {
        entity.setEntityCode(request.getEntityCode());
        entity.setName(request.getName());
        entity.setParentId(request.getParentId());
        entity.setModelFileId(request.getModelFileId());
        entity.setTransformJson(request.getTransformJson());
        entity.setExtraConfig(request.getExtraConfig());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    /**
     * 递归按 sortOrder 排序子节点。
     *
     * @param nodes 节点列表
     */
    private static void sortRecursively(List<EntityVO> nodes) {
        nodes.sort(Comparator
                .comparing((EntityVO node) -> node.getSortOrder() == null ? 0 : node.getSortOrder())
                .thenComparing(EntityVO::getId));
        for (EntityVO node : nodes) {
            sortRecursively(node.getChildren());
        }
    }

    /**
     * 实体转视图对象。
     *
     * @param entity 实体
     * @return 视图对象
     */
    private static EntityVO toVo(TwinEntity entity) {
        return EntityVO.builder()
                .id(String.valueOf(entity.getId()))
                .sceneId(String.valueOf(entity.getSceneId()))
                .entityCode(entity.getEntityCode())
                .name(entity.getName())
                .parentId(entity.getParentId() == null
                        ? null : String.valueOf(entity.getParentId()))
                .modelFileId(entity.getModelFileId() == null
                        ? null : String.valueOf(entity.getModelFileId()))
                .transformJson(entity.getTransformJson())
                .extraConfig(entity.getExtraConfig())
                .sortOrder(entity.getSortOrder())
                .children(new ArrayList<>())
                .build();
    }
}
