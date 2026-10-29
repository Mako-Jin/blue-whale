package com.bluewhale.config.converter;

import com.bluewhale.config.entity.ConfigItem;
import com.bluewhale.config.entity.ConfigItemHistory;
import com.bluewhale.config.vo.ConfigItemHistoryVO;
import com.bluewhale.config.vo.ConfigItemVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 配置中心实体与 VO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段对齐。</p>
 * @author BlueWhale
 */
@Mapper
public interface ConfigItemConverter {

    /** 单例（无状态映射器） */
    ConfigItemConverter INSTANCE = Mappers.getMapper(ConfigItemConverter.class);

    /**
     * 配置项实体转视图对象
     *
     * @param item 配置项实体
     * @return 配置项视图对象
     */
    ConfigItemVO toVo(ConfigItem item);

    /**
     * 配置项历史实体转视图对象
     *
     * @param history 历史实体
     * @return 历史视图对象
     */
    ConfigItemHistoryVO toHistoryVo(ConfigItemHistory history);
}
