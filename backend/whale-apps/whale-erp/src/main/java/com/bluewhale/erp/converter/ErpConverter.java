package com.bluewhale.erp.converter;

import com.bluewhale.erp.dto.ErpItemSaveRequest;
import com.bluewhale.erp.dto.ErpSupplierSaveRequest;
import com.bluewhale.erp.entity.ErpItem;
import com.bluewhale.erp.entity.ErpSupplier;
import com.bluewhale.erp.vo.ErpItemVO;
import com.bluewhale.erp.vo.ErpSupplierVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * ERP 模块实体与 VO/DTO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段对齐。</p>
 */
@Mapper
public interface ErpConverter {

    /** 单例（无状态映射器） */
    ErpConverter INSTANCE = Mappers.getMapper(ErpConverter.class);

    /**
     * 物料实体转视图对象
     *
     * @param item 物料实体
     * @return 物料视图对象
     */
    ErpItemVO toItemVo(ErpItem item);

    /**
     * 物料保存请求转实体
     *
     * @param request 保存请求
     * @return 物料实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    ErpItem toItemEntity(ErpItemSaveRequest request);

    /**
     * 物料保存请求覆盖既有实体
     *
     * @param request 保存请求
     * @param entity  待更新的物料实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateItemEntity(ErpItemSaveRequest request, @MappingTarget ErpItem entity);

    /**
     * 供应商实体转视图对象
     *
     * @param supplier 供应商实体
     * @return 供应商视图对象
     */
    ErpSupplierVO toSupplierVo(ErpSupplier supplier);

    /**
     * 供应商保存请求转实体
     *
     * @param request 保存请求
     * @return 供应商实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    ErpSupplier toSupplierEntity(ErpSupplierSaveRequest request);

    /**
     * 供应商保存请求覆盖既有实体
     *
     * @param request 保存请求
     * @param entity  待更新的供应商实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateSupplierEntity(ErpSupplierSaveRequest request, @MappingTarget ErpSupplier entity);
}
