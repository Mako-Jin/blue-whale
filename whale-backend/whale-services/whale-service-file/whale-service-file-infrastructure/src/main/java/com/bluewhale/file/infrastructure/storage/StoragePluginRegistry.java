package com.bluewhale.file.infrastructure.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.storage.FileStorage;
import com.bluewhale.file.domain.storage.StorageHealth;
import com.bluewhale.file.domain.storage.StoragePlugin;
import com.bluewhale.file.domain.storage.StoragePluginCatalog;
import com.bluewhale.file.domain.storage.StoragePluginInfo;
import com.bluewhale.file.domain.storage.StorageObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 存储插件注册表：收集类路径下全部 {@link StoragePlugin}，按 {@code whale.file.storage.type} 选择活动插件。
 *
 * <p>本类同时适配 {@link FileStorage} 与 {@link StoragePluginCatalog} 两个领域端口：业务层注入
 * {@code FileStorage} 即透明使用当前活动插件，注入 {@code StoragePluginCatalog} 即读取插件清单；
 * 新增存储介质只需新增插件模块并放入类路径，无需改动业务代码。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class StoragePluginRegistry implements FileStorage, StoragePluginCatalog {

    /** 加密对象魔数：读取时据此区分密文与存量明文 */
    private static final byte[] ENCRYPTION_MAGIC = "WHALEENC".getBytes(StandardCharsets.US_ASCII);

    /** GCM nonce 长度（字节） */
    private static final int NONCE_LENGTH = 12;

    /** 加密对象头长度：魔数 + nonce */
    private static final int HEADER_LENGTH = ENCRYPTION_MAGIC.length + NONCE_LENGTH;

    /** GCM 认证标签位数 */
    private static final int GCM_TAG_BITS = 128;

    /** 加密算法 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** 插件类型到插件实例的映射（按类型字典序，便于可观测性输出稳定） */
    private final Map<String, StoragePlugin> plugins;

    /** 当前活动插件 */
    private final StoragePlugin active;

    /** 落盘加密密钥；{@code null} 表示未启用加密 */
    private final SecretKeySpec encryptionKey;

    /** nonce 随机源 */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 以容器内全部插件与文件服务配置构造注册表。
     *
     * @param pluginBeans 容器内全部存储插件 Bean
     * @param fileProperties 文件服务配置（读取活动插件类型）
     * @throws BizException 无任何插件、或配置的活动插件类型不存在时抛出 {@code SYSTEM_ERROR}
     */
    public StoragePluginRegistry(List<StoragePlugin> pluginBeans, FileProperties fileProperties) {
        this.plugins = pluginBeans.stream()
                .collect(Collectors.toMap(StoragePlugin::getType, plugin -> plugin,
                        (left, right) -> {
                            // 同类型插件重复注册时保留先注册者，但必须告警，避免排障时误以为后注册插件生效
                            log.warn("检测到重复的文件存储插件类型: {}，保留 {}，忽略 {}",
                                    left.getType(), left.getClass().getName(), right.getClass().getName());
                            return left;
                        }, TreeMap::new));
        if (plugins.isEmpty()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "file.storage.no-plugin-available",
                    "未找到任何文件存储插件");
        }
        String activeType = fileProperties.getStorage().getType();
        StoragePlugin selected = plugins.get(activeType);
        if (selected == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "file.storage.plugin-unavailable",
                    "未找到文件存储插件: " + activeType + "，可用插件: " + plugins.keySet(),
                    activeType, plugins.keySet());
        }
        this.active = selected;
        this.encryptionKey = resolveEncryptionKey(fileProperties.getStorage().getEncryption());
        log.info("文件存储插件已装配: active={}, available={}, encryption={}", activeType, plugins.keySet(),
                encryptionKey == null ? "off" : "on");
    }

    @Override
    public String activeType() {
        return active.getType();
    }

    @Override
    public List<StoragePluginInfo> plugins() {
        return plugins.values().stream().map(StoragePluginInfo::from).toList();
    }

    @Override
    public void store(String bucket, String objectKey, InputStream in) {
        active.store(bucket, objectKey, encryptionKey == null ? in : encryptingStream(in));
    }

    @Override
    public InputStream load(String bucket, String objectKey, String storageType) {
        InputStream raw = pluginFor(storageType).load(bucket, objectKey);
        return encryptionKey == null ? raw : decryptOrPlain(raw);
    }

    @Override
    public InputStream load(String bucket, String objectKey, long offset, long length, String storageType) {
        StoragePlugin plugin = pluginFor(storageType);
        if (encryptionKey == null) {
            return plugin.load(bucket, objectKey, offset, length);
        }
        byte[] header = probeHeader(plugin, bucket, objectKey);
        if (header == null) {
            // 存量明文对象：继续使用插件原生区间读取，避免从头跳过
            return plugin.load(bucket, objectKey, offset, length);
        }
        InputStream raw = plugin.load(bucket, objectKey);
        try {
            raw.skipNBytes(HEADER_LENGTH);
            InputStream decrypted = new CipherInputStream(raw, decryptCipher(header));
            decrypted.skipNBytes(offset);
            return new LimitedInputStream(decrypted, length);
        } catch (IOException e) {
            closeQuietly(raw);
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "file.storage.decrypt-failed", "文件解密失败");
        }
    }

    @Override
    public void delete(String bucket, String objectKey, String storageType) {
        pluginFor(storageType).delete(bucket, objectKey);
    }

    @Override
    public void move(String bucket, String objectKey, String targetBucket, String storageType) {
        pluginFor(storageType).move(bucket, objectKey, targetBucket);
    }

    @Override
    public List<StorageObject> listObjects(String storageType) {
        return pluginFor(storageType).listObjects();
    }

    @Override
    public String presignedDownloadUrl(String bucket, String objectKey, int expiresSeconds, String storageType) {
        StoragePlugin plugin = pluginFor(storageType);
        if (!plugin.supportsPresigned()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "file.storage.presigned-unsupported",
                    "当前存储插件不支持预签名下载: " + plugin.getType(), plugin.getType());
        }
        return plugin.presignedDownloadUrl(bucket, objectKey, expiresSeconds);
    }

    @Override
    public Map<String, StorageHealth> health() {
        Map<String, StorageHealth> result = new TreeMap<>();
        plugins.forEach((type, plugin) -> {
            try {
                StorageHealth health = plugin.health();
                result.put(type, health == null ? StorageHealth.down("插件未返回健康状态") : health);
            } catch (RuntimeException e) {
                // 单插件健康探测异常不得拖垮整体健康端点，降级为该插件 DOWN 并保留原因
                result.put(type, StorageHealth.down("健康检查异常: " + e.getMessage()));
            }
        });
        return result;
    }

    /**
     * 构造加密输入流：输出「魔数 + nonce + GCM 密文」，由插件读取落盘。
     *
     * @param in 原始内容流
     * @return 加密后的内容流
     * @throws BizException 加密算法或密钥不可用时抛出 {@code SYSTEM_ERROR}
     */
    private InputStream encryptingStream(InputStream in) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] header = new byte[HEADER_LENGTH];
            System.arraycopy(ENCRYPTION_MAGIC, 0, header, 0, ENCRYPTION_MAGIC.length);
            System.arraycopy(nonce, 0, header, ENCRYPTION_MAGIC.length, NONCE_LENGTH);
            return new SequenceInputStream(new ByteArrayInputStream(header), new CipherInputStream(in, cipher));
        } catch (GeneralSecurityException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "file.storage.encrypt-init-failed",
                    "文件加密初始化失败");
        }
    }

    /**
     * 识别对象头并按需解密：命中魔数返回解密流，否则回吐头部按明文返回。
     *
     * @param raw 原始对象流
     * @return 明文内容流
     * @throws BizException 读取失败时抛出 {@code SYSTEM_ERROR}
     */
    private InputStream decryptOrPlain(InputStream raw) {
        PushbackInputStream pin = new PushbackInputStream(raw, HEADER_LENGTH);
        try {
            byte[] header = pin.readNBytes(HEADER_LENGTH);
            if (isEncrypted(header)) {
                return new CipherInputStream(pin, decryptCipher(header));
            }
            if (header.length > 0) {
                pin.unread(header);
            }
            return pin;
        } catch (IOException e) {
            closeQuietly(pin);
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "file.storage.decrypt-failed", "文件解密失败");
        }
    }

    /**
     * 探测对象头是否为加密头；探测失败按明文处理，由后续读取暴露真实错误。
     *
     * @param plugin 目标插件
     * @param bucket 桶名称
     * @param objectKey 对象键
     * @return 加密头字节；明文对象返回 {@code null}
     */
    private byte[] probeHeader(StoragePlugin plugin, String bucket, String objectKey) {
        try (InputStream probe = plugin.load(bucket, objectKey, 0L, HEADER_LENGTH)) {
            byte[] header = probe.readNBytes(HEADER_LENGTH);
            return isEncrypted(header) ? header : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 判断对象头是否为加密格式。
     *
     * @param header 对象头字节
     * @return 加密对象返回 {@code true}
     */
    private static boolean isEncrypted(byte[] header) {
        return header.length == HEADER_LENGTH
                && Arrays.equals(Arrays.copyOf(header, ENCRYPTION_MAGIC.length), ENCRYPTION_MAGIC);
    }

    /**
     * 按对象头中的 nonce 构造解密 Cipher。
     *
     * @param header 加密对象头
     * @return 已初始化的解密 Cipher
     * @throws BizException 初始化解密失败时抛出 {@code SYSTEM_ERROR}
     */
    private Cipher decryptCipher(byte[] header) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] nonce = Arrays.copyOfRange(header, ENCRYPTION_MAGIC.length, HEADER_LENGTH);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "file.storage.decrypt-init-failed",
                    "文件解密初始化失败");
        }
    }

    /**
     * 解析落盘加密密钥：未启用返回 {@code null}；启用时校验 Base64 与密钥长度。
     *
     * @param encryption 加密配置
     * @return AES 密钥；未启用时返回 {@code null}
     * @throws BizException 密钥缺失或非法时抛出 {@code SYSTEM_ERROR}
     */
    private static SecretKeySpec resolveEncryptionKey(FileProperties.Encryption encryption) {
        if (encryption == null || !encryption.isEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(encryption.getKey())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "file.storage.encryption-key-required",
                    "启用落盘加密时必须配置 whale.file.storage.encryption.key");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryption.getKey().trim());
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                throw new IllegalArgumentException("密钥长度必须为 16/24/32 字节");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "file.storage.encryption-key-invalid",
                    "落盘加密密钥必须是 Base64 编码的 16/24/32 字节");
        }
    }

    /**
     * 静默关闭输入流。
     *
     * @param in 待关闭的输入流
     */
    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // 关闭失败不掩盖原始异常
        }
    }

    /**
     * 按长度截断的输入流：用于加密对象的区间读取。
     */
    private static final class LimitedInputStream extends FilterInputStream {

        /** 剩余可读字节数 */
        private long remaining;

        /**
         * 包装源输入流
         *
         * @param in 源输入流
         * @param limit 最大可读字节数
         */
        private LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = Math.max(limit, 0L);
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = super.read(bytes, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public long skip(long length) throws IOException {
            long skipped = super.skip(Math.min(length, remaining));
            remaining -= skipped;
            return skipped;
        }
    }

    /**
     * 按落盘时的插件类型选择插件：类型为空回落活动插件。
     *
     * @param storageType 落盘时的插件类型，可为 {@code null}
     * @return 插件实例
     * @throws BizException 类型对应的插件未装配时抛出 {@code SYSTEM_ERROR}
     */
    private StoragePlugin pluginFor(String storageType) {
        if (!StringUtils.hasText(storageType)) {
            return active;
        }
        StoragePlugin plugin = plugins.get(storageType.trim());
        if (plugin == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "file.storage.recorded-plugin-unavailable",
                    "文件记录的存储插件不可用: " + storageType + "，可用插件: " + plugins.keySet(),
                    storageType, plugins.keySet());
        }
        return plugin;
    }
}
