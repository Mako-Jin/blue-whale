package com.bluewhale.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * SM4 国密对称加密工具
 * <p>按 GB/T 32907-2016 以纯 JDK 实现分组加解密，工作模式为 CBC + PKCS#7 填充，
 * 每次加密生成 16 字节随机 IV 并前置于密文，密钥以 32 位十六进制字符串表示。</p>
 * <p>不依赖 BouncyCastle，便于在仅含 JDK 的运行环境使用；解密时通过重新加密比对做完整性校验。</p>
 *
 * @author BlueWhale
 */
public final class Sm4Crypto {

    /** 分组长度（字节） */
    private static final int BLOCK_SIZE = 16;

    /** 轮数 */
    private static final int ROUND_COUNT = 32;

    /** 32 位字包含的字节数 */
    private static final int WORD_COUNT = 4;

    /** 32 位字包含的位数 */
    private static final int WORD_BITS = Integer.SIZE;

    /** 单字节掩码 */
    private static final int BYTE_MASK = 0xFF;

    /** 位移 8 位 */
    private static final int SHIFT_8 = 8;

    /** 位移 16 位 */
    private static final int SHIFT_16 = 16;

    /** 位移 24 位 */
    private static final int SHIFT_24 = 24;

    /** 密钥十六进制长度 */
    private static final int KEY_HEX_LENGTH = BLOCK_SIZE * 2;

    /** 系统固定参数 FK */
    private static final int[] FK = {0xA3B1BAC6, 0x56AA3350, 0x677D9197, 0xB27022DC};

    /** S 盒（256 字节，十六进制串按字节顺序排列） */
    private static final String SBOX_HEX = "d690e9fecce13db716b614c228fb2c05"
            + "2b679a762abe04c3aa44132649860699"
            + "9c4250f491ef987a33540b43edcfac62"
            + "e4b31ca9c908e89580df94fa758f3fa6"
            + "4707a7fcf37317ba83593c19e6854fa8"
            + "686b81b27164da8bf8eb0f4b70569d35"
            + "1e240e5e6358d1a225227c3b01217887"
            + "d40046579fd327524c3602e7a0c4c89e"
            + "eabf8ad240c738b5a3f7f2cef96115a1"
            + "e0ae5da49b341a55ad933230f58cb1e3"
            + "1df6e22e8266ca60c02923ab0d534e6f"
            + "d5db3745defd8e2f03ff6a726d6c5b51"
            + "8d1baf92bbddbc7f11d95c411f105ad8"
            + "0ac13188a5cd7bbd2d74d012b8e5b4b0"
            + "8969974a0c96777e65b9f109c56ec684"
            + "18f07dec3adc4d2079ee5f3ed7cb3948";

    /** 十六进制工具（需先于 SBOX 初始化） */
    private static final HexFormat HEX = HexFormat.of();

    /** 随机源 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** S 盒查表 */
    private static final int[] SBOX = buildSbox();

    /** 系统固定参数 CK（32 个） */
    private static final int[] CK = buildCk();

    /**
     * 私有构造器，禁止实例化
     */
    private Sm4Crypto() {
    }

    /**
     * 生成随机 SM4 密钥（32 位十六进制）
     *
     * @return 十六进制密钥
     */
    public static String generateKey() {
        byte[] key = new byte[BLOCK_SIZE];
        RANDOM.nextBytes(key);
        return HEX.formatHex(key);
    }

    /**
     * 加密文本
     *
     * @param plaintext 明文（UTF-8）
     * @param hexKey    32 位十六进制密钥
     * @return 十六进制密文（IV 前置）
     */
    public static String encrypt(String plaintext, String hexKey) {
        if (plaintext == null) {
            throw new IllegalArgumentException("明文不能为空");
        }
        return HEX.formatHex(encrypt(plaintext.getBytes(StandardCharsets.UTF_8), parseKey(hexKey)));
    }

    /**
     * 解密文本
     *
     * @param ciphertext 十六进制密文（IV 前置）
     * @param hexKey     32 位十六进制密钥
     * @return 明文
     */
    public static String decrypt(String ciphertext, String hexKey) {
        byte[] plain = decrypt(parseHex(ciphertext), parseKey(hexKey));
        return new String(plain, StandardCharsets.UTF_8);
    }

