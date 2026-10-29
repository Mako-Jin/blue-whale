package com.bluewhale.i18n.admin.converter;

import com.bluewhale.i18n.admin.dto.I18nMessageSaveRequest;
import com.bluewhale.i18n.admin.vo.I18nMessageVO;
import com.bluewhale.i18n.entity.SysI18nMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 国际化消息实体与 VO/DTO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段对齐。</p>
 */
@Mapper
public interface I18nMessageConverter {

    /** 单例（无状态映射器） */
    I18nMessageConverter INSTANCE = Mappers.getMapper(I18nMessageConverter.class);

    /**
     * 消息实体转视图对象
     *
     * @param message 消息实体
     * @return 消息视图对象
     */
    I18nMessageVO toVo(SysI18nMessage message);

    /**
     * 保存请求转实体
     *
     * @param request 保存请求
     * @return 消息实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    SysI18nMessage toEntity(I18nMessageSaveRequest request);

    /**
     * 保存请求覆盖既有实体
     *
     * @param request 保存请求
     * @param entity  待更新的消息实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(I18nMessageSaveRequest request, @MappingTarget SysI18nMessage entity);
}
