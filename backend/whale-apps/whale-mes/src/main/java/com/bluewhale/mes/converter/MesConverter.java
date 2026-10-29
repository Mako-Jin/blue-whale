package com.bluewhale.mes.converter;

import com.bluewhale.mes.dto.MesWorkOrderSaveRequest;
import com.bluewhale.mes.entity.MesWorkOrder;
import com.bluewhale.mes.vo.MesWorkOrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * MES 模块实体与 VO/DTO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段对齐。</p>
 * @author BlueWhale
 */
@Mapper
public interface MesConverter {

    /** 单例（无状态映射器） */
    MesConverter INSTANCE = Mappers.getMapper(MesConverter.class);

    /**
     * 工单实体转视图对象
     *
     * @param entity 工单实体
     * @return 工单视图对象
     */
    MesWorkOrderVO toWorkOrderVo(MesWorkOrder entity);

    /**
     * 工单保存请求转实体
     *
     * @param request 保存请求
     * @return 工单实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    MesWorkOrder toEntity(MesWorkOrderSaveRequest request);

    /**
     * 工单保存请求覆盖既有实体
     *
     * @param request 保存请求
     * @param entity  待更新的工单实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(MesWorkOrderSaveRequest request, @MappingTarget MesWorkOrder entity);
}
