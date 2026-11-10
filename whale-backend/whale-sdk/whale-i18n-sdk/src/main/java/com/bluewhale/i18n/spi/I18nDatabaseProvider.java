package com.bluewhale.i18n.spi;

/**
 * 国际化数据源接口占位（SPI 扩展点）
 * 用于从数据库读取国际化文案
 *
 * @author BlueWhale
 */
public interface I18nDatabaseProvider {

    /**
     * 从数据库获取国际化消息
     *
     * @param code   消息编码
     * @param locale 语言环境
     * @return 消息文本，未找到返回 null
     */
    String findMessage(String code, String locale);
}
