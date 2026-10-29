package com.bluewhale.message.converter;

import com.bluewhale.message.dto.MessageTemplateSaveRequest;
import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.entity.MessageRecord;
import com.bluewhale.message.entity.MessageTemplate;
import com.bluewhale.message.vo.MessageDeliveryVO;
import com.bluewhale.message.vo.MessageRecordVO;
import com.bluewhale.message.vo.MessageTemplateVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 消息模块实体与 VO/DTO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段
 * 对齐。投递明细列表由调用方装配。</p>
 */
@Mapper
public interface MessageConverter {

    /** 单例（无状态映射器） */
    MessageConverter INSTANCE = Mappers.getMapper(MessageConverter.class);

    /**
     * 消息记录实体转视图对象（投递明细由调用方装配）
     *
     * @param record 消息记录实体
     * @return 消息记录视图对象
     */
    @Mapping(target = "deliveries", ignore = true)
    MessageRecordVO toRecordVo(MessageRecord record);

    /**
     * 投递明细实体转视图对象
     *
     * @param delivery 投递明细实体
     * @return 投递明细视图对象
     */
    MessageDeliveryVO toDeliveryVo(MessageDelivery delivery);

    /**
     * 模板实体转视图对象
     *
     * @param template 模板实体
     * @return 模板视图对象
     */
    MessageTemplateVO toTemplateVo(MessageTemplate template);

    /**
     * 保存请求转模板实体（模板码归一化与状态默认值由服务层处理）
     *
     * @param request 保存请求
     * @return 模板实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    MessageTemplate toEntity(MessageTemplateSaveRequest request);

    /**
     * 保存请求覆盖既有模板实体（保留主键与审计字段）
     *
     * @param request 保存请求
     * @param entity  待更新的模板实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(MessageTemplateSaveRequest request, @MappingTarget MessageTemplate entity);
}
