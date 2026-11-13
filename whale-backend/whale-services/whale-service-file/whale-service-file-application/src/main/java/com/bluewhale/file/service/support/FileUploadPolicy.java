package com.bluewhale.file.service.support;

import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.model.UploadLimits;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * 按权限码解析生效上传上限的领域服务（G1 大文件上传策略）。
 *
 * <p>只读取网关可信注入的 {@code X-Permission-Codes}（客户端伪造的同名头在网关侧已被剥离），
 * 在同一请求命中多条规则时取单文件上限最大的一条；未命中任何规则时回落
 * {@code whale.file.max-size-bytes} / {@code whale.file.quota-bytes} 全局默认值，
 * 因此普通上传仍保持 50MB / 1GB 的默认约束。</p>
 *
 * @author BlueWhale
 */
@Component
public class FileUploadPolicy {

    /** 内置超管角色码：与网关权限码头口径一致，命中时取全部规则中的最大上限 */
    private static final String SUPER_ADMIN = "super_admin";

    private final FileProperties fileProperties;

    /**
     * 构造上传策略服务
     *
     * @param fileProperties 文件服务配置（读取 {@code whale.file.upload-policy.*}）
     */
    public FileUploadPolicy(FileProperties fileProperties) {
        this.fileProperties = fileProperties;
    }

    /**
     * 按调用者权限码解析生效上限与配额
     *
     * @param permissionCodes 调用者权限码集合（网关可信注入），可为 {@code null}
     * @return 生效上传上限；策略关闭或无命中时返回全局默认值
     */
    public UploadLimits resolve(Collection<String> permissionCodes) {
        if (!fileProperties.getUploadPolicy().isEnabled()
                || permissionCodes == null || permissionCodes.isEmpty()) {
            return defaults();
        }
        boolean superAdmin = permissionCodes.contains(SUPER_ADMIN);
        FileProperties.Rule matched = null;
        for (FileProperties.Rule rule : fileProperties.getUploadPolicy().getRules()) {
            if (rule == null || !StringUtils.hasText(rule.getPermission()) || rule.getMaxSizeBytes() <= 0) {
                continue;
            }
            if (!superAdmin && !permissionCodes.contains(rule.getPermission())) {
                continue;
            }
            if (matched == null || rule.getMaxSizeBytes() > matched.getMaxSizeBytes()) {
                matched = rule;
            }
        }
        if (matched == null) {
            return defaults();
        }
        long quota = matched.getQuotaBytes() == null
                ? fileProperties.getQuotaBytes() : matched.getQuotaBytes();
        return new UploadLimits(matched.getMaxSizeBytes(), quota);
    }

    /**
     * 全局默认上限（未命中任何权限码规则时使用）
     *
     * @return 默认上传上限
     */
    public UploadLimits defaults() {
        return new UploadLimits(fileProperties.getMaxSizeBytes(), fileProperties.getQuotaBytes());
    }
}