    /**
     * 加密字节数组
     *
     * @param plaintext 明文
     * @param key       16 字节密钥
     * @return 密文（16 字节 IV 前置）
     */
    public static byte[] encrypt(byte[] plaintext, byte[] key) {
        if (plaintext == null) {
            throw new IllegalArgumentException("明文不能为空");
        }
        validateKey(key);
        byte[] iv = new byte[BLOCK_SIZE];
        RANDOM.nextBytes(iv);
        byte[] encrypted = cbcEncrypt(plaintext, key, iv);
        byte[] result = new byte[BLOCK_SIZE + encrypted.length];
        System.arraycopy(iv, 0, result, 0, BLOCK_SIZE);
        System.arraycopy(encrypted, 0, result, BLOCK_SIZE, encrypted.length);
        return result;
    }

    /**
     * 解密字节数组
     *
     * @param ciphertext 密文（16 字节 IV 前置）
     * @param key        16 字节密钥
     * @return 明文
     */
    public static byte[] decrypt(byte[] ciphertext, byte[] key) {
        validateKey(key);
        if (ciphertext == null || ciphertext.length <= BLOCK_SIZE
                || (ciphertext.length - BLOCK_SIZE) % BLOCK_SIZE != 0) {
            throw new IllegalArgumentException("SM4 密文长度非法");
        }
        byte[] iv = Arrays.copyOf(ciphertext, BLOCK_SIZE);
        byte[] body = Arrays.copyOfRange(ciphertext, BLOCK_SIZE, ciphertext.length);
        int[] decryptKeys = reverseKeys(expandRoundKeys(key));
        byte[] plain = new byte[body.length];
        byte[] previous = iv;
        for (int offset = 0; offset < body.length; offset += BLOCK_SIZE) {
            byte[] block = Arrays.copyOfRange(body, offset, offset + BLOCK_SIZE);
            byte[] decrypted = cryptBlock(decryptKeys, block);
            xorInPlace(decrypted, previous);
            System.arraycopy(decrypted, 0, plain, offset, BLOCK_SIZE);
            previous = block;
        }
        byte[] unpadded = unpad(plain);
        verifyIntegrity(unpadded, key, iv, body);
        return unpadded;
    }

    /**
     * 单分组加密（ECB 语义，仅用于标准向量校验）
     *
     * @param key   16 字节密钥
     * @param block 16 字节分组
     * @return 加密后的分组
     */
    static byte[] encryptBlock(byte[] key, byte[] block) {
        validateKey(key);
        if (block == null || block.length != BLOCK_SIZE) {
            throw new IllegalArgumentException("分组长度必须为 16 字节");
        }
        return cryptBlock(expandRoundKeys(key), block);
    }

    /**
     * CBC 加密（含 PKCS#7 填充）
     *
     * @param plaintext 明文
     * @param key       密钥
     * @param iv        初始向量
     * @return 密文
     */
    private static byte[] cbcEncrypt(byte[] plaintext, byte[] key, byte[] iv) {
        byte[] padded = pad(plaintext);
        int[] roundKeys = expandRoundKeys(key);
        byte[] output = new byte[padded.length];
        byte[] previous = iv;
        for (int offset = 0; offset < padded.length; offset += BLOCK_SIZE) {
            byte[] block = Arrays.copyOfRange(padded, offset, offset + BLOCK_SIZE);
            xorInPlace(block, previous);
            byte[] encrypted = cryptBlock(roundKeys, block);
            System.arraycopy(encrypted, 0, output, offset, BLOCK_SIZE);
            previous = encrypted;
        }
        return output;
    }

    /**
     * 分组加解密核心（轮密钥顺序决定加密或解密）
     *
     * @param roundKeys 轮密钥
     * @param block     16 字节分组
     * @return 变换后的分组
     */
    private static byte[] cryptBlock(int[] roundKeys, byte[] block) {
        int[] state = new int[ROUND_COUNT + WORD_COUNT];
        for (int index = 0; index < WORD_COUNT; index++) {
            state[index] = readWord(block, index * WORD_COUNT);
        }
        for (int round = 0; round < ROUND_COUNT; round++) {
            int mixed = state[round + 1] ^ state[round + 2] ^ state[round + 3] ^ roundKeys[round];
            state[round + WORD_COUNT] = state[round] ^ roundTransform(mixed);
        }
        byte[] output = new byte[BLOCK_SIZE];
        for (int index = 0; index < WORD_COUNT; index++) {
            writeWord(output, index * WORD_COUNT, state[ROUND_COUNT + WORD_COUNT - 1 - index]);
        }
        return output;
    }

