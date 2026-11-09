package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.iot.dto.ProductSaveRequest;
import com.bluewhale.iot.dto.ProductVO;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 产品服务：创建/更新/发布/停用/删除与分页查询。
 *
 * <p>状态机：{@code draft → published ⇄ disabled}；已发布产品<b>禁止修改</b>
 * 名称/描述（物模型修改在 {@code IotThingModelService} 单独卡口），
 * 需先停用再改；删除要求产品下已无设备。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class IotProductService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 状态：开发中 */
    public static final String STATUS_DRAFT = "draft";

    /** 状态：已发布 */
    public static final String STATUS_PUBLISHED = "published";

    /** 状态：已停用 */
    public static final String STATUS_DISABLED = "disabled";

    private final IotProductMapper productMapper;

    private final IotDeviceMapper deviceMapper;

    /**
     * 创建产品（草稿态，协议固定 mqtt）。
     *
     * @param request 保存请求
     * @param userId  当前用户
     * @return 视图对象
     */
    public ProductVO create(ProductSaveRequest request, Long userId) {
        if (!StringUtils.hasText(request.getCode())) {
            throw new BizException("产品编码不能为空");
        }
        Long existed = productMapper.selectCount(
                new LambdaQueryWrapper<IotProduct>().eq(IotProduct::getCode, request.getCode()));
        if (existed != null && existed > 0) {
            throw new BizException("产品编码已存在：" + request.getCode());
        }
        IotProduct product = new IotProduct();
        product.setCode(request.getCode());
        product.setProtocol("mqtt");
        product.setStatus(STATUS_DRAFT);
        product.setCreatedBy(userId);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        productMapper.insert(product);
        return toVo(product);
    }

    /**
     * 更新产品（编码不可改；已发布产品禁止修改，需先停用）。
     *
     * @param id      产品ID
     * @param request 保存请求
     * @return 视图对象
     */
    public ProductVO update(Long id, ProductSaveRequest request) {
        IotProduct product = requireExists(id);
        if (STATUS_PUBLISHED.equals(product.getStatus())) {
            throw new BizException("产品已发布，禁止修改（请先停用）");
        }
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        productMapper.updateById(product);
        return toVo(product);
    }

    /**
     * 发布产品（draft/disabled → published）。
     *
     * @param id 产品ID
     * @return 视图对象
     */
    public ProductVO publish(Long id) {
        IotProduct product = requireExists(id);
        product.setStatus(STATUS_PUBLISHED);
        productMapper.updateById(product);
        return toVo(product);
    }

    /**
     * 停用产品（published → disabled；设备接入认证随即 fail-closed）。
     *
     * @param id 产品ID
     * @return 视图对象
     */
    public ProductVO disable(Long id) {
        IotProduct product = requireExists(id);
        product.setStatus(STATUS_DISABLED);
        productMapper.updateById(product);
        return toVo(product);
    }

    /**
     * 删除产品（逻辑删除；要求产品下已无设备）。
     *
     * @param id 产品ID
     */
    public void delete(Long id) {
        requireExists(id);
        Long deviceCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<IotDevice>().eq(IotDevice::getProductId, id));
        if (deviceCount != null && deviceCount > 0) {
            throw new BizException("产品下仍有 " + deviceCount + " 台设备，请先删除设备");
        }
        productMapper.deleteById(id);
    }

    /**
     * 产品分页（管理视角）。
     *
     * @param keyword 名称/编码模糊过滤（可空）
     * @param status  状态过滤（可空）
     * @param current 当前页
     * @param size    每页条数
     * @return 分页结果
     */
    public PageResult<ProductVO> page(String keyword, String status, long current, long size) {
        long pageNo = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<IotProduct> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(IotProduct::getName, keyword)
                    .or().like(IotProduct::getCode, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(IotProduct::getStatus, status);
        }
        wrapper.orderByDesc(IotProduct::getUpdateTime).orderByDesc(IotProduct::getId);
        Page<IotProduct> page = productMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ProductVO> records = new ArrayList<>(page.getRecords().size());
        for (IotProduct product : page.getRecords()) {
            records.add(toVo(product));
        }
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    /**
     * 产品详情。
     *
     * @param id 产品ID
     * @return 视图对象
     */
    public ProductVO get(Long id) {
        return toVo(requireExists(id));
    }

    /**
     * 按 ID 取产品（存在性校验）。
     *
     * @param id 产品ID
     * @return 产品实体
     */
    public IotProduct requireExists(Long id) {
        IotProduct product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException("产品不存在");
        }
        return product;
    }

    /**
     * 实体转视图对象（附带设备计数）。
     *
     * @param product 产品实体
     * @return 视图对象
     */
    private ProductVO toVo(IotProduct product) {
        Long deviceCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<IotDevice>().eq(IotDevice::getProductId, product.getId()));
        return ProductVO.builder()
                .id(String.valueOf(product.getId()))
                .code(product.getCode())
                .name(product.getName())
                .protocol(product.getProtocol())
                .description(product.getDescription())
                .status(product.getStatus())
                .deviceCount(deviceCount == null ? 0L : deviceCount)
                .createdBy(product.getCreatedBy() == null ? null : String.valueOf(product.getCreatedBy()))
                .createTime(format(product.getCreateTime()))
                .updateTime(format(product.getUpdateTime()))
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
