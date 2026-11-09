package com.bluewhale.iot.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.iot.dto.ThingModelItem;
import com.bluewhale.iot.dto.ThingModelSaveRequest;
import com.bluewhale.iot.dto.ThingModelVO;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.entity.IotThingModel;
import com.bluewhale.iot.mapper.IotThingModelMapper;
import com.bluewhale.iot.service.spec.ThingModelValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 物模型服务：整包覆盖式保存与查询。
 *
 * <p>口径：已发布产品<b>禁止修改物模型</b>（需先停用）——发布即"接入契约冻结"，
 * 避免 Online 设备的遥测与模型定义悄悄脱节。保存为物理替换
 * （{@link IotThingModelMapper#deletePhysicalByProductId} 后整批插入）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotThingModelService {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 模型类别：属性 */
    public static final String KIND_PROPERTY = "property";

    private final IotThingModelMapper thingModelMapper;

    private final IotProductService productService;

    /**
     * 整包覆盖式保存物模型（已发布产品拒绝）。
     *
     * @param productId 产品ID
     * @param request   保存请求（空列表 = 清空）
     * @return 保存后的物模型视图
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ThingModelVO> replaceAll(Long productId, ThingModelSaveRequest request) {
        IotProduct product = productService.requireExists(productId);
        if (IotProductService.STATUS_PUBLISHED.equals(product.getStatus())) {
            throw new BizException("产品已发布，物模型已冻结（请先停用产品再修改）");
        }
        List<ThingModelItem> items = request.getItems() == null ? List.of() : request.getItems();
        validateNoDuplicate(items);
        ThingModelValidator.validate(items);
        thingModelMapper.deletePhysicalByProductId(productId);
        for (ThingModelItem item : items) {
            IotThingModel model = new IotThingModel();
            model.setProductId(productId);
            model.setKind(item.getKind());
            model.setIdentifier(item.getIdentifier());
            model.setName(item.getName());
            model.setDataType(item.getDataType());
            model.setSpecJson(toJson(item.getSpec()));
            model.setRequired(item.getRequired() == null ? 0 : item.getRequired());
            thingModelMapper.insert(model);
        }
        return listByProduct(productId);
    }

    /**
     * 查询产品物模型全集。
     *
     * @param productId 产品ID
     * @return 物模型视图列表（property 在前，同类按标识符排序）
     */
    public List<ThingModelVO> listByProduct(Long productId) {
        productService.requireExists(productId);
        List<IotThingModel> models = thingModelMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IotThingModel>()
                        .eq(IotThingModel::getProductId, productId)
                        .orderByAsc(IotThingModel::getKind)
                        .orderByAsc(IotThingModel::getIdentifier));
        List<ThingModelVO> result = new ArrayList<>(models.size());
        for (IotThingModel model : models) {
            result.add(ThingModelVO.builder()
                    .kind(model.getKind())
                    .identifier(model.getIdentifier())
                    .name(model.getName())
                    .dataType(model.getDataType())
                    .spec(parseJson(model.getSpecJson()))
                    .required(model.getRequired())
                    .build());
        }
        return result;
    }

    /**
     * 校验条目无重复（产品+类别+标识符 维度）。
     *
     * @param items 条目列表
     */
    private static void validateNoDuplicate(List<ThingModelItem> items) {
        Set<String> seen = new HashSet<>();
        for (ThingModelItem item : items) {
            String key = item.getKind() + "/" + item.getIdentifier();
            if (!seen.add(key)) {
                throw new BizException("物模型条目重复：" + key);
            }
        }
    }

    /**
     * 序列化规格为 JSON。
     *
     * @param spec 规格对象
     * @return JSON 串；空值返回 {@code null}
     */
    private static String toJson(Object spec) {
        if (spec == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(spec);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("物模型规格序列化失败", e);
        }
    }

    /**
     * 解析规格 JSON。
     *
     * @param json JSON 串
     * @return 规格对象；空或解析失败返回 {@code null}
     */
    private static Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("物模型规格 JSON 解析失败", e);
            return null;
        }
    }
}
