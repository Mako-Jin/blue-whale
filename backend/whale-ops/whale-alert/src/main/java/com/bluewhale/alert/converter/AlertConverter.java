package com.bluewhale.alert.converter;

import com.bluewhale.alert.dto.AlertRuleSaveRequest;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.entity.AlertRule;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.alert.vo.AlertRuleVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * 告警模块实体与 VO/DTO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段
 * 对齐。</p>
 * @author BlueWhale
 */
@Mapper
public interface AlertConverter {

    /** 单例（无状态映射器） */
    AlertConverter INSTANCE = Mappers.getMapper(AlertConverter.class);

    /**
     * 告警记录实体转视图对象
     *
     * @param record 告警记录实体
     * @return 告警记录视图对象
     */
    AlertRecordVO toRecordVo(AlertRecord record);

    /**
     * 告警规则实体转视图对象
     *
     * @param rule 告警规则实体
     * @return 告警规则视图对象
     */
    AlertRuleVO toRuleVo(AlertRule rule);

    /**
     * 保存请求转规则实体（唯一性校验由服务层处理）
     *
     * @param request 保存请求
     * @return 规则实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    AlertRule toEntity(AlertRuleSaveRequest request);

    /**
     * 保存请求覆盖既有规则实体（保留主键与审计字段）
     *
     * @param request 保存请求
     * @param entity  待更新的规则实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(AlertRuleSaveRequest request, @MappingTarget AlertRule entity);
}
