package com.bluewhale.i18n;

import com.bluewhale.i18n.spi.I18nDatabaseProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用内存版数据库 Provider（模拟 sys_i18n_message 表）
 *
 * @author BlueWhale
 */
public class InMemoryI18nDatabaseProvider implements I18nDatabaseProvider {

    private final Map<String, String> messages = new ConcurrentHashMap<>();

    public InMemoryI18nDatabaseProvider() {
    }

    public InMemoryI18nDatabaseProvider(Map<String, String> initial) {
        messages.putAll(initial);
    }

    /**
     * key 格式：code|locale
     */
    public void put(String code, String locale, String content) {
        messages.put(key(code, locale), content);
    }

    @Override
    public String findMessage(String code, String locale) {
        return messages.get(key(code, locale));
    }

    private String key(String code, String locale) {
        return code + "|" + locale;
    }
}
