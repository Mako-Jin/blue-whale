package com.bluewhale.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.message.po.MessageTemplatePo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 消息模板 Mapper。
 *
 * <p>不用标注 {@code @Mapper}：由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描。</p>
 *
 * @author BlueWhale
 */
public interface MessageTemplateMapper extends BaseMapper<MessageTemplatePo> {

    /**
     * 统计模板编码占用数（含逻辑删除行）。
     *
     * <p>唯一索引 {@code uk_tenant_template_code} 不包含删除标记，因此编码一旦使用过，
     * 即使模板被逻辑删除也不能复用；这里绕开 MyBatis-Plus 的 {@code deleted=0} 自动条件，
     * 避免“校验通过但插入撞唯一键”。租户条件由租户插件自动追加。</p>
     *
     * @param templateCode 模板编码（大写）
     * @param excludeId    需要排除的模板ID；新增时传 {@code 0}
     * @return 占用数
     */
    @Select("SELECT COUNT(1) FROM msg_template WHERE template_code = #{templateCode} AND id <> #{excludeId}")
    long countByCodeIncludeDeleted(@Param("templateCode") String templateCode,
                                   @Param("excludeId") Long excludeId);
}
