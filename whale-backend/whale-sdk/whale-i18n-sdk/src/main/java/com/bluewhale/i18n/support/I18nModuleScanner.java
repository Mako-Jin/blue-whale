package com.bluewhale.i18n.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 国际化模块文案扫描器
 *
 * <p>扫描类路径下所有 {@code i18n/<模块>/messages.properties}，将其转换为
 * {@link org.springframework.context.support.ReloadableResourceBundleMessageSource}
 * 可识别的 basename，实现「每个服务/jar 自带文案」的回退能力。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class I18nModuleScanner {

    /**
     * 文案 basename 片段（去掉 .properties 后缀）
     */
    private static final String MESSAGES_BASENAME = "messages";

    /**
     * 类路径前缀
     */
    private static final String CLASSPATH_PREFIX = "classpath:";

    /**
     * i18n 目录标识
     */
    private static final String I18N_DIR = "i18n/";

    private final String scanPattern;

    private final String defaultBasename;

    private final boolean scanEnabled;

    /**
     * 构造扫描器
     *
     * @param scanPattern     扫描模式，如 classpath*:i18n/**&#47;messages.properties
     * @param defaultBasename 全局兜底 basename
     * @param scanEnabled     是否启用扫描
     */
    public I18nModuleScanner(String scanPattern, String defaultBasename, boolean scanEnabled) {
        this.scanPattern = scanPattern;
        this.defaultBasename = defaultBasename;
        this.scanEnabled = scanEnabled;
    }

    /**
     * 执行扫描
     *
     * @return 去重且排序后的 basename 列表，末尾追加全局兜底 basename
     */
    public List<String> scan() {
        TreeSet<String> scanned = new TreeSet<>();
        if (scanEnabled && StringUtils.hasText(scanPattern)) {
            try {
                Resource[] resources = new PathMatchingResourcePatternResolver().getResources(scanPattern);
                for (Resource resource : resources) {
                    String basename = toBasename(resource);
                    if (basename != null) {
                        scanned.add(basename);
                    }
                }
            } catch (IOException ex) {
                log.warn("[whale-i18n] 扫描模块文案失败，仅使用全局兜底文案：{}", ex.getMessage());
            }
        }
        List<String> basenames = new ArrayList<>(scanned);
        if (StringUtils.hasText(defaultBasename) && !basenames.contains(defaultBasename)) {
            basenames.add(defaultBasename);
        }
        log.info("[whale-i18n] 扫描到 {} 个模块文案源，兜底 basename={}", scanned.size(), defaultBasename);
        if (log.isDebugEnabled()) {
            log.debug("[whale-i18n] 模块文案 basename 清单：{}", basenames);
        }
        return basenames;
    }

    /**
     * 将资源路径转换为 basename
     *
     * @param resource 文案资源
     * @return 形如 classpath:i18n/auth/messages 的 basename；无法识别时返回 null
     */
    static String toBasename(Resource resource) {
        try {
            String url = resource.getURL().toString();
            int start = url.lastIndexOf(I18N_DIR);
            int end = url.lastIndexOf(MESSAGES_BASENAME);
            if (start < 0 || end <= start) {
                return null;
            }
            return CLASSPATH_PREFIX + url.substring(start, end + MESSAGES_BASENAME.length());
        } catch (IOException ex) {
            log.warn("[whale-i18n] 解析模块文案路径失败：{}", ex.getMessage());
            return null;
        }
    }
}
