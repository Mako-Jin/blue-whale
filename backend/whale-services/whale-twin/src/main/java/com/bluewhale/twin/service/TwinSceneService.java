package com.bluewhale.twin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.twin.dto.SceneSaveRequest;
import com.bluewhale.twin.dto.SceneVO;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.mapper.TwinSceneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 孪生场景服务：创建/更新/发布/停用/删除与分页查询。
 *
 * <p>状态机对齐 whale-iot 产品：{@code draft → published ⇄ disabled}；
 * 已发布场景<b>禁止修改</b>名称/描述/配置（需先停用）；删除要求场景下
 * 已无实体。每次内容更新 {@code version} 自增，供发布快照冻结版本。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TwinSceneService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 状态：草稿 */
    public static final String STATUS_DRAFT = "draft";

    /** 状态：已发布 */
    public static final String STATUS_PUBLISHED = "published";

    /** 状态：已停用 */
    public static final String STATUS_DISABLED = "disabled";

    private final TwinSceneMapper sceneMapper;

    private final TwinEntityMapper entityMapper;

    /**
     * 创建场景（草稿态，版本号 1）。
     *
     * @param request 保存请求
     * @param userId  当前用户
     * @return 视图对象
     */
    public SceneVO create(SceneSaveRequest request, Long userId) {
        Long existed = sceneMapper.selectCount(
                new LambdaQueryWrapper<TwinScene>().eq(TwinScene::getCode, request.getCode()));
        if (existed != null && existed > 0) {
            throw new BizException("场景编码已存在：" + request.getCode());
        }
        TwinScene scene = new TwinScene();
        scene.setCode(request.getCode());
        scene.setName(request.getName());
        scene.setDescription(request.getDescription());
        scene.setThumbnailFileId(request.getThumbnailFileId());
        scene.setSceneConfig(request.getSceneConfig());
        scene.setVersion(1);
        scene.setStatus(STATUS_DRAFT);
        scene.setCreatedBy(userId);
        sceneMapper.insert(scene);
        return toVo(scene, 0L);
    }

    /**
     * 更新场景（编码不可改；已发布场景需先停用；version 自增）。
     *
     * @param id      场景ID
     * @param request 保存请求
     * @return 视图对象
     */
    public SceneVO update(Long id, SceneSaveRequest request) {
        TwinScene scene = requireExists(id);
        if (STATUS_PUBLISHED.equals(scene.getStatus())) {
            throw new BizException("场景已发布，禁止修改（请先停用）");
        }
        scene.setName(request.getName());
        scene.setDescription(request.getDescription());
        scene.setThumbnailFileId(request.getThumbnailFileId());
        scene.setSceneConfig(request.getSceneConfig());
        scene.setVersion(scene.getVersion() == null ? 1 : scene.getVersion() + 1);
        sceneMapper.updateById(scene);
        return toVo(scene, countEntities(id));
    }

    /**
     * 发布场景（draft/disabled → published；内容随之冻结进快照由 M3 补齐）。
     *
     * @param id 场景ID
     * @return 视图对象
     */
    public SceneVO publish(Long id) {
        TwinScene scene = requireExists(id);
        scene.setStatus(STATUS_PUBLISHED);
        sceneMapper.updateById(scene);
        return toVo(scene, countEntities(id));
    }

    /**
     * 停用场景（published → disabled，恢复可编辑）。
     *
     * @param id 场景ID
     * @return 视图对象
     */
    public SceneVO disable(Long id) {
        TwinScene scene = requireExists(id);
        scene.setStatus(STATUS_DISABLED);
        sceneMapper.updateById(scene);
        return toVo(scene, countEntities(id));
    }

    /**
     * 删除场景（逻辑删除；要求场景下已无实体）。
     *
     * @param id 场景ID
     */
    public void delete(Long id) {
        requireExists(id);
        long entityCount = countEntities(id);
        if (entityCount > 0) {
            throw new BizException("场景下仍有 " + entityCount + " 个实体，请先删除实体");
        }
        sceneMapper.deleteById(id);
    }

    /**
     * 场景分页（管理视角）。
     *
     * @param keyword 名称/编码模糊过滤（可空）
     * @param status  状态过滤（可空）
     * @param current 当前页
     * @param size    每页条数
     * @return 分页结果
     */
    public PageResult<SceneVO> page(String keyword, String status, long current, long size) {
        long pageNo = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<TwinScene> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(TwinScene::getName, keyword)
                    .or().like(TwinScene::getCode, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(TwinScene::getStatus, status);
        }
        wrapper.orderByDesc(TwinScene::getUpdateTime).orderByDesc(TwinScene::getId);
        Page<TwinScene> page = sceneMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<SceneVO> records = new ArrayList<>(page.getRecords().size());
        for (TwinScene scene : page.getRecords()) {
            records.add(toVo(scene, countEntities(scene.getId())));
        }
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    /**
     * 场景详情。
     *
     * @param id 场景ID
     * @return 视图对象
     */
    public SceneVO get(Long id) {
        return toVo(requireExists(id), countEntities(id));
    }

    /**
     * 按 ID 取场景（存在性校验）。
     *
     * @param id 场景ID
     * @return 场景实体
     */
    public TwinScene requireExists(Long id) {
        TwinScene scene = sceneMapper.selectById(id);
        if (scene == null) {
            throw new BizException("场景不存在");
        }
        return scene;
    }

    /**
     * 统计场景下实体数（逻辑删除口径）。
     *
     * @param sceneId 场景ID
     * @return 实体数
     */
    public long countEntities(Long sceneId) {
        Long count = entityMapper.selectCount(
                new LambdaQueryWrapper<TwinEntity>().eq(TwinEntity::getSceneId, sceneId));
        return count == null ? 0L : count;
    }

    /**
     * 实体转视图对象。
     *
     * @param scene       场景实体
     * @param entityCount 实体数
     * @return 视图对象
     */
    private SceneVO toVo(TwinScene scene, long entityCount) {
        return SceneVO.builder()
                .id(String.valueOf(scene.getId()))
                .code(scene.getCode())
                .name(scene.getName())
                .description(scene.getDescription())
                .thumbnailFileId(scene.getThumbnailFileId() == null
                        ? null : String.valueOf(scene.getThumbnailFileId()))
                .sceneConfig(scene.getSceneConfig())
                .version(scene.getVersion())
                .status(scene.getStatus())
                .entityCount(entityCount)
                .createdBy(scene.getCreatedBy() == null
                        ? null : String.valueOf(scene.getCreatedBy()))
                .createTime(format(scene.getCreateTime()))
                .updateTime(format(scene.getUpdateTime()))
                .build();
    }

    /**
     * 格式化时间。
     *
     * @param time 时间
     * @return ISO-8601 字符串；空值返回 {@code null}
     */
    private static String format(java.time.LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }
}
