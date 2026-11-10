package com.bluewhale.i18n.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.i18n.entity.SysI18nMessage;

/**
 * 国际化消息 Mapper
 *
 * <p>由接入方 {@code @MapperScan("com.bluewhale.**.mapper")}（whale-spring-boot-starter 的
 * {@code MybatisPlusConfig}）统一扫描注册；SDK 自身不再注册该 Mapper，避免同名 Bean 冲突。
 *
 * @author BlueWhale
 */
public interface SysI18nMessageMapper extends BaseMapper<SysI18nMessage> {
}
