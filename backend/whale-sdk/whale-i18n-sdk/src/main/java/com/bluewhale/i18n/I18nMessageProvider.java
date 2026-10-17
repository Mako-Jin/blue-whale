package com.bluewhale.i18n;

/**
 * 国际化 SDK 核心接口占位
 *
 * <p>优先级规则：数据库 > 属性文件 > 默认值
 * 1. 首先尝试从数据库读取国际化文案（支持动态更新）
 * 2. 数据库未找到时，回退到 classpath 下的 messages_*.properties 属性文件
 * 3. 属性文件也未找到时，返回调用方传入的默认值
 *
 * @author BlueWhale
 */
public interface I18nMessageProvider {

    /**
     * 获取国际化消息
     *
     * @param code         消息编码
     * @param locale       语言环境（如 zh_CN, en_US）
     * @param defaultValue 默认值
     * @param params       消息参数
     * @return 国际化后的消息文本
     */
    String getMessage(String code, String locale, String defaultValue, Object... params);
}
