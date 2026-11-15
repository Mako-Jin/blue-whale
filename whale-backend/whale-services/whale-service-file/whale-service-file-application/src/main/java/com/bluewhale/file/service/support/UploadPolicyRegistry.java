package com.bluewhale.file.service.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 上传策略规则注册表：保存当前生效的按权限码规则快照，支持运行时整表替换。
 *
 * <p>初始快照在构造时从 {@code whale.file.upload-policy.rules} 深拷贝，之后配置对象的变化
 * 不再影响注册表；上传路径每次请求只读 {@link #rules()}，替换通过
 * {@link AtomicReference} 原子切换，读侧无需加锁。</p>
 *
 * <p>热更新入口只接受合法规则（权限码非空白、单文件上限为正、配额为空或非负），
 * 校验失败抛 {@code PARAM_ERROR} 且保留旧快照；启动配置中的非法规则仍由
 * {@link FileUploadPolicy} 在解析阶段跳过，不阻塞启动。</p>
 *
 * <p>规则只保存在当前实例内存，多副本部署时各实例需分别更新（或接入配置中心广播），
 * 重启后回到配置文件中的规则。</p>
 *
 * @author BlueWhale
 */
@Component
public class UploadPolicyRegistry {

    private final AtomicReference<List<FileProperties.Rule>> rules;

    /**
     * 构造注册表：以配置中的规则深拷贝作为初始快照
     *
     * @param fileProperties 文件服务配置（读取 {@code whale.file.upload-policy.rules}）
     */
    public UploadPolicyRegistry(FileProperties fileProperties) {
        this.rules = new AtomicReference<>(copyOf(fileProperties.getUploadPolicy().getRules()));
    }

    /**
     * 读取当前生效规则快照（不可变，可安全并发访问）
     *
     * @return 不可变规则列表
     */
    public List<FileProperties.Rule> rules() {
        return rules.get();
    }

    /**
     * 整表替换规则快照
     *
     * @param newRules 新规则列表，不能为空且每条规则必须合法
     * @return 替换后的不可变规则快照
     * @throws BizException 规则列表为空、条目为 {@code null}、权限码空白、单文件上限非正或配额为负时抛出
     */
    public List<FileProperties.Rule> replace(List<FileProperties.Rule> newRules) {
        List<FileProperties.Rule> snapshot = validateAndCopy(newRules);
        rules.set(snapshot);
        return snapshot;
    }

    /**
     * 深拷贝配置来源的规则，容错处理 {@code null} 列表与条目
     *
     * @param source 配置中的规则列表，可为 {@code null}
     * @return 不可变规则快照
     */
    private static List<FileProperties.Rule> copyOf(List<FileProperties.Rule> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<FileProperties.Rule> copies = new ArrayList<>(source.size());
        for (FileProperties.Rule rule : source) {
            if (rule != null) {
                copies.add(copyOf(rule));
            }
        }
        return List.copyOf(copies);
    }

    /**
     * 校验并深拷贝热更新传入的规则
     *
     * @param newRules 新规则列表
     * @return 不可变规则快照
     * @throws BizException 任一校验不通过时抛出
     */
    private static List<FileProperties.Rule> validateAndCopy(List<FileProperties.Rule> newRules) {
        if (newRules == null || newRules.isEmpty()) {
            throw paramError("上传策略规则不能为空");
        }
        List<FileProperties.Rule> copies = new ArrayList<>(newRules.size());
        for (FileProperties.Rule rule : newRules) {
            if (rule == null) {
                throw paramError("上传策略规则条目不能为空");
            }
            if (!StringUtils.hasText(rule.getPermission())) {
                throw paramError("上传策略权限码不能为空");
            }
            if (rule.getMaxSizeBytes() <= 0) {
                throw paramError("上传策略单文件上限必须大于 0");
            }
            if (rule.getQuotaBytes() != null && rule.getQuotaBytes() < 0) {
                throw paramError("上传策略配额不能为负数");
            }
            copies.add(copyOf(rule));
        }
        return List.copyOf(copies);
    }

    /**
     * 复制单条规则并去除权限码首尾空白
     *
     * @param rule 源规则
     * @return 规则副本
     */
    private static FileProperties.Rule copyOf(FileProperties.Rule rule) {
        String permission = rule.getPermission() == null ? null : rule.getPermission().trim();
        return FileProperties.Rule.of(permission, rule.getMaxSizeBytes(), rule.getQuotaBytes());
    }

    /**
     * 构造参数错误异常
     *
     * @param message 错误信息
     * @return 参数错误异常
     */
    private static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
