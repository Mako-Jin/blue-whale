package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.iot.dto.DeviceCreateRequest;
import com.bluewhale.iot.dto.DeviceCreateVO;
import com.bluewhale.iot.dto.DeviceSecretResetVO;
import com.bluewhale.iot.dto.DeviceUpdateRequest;
import com.bluewhale.iot.dto.DeviceVO;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 设备服务：注册（三元组签发）/更新/禁用/删除、密钥重置与分页查询。
 *
 * <p>secret 口径：服务端生成 32 字节随机数（SecureRandom）的十六进制串，
 * <b>仅创建/重置响应返回一次</b>，库内可逆存储用于认证 HMAC 校验（内网库权限保护）。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class IotDeviceService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** secret 字节数（十六进制后 64 字符） */
    private static final int SECRET_BYTES = 32;

    /** 状态：启用 */
    public static final String STATUS_ACTIVE = "active";

    /** 状态：禁用 */
    public static final String STATUS_DISABLED = "disabled";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IotDeviceMapper deviceMapper;

    private final IotProductService productService;

    /**
     * 注册设备（签发三元组 secret，仅响应返回一次）。
     *
     * @param request 创建请求
     * @return 含一次性 secret 的视图对象
     */
    public DeviceCreateVO create(DeviceCreateRequest request) {
        IotProduct product = productService.requireExists(request.getProductId());
        Long existed = deviceMapper.selectCount(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getProductId, request.getProductId())
                .eq(IotDevice::getDeviceName, request.getDeviceName()));
        if (existed != null && existed > 0) {
            throw new BizException("设备名称已存在：" + request.getDeviceName() + "（产品内唯一）");
        }
        IotDevice device = new IotDevice();
        device.setProductId(request.getProductId());
        device.setDeviceName(request.getDeviceName());
        device.setNickname(request.getNickname());
        device.setSecret(generateSecret());
        device.setOnline(0);
        device.setStatus(STATUS_ACTIVE);
        deviceMapper.insert(device);
        DeviceCreateVO vo = new DeviceCreateVO();
        org.springframework.beans.BeanUtils.copyProperties(toVo(device, product), vo);
        vo.setSecret(device.getSecret());
        return vo;
    }

    /**
     * 更新设备（昵称/状态；设备名与产品归属不可改）。
     *
     * @param id      设备ID
     * @param request 更新请求
     * @return 视图对象
     */
    public DeviceVO update(Long id, DeviceUpdateRequest request) {
        IotDevice device = requireExists(id);
        if (StringUtils.hasText(request.getNickname())) {
            device.setNickname(request.getNickname());
        }
        if (StringUtils.hasText(request.getStatus())) {
            device.setStatus(request.getStatus());
        }
        deviceMapper.updateById(device);
        return toVo(device, productService.requireExists(device.getProductId()));
    }

    /**
     * 重置设备密钥（旧 secret 立即失效；新 secret 仅本次返回）。
     *
     * @param id 设备ID
     * @return 含一次性新 secret 的视图对象
     */
    public DeviceSecretResetVO resetSecret(Long id) {
        IotDevice device = requireExists(id);
        device.setSecret(generateSecret());
        deviceMapper.updateById(device);
        DeviceSecretResetVO vo = new DeviceSecretResetVO();
        org.springframework.beans.BeanUtils.copyProperties(
                toVo(device, productService.requireExists(device.getProductId())), vo);
        vo.setSecret(device.getSecret());
        return vo;
    }

    /**
     * 删除设备（逻辑删除）。
     *
     * @param id 设备ID
     */
    public void delete(Long id) {
        requireExists(id);
        deviceMapper.deleteById(id);
    }

    /**
     * 设备分页（产品/关键字过滤）。
     *
     * @param productId 产品过滤（可空）
     * @param keyword   名称/昵称模糊过滤（可空）
     * @param current   当前页
     * @param size      每页条数
     * @return 分页结果
     */
    public PageResult<DeviceVO> page(Long productId, String keyword, long current, long size) {
        long pageNo = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<IotDevice> wrapper = new LambdaQueryWrapper<>();
        if (productId != null) {
            wrapper.eq(IotDevice::getProductId, productId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(IotDevice::getDeviceName, keyword)
                    .or().like(IotDevice::getNickname, keyword));
        }
        wrapper.orderByDesc(IotDevice::getUpdateTime).orderByDesc(IotDevice::getId);
        Page<IotDevice> page = deviceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<DeviceVO> records = new ArrayList<>(page.getRecords().size());
        for (IotDevice device : page.getRecords()) {
            records.add(toVo(device, productService.requireExists(device.getProductId())));
        }
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    /**
     * 设备详情。
     *
     * @param id 设备ID
     * @return 视图对象
     */
    public DeviceVO get(Long id) {
        IotDevice device = requireExists(id);
        return toVo(device, productService.requireExists(device.getProductId()));
    }

    /**
     * 按 ID 取设备（存在性校验）。
     *
     * @param id 设备ID
     * @return 设备实体
     */
    public IotDevice requireExists(Long id) {
        IotDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BizException("设备不存在");
        }
        return device;
    }

    /**
     * 生成随机 secret（32 字节 → 64 位十六进制）。
     *
     * @return secret 字符串
     */
    private static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 实体转视图对象。
     *
     * @param device  设备实体
     * @param product 所属产品
     * @return 视图对象
     */
    private DeviceVO toVo(IotDevice device, IotProduct product) {
        return DeviceVO.builder()
                .id(String.valueOf(device.getId()))
                .productId(String.valueOf(device.getProductId()))
                .productCode(product == null ? null : product.getCode())
                .productName(product == null ? null : product.getName())
                .deviceName(device.getDeviceName())
                .nickname(device.getNickname())
                .online(device.getOnline())
                .lastOnlineTime(format(device.getLastOnlineTime()))
                .lastReportTime(format(device.getLastReportTime()))
                .status(device.getStatus())
                .createTime(format(device.getCreateTime()))
                .updateTime(format(device.getUpdateTime()))
                .build();
    }

    /**
     * 格式化时间。
     *
     * @param time 时间
     * @return ISO-8601 字符串；空值返回 {@code null}
     */
    private static String format(java.time.LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }
}
