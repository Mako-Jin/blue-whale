package com.bluewhale.oa.converter;

import com.bluewhale.oa.dto.OaLeaveSaveRequest;
import com.bluewhale.oa.entity.OaLeave;
import com.bluewhale.oa.vo.OaLeaveVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * OA 模块实体与 VO/DTO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段对齐。</p>
 * @author BlueWhale
 */
@Mapper
public interface OaConverter {

    /** 单例（无状态映射器） */
    OaConverter INSTANCE = Mappers.getMapper(OaConverter.class);

    /**
     * 请假单实体转视图对象
     *
     * @param entity 请假单实体
     * @return 请假单视图对象
     */
    OaLeaveVO toLeaveVo(OaLeave entity);

    /**
     * 请假单保存请求转实体
     *
     * @param request 保存请求
     * @return 请假单实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    OaLeave toEntity(OaLeaveSaveRequest request);

    /**
     * 请假单保存请求覆盖既有实体
     *
     * @param request 保存请求
     * @param entity  待更新的请假单实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(OaLeaveSaveRequest request, @MappingTarget OaLeave entity);
}
