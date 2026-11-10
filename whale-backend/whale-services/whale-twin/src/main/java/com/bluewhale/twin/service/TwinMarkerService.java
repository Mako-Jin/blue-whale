package com.bluewhale.twin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.dto.MarkerSaveRequest;
import com.bluewhale.twin.dto.MarkerVO;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.entity.TwinMarker;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.mapper.TwinMarkerMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 孪生标注/热点服务：标注 CRUD 与合法性校验。
 *
 * <p>标注挂在场景下，{@code entityId} 可空（场景级锚点）；校验规则：
 * 类型白名单（label/hotspot/alarm）、坐标 JSON 必须是对象（{"x","y","z"}，
 * 语义由前端解释）、挂靠实体必须属于当前场景。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TwinMarkerService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** JSON 解析器（仅校验坐标 JSON 结构） */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final TwinMarkerMapper markerMapper;

    private final TwinEntityMapper entityMapper;

    private final TwinSceneService sceneService;

    /**
     * 创建标注。
     *
     * @param sceneId 场景ID
     * @param request 保存请求
     * @return 视图对象
     */
    public MarkerVO create(Long sceneId, MarkerSaveRequest request) {
        sceneService.requireExists(sceneId);
        assertPositionJson(request.getPositionJson());
        TwinMarker marker = new TwinMarker();
        applyRequest(marker, request);
        marker.setSceneId(sceneId);
        marker.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        if (request.getEntityId() != null) {
            marker.setEntityId(requireEntityInScene(sceneId, request.getEntityId()).getId());
        }
        markerMapper.insert(marker);
        return toVo(marker);
    }

    /**
     * 更新标注（类型可改；挂靠实体可换）。
     *
     * @param sceneId 场景ID
     * @param id      标注ID
     * @param request 保存请求
     * @return 视图对象
     */
    public MarkerVO update(Long sceneId, Long id, MarkerSaveRequest request) {
        TwinMarker marker = requireInScene(sceneId, id);
        assertPositionJson(request.getPositionJson());
        applyRequest(marker, request);
        marker.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        if (request.getEntityId() != null) {
            marker.setEntityId(requireEntityInScene(sceneId, request.getEntityId()).getId());
        } else {
            marker.setEntityId(null);
        }
        markerMapper.updateById(marker);
        return toVo(marker);
    }

    /**
     * 删除标注（逻辑删除）。
     *
     * @param sceneId 场景ID
     * @param id      标注ID
     */
    public void delete(Long sceneId, Long id) {
        requireInScene(sceneId, id);
        markerMapper.deleteById(id);
    }

    /**
     * 标注详情。
     *
     * @param sceneId 场景ID
     * @param id      标注ID
     * @return 视图对象
     */
    public MarkerVO get(Long sceneId, Long id) {
        return toVo(requireInScene(sceneId, id));
    }

    /**
     * 场景标注列表（id 升序；冗余挂靠实体编码/名称，实体已删则留空）。
     *
     * @param sceneId 场景ID
     * @return 标注视图列表
     */
    public List<MarkerVO> list(Long sceneId) {
        List<TwinMarker> markers = markerMapper.selectList(new LambdaQueryWrapper<TwinMarker>()
                .eq(TwinMarker::getSceneId, sceneId)
                .orderByAsc(TwinMarker::getId));
        List<MarkerVO> result = new ArrayList<>(markers.size());
        for (TwinMarker marker : markers) {
            result.add(toVo(marker));
        }
        return result;
    }

    /**
     * 校验标注属于指定场景。
     *
     * @param sceneId 场景ID
     * @param id      标注ID
     * @return 标注实体
     */
    private TwinMarker requireInScene(Long sceneId, Long id) {
        TwinMarker marker = markerMapper.selectById(id);
        if (marker == null || !sceneId.equals(marker.getSceneId())) {
            throw new BizException("标注不存在或不属于当前场景");
        }
        return marker;
    }

    /**
     * 校验挂靠实体属于指定场景。
     *
     * @param sceneId  场景ID
     * @param entityId 实体ID
     * @return 实体
     */
    private TwinEntity requireEntityInScene(Long sceneId, Long entityId) {
        TwinEntity entity = entityMapper.selectById(entityId);
        if (entity == null || !sceneId.equals(entity.getSceneId())) {
            throw new BizException("挂靠实体不存在或不属于当前场景");
        }
        return entity;
    }

    /**
     * 校验坐标 JSON 必须是 JSON 对象（空值放行，位置可后补）。
     *
     * @param positionJson 坐标 JSON
     */
    private void assertPositionJson(String positionJson) {
        if (!StringUtils.hasText(positionJson)) {
            return;
        }
        try {
            JsonNode node = JSON.readTree(positionJson);
            if (!node.isObject()) {
                throw new BizException("坐标必须是 JSON 对象（含 x/y/z）");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("坐标不是合法 JSON");
        }
    }

    /**
     * 请求字段拷贝（不含 entityId/enabled/sceneId/tenantId，由调用方回填）。
     *
     * @param marker  目标标注实体
     * @param request 保存请求
     */
    private static void applyRequest(TwinMarker marker, MarkerSaveRequest request) {
        marker.setMarkerType(request.getMarkerType());
        marker.setContent(request.getContent());
        marker.setPositionJson(request.getPositionJson());
        marker.setLinkUrl(request.getLinkUrl());
    }

    /**
     * 标注转视图对象（冗余挂靠实体信息，实体已删则留空）。
     *
     * @param marker 标注实体
     * @return 视图对象
     */
    private MarkerVO toVo(TwinMarker marker) {
        TwinEntity entity = marker.getEntityId() == null
                ? null : entityMapper.selectById(marker.getEntityId());
        return MarkerVO.builder()
                .id(String.valueOf(marker.getId()))
                .sceneId(String.valueOf(marker.getSceneId()))
                .entityId(marker.getEntityId() == null
                        ? null : String.valueOf(marker.getEntityId()))
                .entityCode(entity == null ? null : entity.getEntityCode())
                .entityName(entity == null ? null : entity.getName())
                .markerType(marker.getMarkerType())
                .content(marker.getContent())
                .positionJson(marker.getPositionJson())
                .linkUrl(marker.getLinkUrl())
                .enabled(marker.getEnabled())
                .createTime(marker.getCreateTime() == null
                        ? null : TIME_FORMAT.format(marker.getCreateTime()))
                .build();
    }
}
