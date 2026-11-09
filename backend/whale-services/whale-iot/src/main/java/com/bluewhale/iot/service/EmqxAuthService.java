package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * EMQX 认证与连接事件服务（认证 Webhook 提供方）。
 *
 * <p>认证契约：{@code username = "{productCode}/{deviceName}"}，
 * {@code password = HMAC-SHA256(deviceSecret, username)} 十六进制小写。
 * 校验 fail-closed：任一环节不满足（设备不存在/禁用、产品停用、HMAC 不符）一律拒绝。</p>
 *
 * <p>连接事件：client.connected → 置在线；client.disconnected → 置离线。
 * 用户名解析失败的事件仅记日志不落库（脏数据不阻塞 Broker）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmqxAuthService {

    /** HMAC 算法 */
    private static final String HMAC_ALGO = "HmacSHA256";

    /** 用户名分隔符 */
    private static final String USERNAME_SEPARATOR = "/";

    private final IotDeviceMapper deviceMapper;

    private final IotProductMapper productMapper;

    private final IotShadowService shadowService;

    /**
     * 认证一台设备（EMQX HTTP Authentication 回调）。
     *
     * <p>返回 EMQX 约定形状：{@code {"result":"allow"}} / {@code {"result":"deny"}}，
     * 本方法只做判定，HTTP 形状由控制器组装。</p>
     *
     * @param username 用户名（{productCode}/{deviceName}）
     * @param password HMAC 口令
     * @return true 允许接入
     */
    public boolean authenticate(String username, String password) {
        ParsedUsername parsed = parseUsername(username);
        if (parsed == null || !StringUtils.hasText(password)) {
            return false;
        }
        IotProduct product = productMapper.selectOne(new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getCode, parsed.productCode())
                .last("LIMIT 1"));
        if (product == null || !IotProductService.STATUS_PUBLISHED.equals(product.getStatus())) {
            return false;
        }
        IotDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getProductId, product.getId())
                .eq(IotDevice::getDeviceName, parsed.deviceName())
                .last("LIMIT 1"));
        if (device == null) {
            return false;
        }
        if (!IotDeviceService.STATUS_ACTIVE.equals(device.getStatus())) {
            return false;
        }
        return hmacHex(device.getSecret(), username).equals(password);
    }

    /**
     * 处理连接事件（置在线 + 最近在线时间）。
     *
     * @param username 用户名（{productCode}/{deviceName}）
     */
    public void onConnected(String username) {
        ParsedUsername parsed = parseUsername(username);
        if (parsed == null) {
            log.warn("EMQX 连接事件用户名无法解析：{}", username);
            return;
        }
        IotDevice device = findDevice(parsed);
        if (device == null) {
            log.warn("EMQX 连接事件设备不存在：{}", username);
            return;
        }
        device.setOnline(1);
        device.setLastOnlineTime(LocalDateTime.now());
        deviceMapper.updateById(device);
        shadowService.syncDesiredOnConnect(device);
    }

    /**
     * 处理断开事件（置离线）。
     *
     * @param username 用户名（{productCode}/{deviceName}）
     */
    public void onDisconnected(String username) {
        ParsedUsername parsed = parseUsername(username);
        if (parsed == null) {
            log.warn("EMQX 断开事件用户名无法解析：{}", username);
            return;
        }
        IotDevice device = findDevice(parsed);
        if (device == null) {
            log.warn("EMQX 断开事件设备不存在：{}", username);
            return;
        }
        device.setOnline(0);
        device.setLastOnlineTime(LocalDateTime.now());
        deviceMapper.updateById(device);
    }

    /**
     * 解析用户名为产品编码与设备名。
     *
     * @param username 用户名
     * @return 解析结果；不合法返回 {@code null}
     */
    static ParsedUsername parseUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        int idx = username.indexOf(USERNAME_SEPARATOR);
        if (idx <= 0 || idx == username.length() - 1) {
            return null;
        }
        return new ParsedUsername(username.substring(0, idx), username.substring(idx + 1));
    }

    /**
     * 按解析结果查设备（产品编码与设备名联合定位，避免跨产品重名误伤）。
     *
     * @param parsed 解析结果
     * @return 设备实体；不存在返回 {@code null}
     */
    private IotDevice findDevice(ParsedUsername parsed) {
        IotProduct product = productMapper.selectOne(new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getCode, parsed.productCode())
                .last("LIMIT 1"));
        if (product == null) {
            return null;
        }
        return deviceMapper.selectOne(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getProductId, product.getId())
                .eq(IotDevice::getDeviceName, parsed.deviceName())
                .last("LIMIT 1"));
    }

    /**
     * 计算 HMAC-SHA256 十六进制小写（供认证与测试复用）。
     *
     * @param secret 设备密钥
     * @param data   待签数据（用户名）
     * @return 十六进制小写
     */
    public static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    /**
     * 解析后的用户名（产品编码 + 设备名）。
     *
     * @param productCode 产品编码
     * @param deviceName  设备名
     */
    record ParsedUsername(String productCode, String deviceName) {
    }
}
