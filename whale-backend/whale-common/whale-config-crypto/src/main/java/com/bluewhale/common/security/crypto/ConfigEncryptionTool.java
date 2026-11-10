package com.bluewhale.common.security.crypto;

/**
 * 配置密文工具（REQ-SEC-020）：在命令行生成或验证 {@code ENC(...)} 密文，
 * 供 {@code scripts/security/encrypt-secret.ps1} 调用。
 *
 * <p>用法：{@code java ... ConfigEncryptionTool encrypt <明文>} 或
 * {@code ... decrypt ENC(<密文>)}；密钥只从环境变量 {@code WHALE_CONFIG_ENC_KEY} 读取，
 * 不接受命令行明文密钥，避免出现在进程列表与历史记录中。</p>
 *
 * @author BlueWhale
 */
public final class ConfigEncryptionTool {

    /** 加密模式 */
    private static final String MODE_ENCRYPT = "encrypt";
    /** 解密模式 */
    private static final String MODE_DECRYPT = "decrypt";

    /**
     * 私有构造器：工具类不允许实例化
     */
    private ConfigEncryptionTool() {
    }

    /**
     * 命令行入口
     *
     * @param args 参数：{@code <encrypt|decrypt> <值>}
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("用法: ConfigEncryptionTool <encrypt|decrypt> <值>");
            System.err.println("密钥来源: 环境变量 " + ConfigEncryptionSettings.ENV_KEY);
            System.exit(2);
        }
        ConfigValueCipher cipher = new ConfigValueCipher(
                ConfigEncryptionSettings.of(System.getenv(ConfigEncryptionSettings.ENV_KEY)));
        if (MODE_ENCRYPT.equals(args[0])) {
            System.out.println(cipher.encrypt(args[1]));
            return;
        }
        if (MODE_DECRYPT.equals(args[0])) {
            System.out.println(cipher.decryptIfNeeded("command-line", args[1]));
            return;
        }
        System.err.println("不支持的模式: " + args[0] + "（仅支持 encrypt / decrypt）");
        System.exit(2);
    }
}
