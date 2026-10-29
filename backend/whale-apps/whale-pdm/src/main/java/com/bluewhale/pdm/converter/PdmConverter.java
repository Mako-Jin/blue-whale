package com.bluewhale.pdm.converter;

import com.bluewhale.pdm.dto.PdmProductSaveRequest;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.vo.PdmProductVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * PDM 模块实体与 VO/DTO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段对齐。</p>
 * @author BlueWhale
 */
@Mapper
public interface PdmConverter {

    /** 单例（无状态映射器） */
    PdmConverter INSTANCE = Mappers.getMapper(PdmConverter.class);

    /**
     * 产品实体转视图对象
     *
     * @param entity 产品实体
     * @return 产品视图对象
     */
    PdmProductVO toProductVo(PdmProduct entity);

    /**
     * 产品保存请求转实体
     *
     * @param request 保存请求
     * @return 产品实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    PdmProduct toEntity(PdmProductSaveRequest request);

    /**
     * 产品保存请求覆盖既有实体
     *
     * @param request 保存请求
     * @param entity  待更新的产品实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(PdmProductSaveRequest request, @MappingTarget PdmProduct entity);
}
