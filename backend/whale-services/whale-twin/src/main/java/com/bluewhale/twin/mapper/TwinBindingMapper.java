package com.bluewhale.twin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.twin.entity.TwinBinding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 孪生数据源绑定 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface TwinBindingMapper extends BaseMapper<TwinBinding> {
}