    /**
     * 扩展轮密钥
     *
     * @param key 16 字节密钥
     * @return 32 个轮密钥
     */
    private static int[] expandRoundKeys(byte[] key) {
        int[] state = new int[ROUND_COUNT + WORD_COUNT];
        for (int index = 0; index < WORD_COUNT; index++) {
            state[index] = readWord(key, index * WORD_COUNT) ^ FK[index];
        }
        int[] roundKeys = new int[ROUND_COUNT];
        for (int round = 0; round < ROUND_COUNT; round++) {
            int mixed = state[round + 1] ^ state[round + 2] ^ state[round + 3] ^ CK[round];
            state[round + WORD_COUNT] = state[round] ^ keyTransform(mixed);
            roundKeys[round] = state[round + WORD_COUNT];
        }
        return roundKeys;
    }

    /**
     * 反转轮密钥顺序（解密使用）
     *
     * @param roundKeys 加密轮密钥
     * @return 解密轮密钥
     */
    private static int[] reverseKeys(int[] roundKeys) {
        int[] reversed = new int[roundKeys.length];
        for (int index = 0; index < roundKeys.length; index++) {
            reversed[index] = roundKeys[roundKeys.length - 1 - index];
        }
        return reversed;
    }

    /**
     * 轮函数合成变换 T
     *
     * @param value 32 位字
     * @return 变换结果
     */
    private static int roundTransform(int value) {
        int substituted = substitute(value);
        return substituted ^ rotateLeft(substituted, 2) ^ rotateLeft(substituted, 10)
                ^ rotateLeft(substituted, 18) ^ rotateLeft(substituted, 24);
    }

    /**
     * 密钥扩展变换 T'
     *
     * @param value 32 位字
     * @return 变换结果
     */
    private static int keyTransform(int value) {
        int substituted = substitute(value);
        return substituted ^ rotateLeft(substituted, 13) ^ rotateLeft(substituted, 23);
    }

    /**
     * S 盒代换（逐字节查表）
     *
     * @param value 32 位字
     * @return 代换结果
     */
    private static int substitute(int value) {
        return (SBOX[(value >>> SHIFT_24) & BYTE_MASK] << SHIFT_24)
                | (SBOX[(value >>> SHIFT_16) & BYTE_MASK] << SHIFT_16)
                | (SBOX[(value >>> SHIFT_8) & BYTE_MASK] << SHIFT_8)
                | SBOX[value & BYTE_MASK];
    }

    /**
     * 32 位循环左移
     *
     * @param value 原值
     * @param shift 位移位数
     * @return 移位结果
     */
    private static int rotateLeft(int value, int shift) {
        return (value << shift) | (value >>> (WORD_BITS - shift));
    }

    /**
     * 从字节数组读取 32 位字（大端）
     *
     * @param data   字节数组
     * @param offset 起始下标
     * @return 32 位字
     */
    private static int readWord(byte[] data, int offset) {
        return ((data[offset] & BYTE_MASK) << SHIFT_24)
                | ((data[offset + 1] & BYTE_MASK) << SHIFT_16)
                | ((data[offset + 2] & BYTE_MASK) << SHIFT_8)
                | (data[offset + 3] & BYTE_MASK);
    }

