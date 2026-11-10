package com.bluewhale.form.converter;

import com.bluewhale.form.entity.FormColumnMeta;
import com.bluewhale.form.entity.FormData;
import com.bluewhale.form.entity.FormDefinition;
import com.bluewhale.form.entity.FormFieldConfig;
import com.bluewhale.form.entity.FormFieldOption;
import com.bluewhale.form.entity.FormTableDefinition;
import com.bluewhale.form.entity.FormTableRegistry;
import com.bluewhale.form.vo.FormColumnMetaVO;
import com.bluewhale.form.vo.FormDataVO;
import com.bluewhale.form.vo.FormDefinitionVO;
import com.bluewhale.form.vo.FormFieldConfigVO;
import com.bluewhale.form.vo.FormFieldOptionVO;
import com.bluewhale.form.vo.FormTableDefinitionVO;
import com.bluewhale.form.vo.FormTableRegistryVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 表单模块实体与 VO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段
 * 对齐。派生字段（fields/options/columns 等）由调用方装配，在此声明 ignore。</p>
 * @author BlueWhale
 */
@Mapper
public interface FormConverter {

    /** 单例（无状态映射器） */
    FormConverter INSTANCE = Mappers.getMapper(FormConverter.class);

    /**
     * 表单数据实体转视图对象（表单字段值由调用方装配）
     *
     * @param entity 表单数据实体
     * @return 表单数据视图对象
     */
    @Mapping(target = "fields", ignore = true)
    FormDataVO toDataVo(FormData entity);

    /**
     * 字段配置实体转视图对象（选项由调用方装配）
     *
     * @param config 字段配置实体
     * @return 字段配置视图对象
     */
    @Mapping(target = "columnCode", ignore = true)
    @Mapping(target = "dataType", ignore = true)
    @Mapping(target = "maxLength", ignore = true)
    @Mapping(target = "options", ignore = true)
    @Mapping(target = "required", ignore = true)
    FormFieldConfigVO toFieldConfigVo(FormFieldConfig config);

    /**
     * 字段选项实体转视图对象
     *
     * @param option 选项实体
     * @return 选项视图对象
     */
    FormFieldOptionVO toOptionVo(FormFieldOption option);

    /**
     * 表单定义实体转视图对象（字段列表由调用方装配）
     *
     * @param entity 表单定义实体
     * @return 表单定义视图对象
     */
    @Mapping(target = "fields", ignore = true)
    FormDefinitionVO toDefinitionVo(FormDefinition entity);

    /**
     * 表单注册表实体转视图对象
     *
     * @param entity 注册表实体
     * @return 注册表视图对象
     */
    FormTableRegistryVO toRegistryVo(FormTableRegistry entity);

    /**
     * 表单物理表定义实体转视图对象（列元数据由调用方装配）
     *
     * @param entity 物理表定义实体
     * @return 物理表定义视图对象
     */
    @Mapping(target = "columns", ignore = true)
    FormTableDefinitionVO toTableDefinitionVo(FormTableDefinition entity);

    /**
     * 列元数据实体转视图对象
     *
     * @param entity 列元数据实体
     * @return 列元数据视图对象
     */
    FormColumnMetaVO toColumnMetaVo(FormColumnMeta entity);

    /**
     * 复制字段配置实体（表单复制场景：主键与审计字段由调用方清空后重新落库）
     *
     * @param source 源字段配置
     * @return 新字段配置实体（同字段拷贝）
     */
    FormFieldConfig toNewConfig(FormFieldConfig source);
}
