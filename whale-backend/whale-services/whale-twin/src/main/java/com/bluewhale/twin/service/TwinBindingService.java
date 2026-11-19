package com.bluewhale.twin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingSaveRequest;
import com.bluewhale.twin.dto.BindingVO;
import com.bluewhale.twin.entity.TwinBinding;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.mapper.TwinBindingMapper;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.support.InternalApiStateClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 孪生数据源绑定服务：绑定 CRUD 与合法性校验。
 *
 * <p>绑定把 3D 实体和数据源连起来；校验规则：实体必须存在于指定场景、
 * 数据源类型白名单（iot_device / internal_api）、{@code bindKey} 形态随
 * 类型校验（设备ID须纯数字）、{@code propertyMap} 必须是 JSON 对象。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwinBindingService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 默认轮询间隔（毫秒） */
    private static final int DEFAULT_POLL_INTERVAL_MS = 5000;

    /** 无头评估扫描时单次 IN 查询的实体ID上限（超过则跳过本轮，避免打爆数据库） */
    private static final int MAX_SCAN_ENTITY_IDS = 5000;

    /** JSON 解析器（仅做结构校验，不落字段白名单——映射语义由前端解释） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 数据源类型：IoT 设备属性 */
    public static final String SOURCE_IOT_DEVICE = "iot_device";

    /** 数据源类型：内网接口 */
    public static final String SOURCE_INTERNAL_API = "internal_api";

    private final TwinBindingMapper bindingMapper;

    private final TwinEntityMapper entityMapper;

    /** internal_api 绑定源客户端（白名单口径与运行时共用） */
    private final InternalApiStateClient internalApiStateClient;

    private final TwinProperties properties;

    /**
     * 创建绑定。
     *
     * @param sceneId 场景ID
     * @param request 保存请求
     * @return 视图对象
     */
    public BindingVO create(Long sceneId, BindingSaveRequest request) {
        TwinEntity entity = requireEntityInScene(sceneId, request.getEntityId());
        assertBindKeyShape(request.getSourceType(), request.getBindKey());
        assertPropertyMap(request.getPropertyMap());
        TwinBinding binding = new TwinBinding();
        applyRequest(binding, request);
        binding.setEntityId(entity.getId());
        binding.setTenantId(entity.getTenantId());
        bindingMapper.insert(binding);
        return toVo(binding, entity);
    }

    /**
     * 更新绑定（数据源类型不可改，改则等于删了重建）。
     *
     * @param sceneId 场景ID
     * @param id      绑定ID
     * @param request 保存请求
     * @return 视图对象
     */
    public BindingVO update(Long sceneId, Long id, BindingSaveRequest request) {
        TwinBinding binding = requireInScene(sceneId, id);
        TwinEntity entity = requireEntityInScene(sceneId, request.getEntityId());
        if (!binding.getSourceType().equals(request.getSourceType())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.binding.source-type-immutable",
                    "数据源类型不可修改，请删除后重建绑定");
        }
        assertBindKeyShape(request.getSourceType(), request.getBindKey());
        assertPropertyMap(request.getPropertyMap());
        applyRequest(binding, request);
        binding.setEntityId(entity.getId());
        bindingMapper.updateById(binding);
        return toVo(binding, entity);
    }

    /**
     * 删除绑定（逻辑删除）。
     *
     * @param sceneId 场景ID
     * @param id      绑定ID
     */
    public void delete(Long sceneId, Long id) {
        requireInScene(sceneId, id);
        bindingMapper.deleteById(id);
    }

    /**
     * 绑定详情。
     *
     * @param sceneId 场景ID
     * @param id      绑定ID
     * @return 视图对象
     */
    public BindingVO get(Long sceneId, Long id) {
        TwinBinding binding = requireInScene(sceneId, id);
        return toVo(binding, entityMapper.selectById(binding.getEntityId()));
    }

    /**
     * 场景绑定列表（先取场景实体集合，再按 entityId in 过滤；
     * 实体已被删除的绑定跳过，避免脏绑定影响前端）。
     *
     * @param sceneId 场景ID
     * @return 绑定视图列表
     */
    public List<BindingVO> list(Long sceneId) {
        List<TwinBinding> bindings = listEntitiesBindings(sceneId);
        Map<Long, TwinEntity> entityById = new HashMap<>(bindings.size() * 2);
        List<BindingVO> result = new ArrayList<>(bindings.size());
        for (TwinBinding binding : bindings) {
            TwinEntity entity = entityById.computeIfAbsent(binding.getEntityId(),
                    key -> entityMapper.selectById(key));
            if (entity == null) {
                continue;
            }
            result.add(toVo(binding, entity));
        }
        return result;
    }

    /**
     * 场景下全部绑定实体（运行时轮询与列表查询共用；空场景返回空列表）。
     *
     * @param sceneId 场景ID
     * @return 绑定实体列表
     */
    public List<TwinBinding> listByScene(Long sceneId) {
        return listEntitiesBindings(sceneId);
    }

    /**
     * 存在启用绑定（且源类型可轮询）的场景ID列表——无头评估通道的扫描入口
     * （Wave 6.08 M0）。
     *
     * <p>只取 {@code enabled=1} 的 {@code iot_device}/{@code internal_api} 绑定，
     * 经实体反查所属场景并去重，按场景ID升序返回；超过 {@code limit} 截断
     * （升序保证截断结果稳定，运维可预期哪批场景先被评估）。</p>
     *
     * @param limit 返回场景数上限（小于 1 视为不限）
     * @return 场景ID列表（可能为空）
     */
    public List<Long> listScenesWithEnabledBindings(int limit) {
        List<TwinBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<TwinBinding>()
                .eq(TwinBinding::getEnabled, 1)
                .in(TwinBinding::getSourceType, SOURCE_IOT_DEVICE, SOURCE_INTERNAL_API)
                .select(TwinBinding::getEntityId));
        if (bindings.isEmpty()) {
            return List.of();
        }
        // IN 子句规模上限：绑定数量远超实体数量时先去重，仍过大则跳过本轮（宁可不评估也不打爆数据库）
        List<Long> entityIds = bindings.stream()
                .map(TwinBinding::getEntityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (entityIds.size() > MAX_SCAN_ENTITY_IDS) {
            log.warn("无头评估扫描跳过：启用绑定关联实体过多（{} > {}）", entityIds.size(), MAX_SCAN_ENTITY_IDS);
            return List.of();
        }
        List<TwinEntity> entities = entityMapper.selectList(new LambdaQueryWrapper<TwinEntity>()
                .in(TwinEntity::getId, entityIds)
                .select(TwinEntity::getId, TwinEntity::getSceneId));
        List<Long> sceneIds = entities.stream()
                .map(TwinEntity::getSceneId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (limit > 0 && sceneIds.size() > limit) {
            return sceneIds.subList(0, limit);
        }
        return sceneIds;
    }

    /**
     * 按场景取绑定：先取场景实体集合，再按 entityId in 过滤。
     *
     * @param sceneId 场景ID
     * @return 绑定实体列表
     */
    private List<TwinBinding> listEntitiesBindings(Long sceneId) {
        List<TwinEntity> entities = entityMapper.selectList(new LambdaQueryWrapper<TwinEntity>()
                .eq(TwinEntity::getSceneId, sceneId));
        if (entities.isEmpty()) {
            return List.of();
        }
        List<Long> entityIds = entities.stream().map(TwinEntity::getId).toList();
        return bindingMapper.selectList(new LambdaQueryWrapper<TwinBinding>()
                .in(TwinBinding::getEntityId, entityIds)
                .orderByAsc(TwinBinding::getId));
    }

    /**
     * 校验实体属于指定场景，不存在或不属于则抛业务异常。
     *
     * @param sceneId  场景ID
     * @param entityId 实体ID
     * @return 实体实体
     */
    private TwinEntity requireEntityInScene(Long sceneId, Long entityId) {
        if (entityId == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.binding.entity-id-required", "实体ID不能为空");
        }
        TwinEntity entity = entityMapper.selectById(entityId);
        if (entity == null || !sceneId.equals(entity.getSceneId())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.binding.entity-not-found", "实体不存在或不属于该场景");
        }
        return entity;
    }

    /**
     * 校验绑定属于指定场景。
     *
     * @param sceneId 场景ID
     * @param id      绑定ID
     * @return 绑定实体
     */
    private TwinBinding requireInScene(Long sceneId, Long id) {
        TwinBinding binding = bindingMapper.selectById(id);
        if (binding == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.binding.not-found", "绑定不存在");
        }
        TwinEntity entity = entityMapper.selectById(binding.getEntityId());
        if (entity == null || !sceneId.equals(entity.getSceneId())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.binding.not-found-in-scene",
                    "绑定不存在或不属于该场景");
        }
        return binding;
    }

    /**
     * 校验绑定键形态：iot_device 须为纯数字设备ID；internal_api 须为以 / 开头的
     * 接口路径（无路径穿越），且命中运行时白名单前缀——保存即拦截，避免
     * 配置出永不生效的绑定。
     *
     * @param sourceType 数据源类型
     * @param bindKey    绑定键
     */
    private void assertBindKeyShape(String sourceType, String bindKey) {
        if (SOURCE_IOT_DEVICE.equals(sourceType) && !bindKey.matches("^\\d{1,19}$")) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.binding.iot-bind-key-invalid",
                    "iot_device 绑定键须为设备ID（纯数字）");
        }
        if (SOURCE_INTERNAL_API.equals(sourceType)) {
            if (internalApiStateClient.normalizePath(bindKey) == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.binding.api-bind-key-invalid",
                        "internal_api 绑定键须为以 / 开头的接口路径（不含 .. 与绝对 URL）");
            }
            if (!internalApiStateClient.isAllowed(bindKey)) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.binding.api-bind-key-not-allowed",
                        "internal_api 绑定键不在白名单内，仅允许各服务 /internal/ 只读端点");
            }
        }
    }

    /**
     * 校验属性映射必须是 JSON 对象（键为源字段，值为状态键或带阈值的对象）。
     *
     * @param propertyMap 属性映射 JSON
     */
    private void assertPropertyMap(String propertyMap) {
        if (!StringUtils.hasText(propertyMap)) {
            return;
        }
        try {
            JsonNode node = JSON.readTree(propertyMap);
            if (!node.isObject()) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.binding.property-map-not-object",
                        "属性映射必须是 JSON 对象");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "twin.binding.property-map-invalid",
                    "属性映射不是合法 JSON");
        }
    }

    /**
     * 请求字段拷贝（不含 entityId/tenantId，由调用方按场景归属回填）。
     *
     * @param binding 目标绑定实体
     * @param request 保存请求
     */
    private void applyRequest(TwinBinding binding, BindingSaveRequest request) {
        binding.setSourceType(request.getSourceType());
        binding.setBindKey(request.getBindKey());
        binding.setPropertyMap(request.getPropertyMap());
        binding.setPollIntervalMs(request.getPollIntervalMs() == null
                ? DEFAULT_POLL_INTERVAL_MS : request.getPollIntervalMs());
        binding.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
    }

    /**
     * 绑定实体转视图对象（实体可能已被删除，此时冗余字段留空）。
     *
     * @param binding 绑定实体
     * @param entity  实体实体（可空）
     * @return 视图对象
     */
    private BindingVO toVo(TwinBinding binding, TwinEntity entity) {
        return BindingVO.builder()
                .id(String.valueOf(binding.getId()))
                .entityId(String.valueOf(binding.getEntityId()))
                .entityCode(entity == null ? null : entity.getEntityCode())
                .entityName(entity == null ? null : entity.getName())
                .sourceType(binding.getSourceType())
                .bindKey(binding.getBindKey())
                .propertyMap(binding.getPropertyMap())
                .pollIntervalMs(binding.getPollIntervalMs())
                .enabled(binding.getEnabled())
                .createTime(binding.getCreateTime() == null ? null : TIME_FORMAT.format(binding.getCreateTime()))
                .build();
    }
}