    /**
     * 向字节数组写入 32 位字（大端）
     *
     * @param data   字节数组
     * @param offset 起始下标
     * @param value  32 位字
     */
    private static void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> SHIFT_24);
        data[offset + 1] = (byte) (value >>> SHIFT_16);
        data[offset + 2] = (byte) (value >>> SHIFT_8);
        data[offset + 3] = (byte) value;
    }

    /**
     * PKCS#7 填充
     *
     * @param plaintext 明文
     * @return 填充后的数据
     */
    private static byte[] pad(byte[] plaintext) {
        int padding = BLOCK_SIZE - plaintext.length % BLOCK_SIZE;
        byte[] padded = Arrays.copyOf(plaintext, plaintext.length + padding);
        Arrays.fill(padded, plaintext.length, padded.length, (byte) padding);
        return padded;
    }

    /**
     * PKCS#7 去填充
     *
     * @param plaintext 填充数据
     * @return 去填充数据
     */
    private static byte[] unpad(byte[] plaintext) {
        int padding = plaintext[plaintext.length - 1] & BYTE_MASK;
        if (padding < 1 || padding > BLOCK_SIZE || padding > plaintext.length) {
            throw new IllegalArgumentException("SM4 解密失败：填充非法（密钥错误或密文被篡改）");
        }
        for (int index = plaintext.length - padding; index < plaintext.length; index++) {
            if ((plaintext[index] & BYTE_MASK) != padding) {
                throw new IllegalArgumentException("SM4 解密失败：填充非法（密钥错误或密文被篡改）");
            }
        }
        return Arrays.copyOf(plaintext, plaintext.length - padding);
    }

    /**
     * 完整性校验：用相同密钥与 IV 重新加密比对密文
     *
     * @param plaintext 去填充后的明文
     * @param key       密钥
     * @param iv        初始向量
     * @param body      原始密文主体
     */
    private static void verifyIntegrity(byte[] plaintext, byte[] key, byte[] iv, byte[] body) {
        byte[] reEncrypted = cbcEncrypt(plaintext, key, iv);
        if (!Arrays.equals(reEncrypted, body)) {
            throw new IllegalArgumentException("SM4 解密失败：完整性校验不通过（密钥错误或密文被篡改）");
        }
    }

    /**
     * 解析十六进制密钥
     *
     * @param hexKey 32 位十六进制
     * @return 密钥字节
     */
    private static byte[] parseKey(String hexKey) {
        if (hexKey == null || hexKey.length() != KEY_HEX_LENGTH) {
            throw new IllegalArgumentException("SM4 密钥必须为 32 位十六进制字符串");
        }
        try {
            return HEX.parseHex(hexKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("SM4 密钥含非法十六进制字符", ex);
        }
    }

    /**
     * 解析十六进制密文
     *
     * @param ciphertext 十六进制文本
     * @return 密文字节
     */
    private static byte[] parseHex(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("密文不能为空");
        }
        try {
            return HEX.parseHex(ciphertext);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("SM4 密文含非法十六进制字符", ex);
        }
    }

    /**
     * 校验密钥长度
     *
     * @param key 密钥
     */
    private static void validateKey(byte[] key) {
        if (key == null || key.length != BLOCK_SIZE) {
            throw new IllegalArgumentException("SM4 密钥必须为 16 字节");
        }
    }

    /**
     * 分组异或（原地）
     *
     * @param target 目标分组
     * @param source 异或源
     */
    private static void xorInPlace(byte[] target, byte[] source) {
        for (int index = 0; index < target.length; index++) {
            target[index] ^= source[index];
        }
    }

    /**
     * 构建 S 盒查表
     *
     * @return 256 项查表
     */
    private static int[] buildSbox() {
        byte[] raw = HEX.parseHex(SBOX_HEX);
        int[] table = new int[raw.length];
        for (int index = 0; index < raw.length; index++) {
            table[index] = raw[index] & BYTE_MASK;
        }
        return table;
    }

    /**
     * 按标准公式构建 CK 常量
     *
     * @return 32 个 CK
     */
    private static int[] buildCk() {
        int[] ck = new int[ROUND_COUNT];
        for (int round = 0; round < ROUND_COUNT; round++) {
            int base = round * WORD_COUNT;
            ck[round] = (ckByte(base) << SHIFT_24) | (ckByte(base + 1) << SHIFT_16)
                    | (ckByte(base + 2) << SHIFT_8) | ckByte(base + 3);
        }
        return ck;
    }

    /**
     * CK 分量计算：ck(i, j) = (4i + j) * 7 mod 256
     *
     * @param index 分量序号
     * @return 单字节值
     */
    private static int ckByte(int index) {
        return (index * 7) & BYTE_MASK;
    }
}
