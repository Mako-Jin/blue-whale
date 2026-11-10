package com.bluewhale.i18n.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.i18n.entity.SysI18nMessage;
import com.bluewhale.i18n.mapper.SysI18nMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link I18nDatabaseProvider} 默认实现：基于 MyBatis-Plus 查询 sys_i18n_message
 *
 * <p>(message_key, locale) 唯一；逻辑删除由 @TableLogic 自动过滤。
 *
 * @author BlueWhale
 */
@Slf4j
@RequiredArgsConstructor
public class DbI18nDatabaseProvider implements I18nDatabaseProvider {

    private final SysI18nMessageMapper sysI18nMessageMapper;

    @Override
    public String findMessage(String code, String locale) {
        SysI18nMessage message = sysI18nMessageMapper.selectOne(new LambdaQueryWrapper<SysI18nMessage>()
                .eq(SysI18nMessage::getMessageKey, code)
                .eq(SysI18nMessage::getLocale, locale)
                .last("LIMIT 1"));
        return message == null ? null : message.getMessageContent();
    }
}
