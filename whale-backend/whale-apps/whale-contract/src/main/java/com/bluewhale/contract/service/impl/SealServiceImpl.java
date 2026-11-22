package com.bluewhale.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.contract.dto.OperatorContext;
import com.bluewhale.contract.dto.SealGrantRequest;
import com.bluewhale.contract.dto.SealSaveRequest;
import com.bluewhale.contract.entity.CtSeal;
import com.bluewhale.contract.entity.CtSealGrant;
import com.bluewhale.contract.esign.FileClient;
import com.bluewhale.contract.esign.PdfStampService;
import com.bluewhale.contract.mapper.CtSealGrantMapper;
import com.bluewhale.contract.mapper.CtSealMapper;
import com.bluewhale.contract.service.SealService;
import com.bluewhale.contract.vo.SealGrantVO;
import com.bluewhale.contract.vo.SealVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 印章与用章授权服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SealServiceImpl implements SealService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 状态：启用 */
    private static final String STATUS_ENABLED = "ENABLED";

    /** 状态：停用 */
    private static final String STATUS_DISABLED = "DISABLED";

    /** 默认印章类型 */
    private static final String DEFAULT_SEAL_TYPE = "CONTRACT";

    private final CtSealMapper sealMapper;

    private final CtSealGrantMapper grantMapper;

    private final FileClient fileClient;

    private final PdfStampService pdfStampService;

    @Override
    public PageResult<SealVO> page(long current, long size, String keyword, String status) {
        LambdaQueryWrapper<CtSeal> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(keyword), CtSeal::getName, keyword);
        wrapper.eq(StringUtils.hasText(status), CtSeal::getStatus, status);
        wrapper.orderByDesc(CtSeal::getCreateTime);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        long pageCurrent = Math.max(current, 1L);
        Page<CtSeal> page = sealMapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(SealServiceImpl::toVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public SealVO create(SealSaveRequest request) {
        assertDateRange(request.getValidFrom(), request.getValidTo());
        CtSeal seal = new CtSeal();
        apply(request, seal);
        seal.setSealType(StringUtils.hasText(request.getSealType())
                ? request.getSealType().toUpperCase(Locale.ROOT) : DEFAULT_SEAL_TYPE);
        seal.setStatus(StringUtils.hasText(request.getStatus())
                ? request.getStatus().toUpperCase(Locale.ROOT) : STATUS_ENABLED);
        sealMapper.insert(seal);
        log.info("印章已创建: id={}, name={}", seal.getId(), seal.getName());
        return toVo(seal);
    }

    @Override
    public SealVO update(Long id, SealSaveRequest request) {
        CtSeal seal = requireSeal(id);
        assertDateRange(request.getValidFrom(), request.getValidTo());
        apply(request, seal);
        if (StringUtils.hasText(request.getSealType())) {
            seal.setSealType(request.getSealType().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(request.getStatus())) {
            seal.setStatus(request.getStatus().toUpperCase(Locale.ROOT));
        }
        sealMapper.updateById(seal);
        log.info("印章已更新: id={}, name={}", id, seal.getName());
        return toVo(seal);
    }

    @Override
    public void delete(Long id) {
        CtSeal seal = requireSeal(id);
        sealMapper.deleteById(id);
        log.info("印章已删除: id={}, name={}", id, seal.getName());
    }

    @Override
    public SealGrantVO grant(Long sealId, SealGrantRequest request, OperatorContext operator) {
        requireSeal(sealId);
        assertDateRange(request.getValidFrom(), request.getValidTo());
        CtSealGrant grant = new CtSealGrant();
        grant.setSealId(sealId);
        grant.setGranteeUserId(request.getGranteeUserId());
        grant.setContractCategory(request.getContractCategory());
        grant.setMaxAmount(request.getMaxAmount());
        grant.setValidFrom(request.getValidFrom());
        grant.setValidTo(request.getValidTo());
        grant.setStatus(STATUS_ENABLED);
        grant.setGrantedBy(parseUserId(operator));
        grantMapper.insert(grant);
        log.info("用章授权已创建: sealId={}, grantee={}", sealId, request.getGranteeUserId());
        return toGrantVo(grant);
    }

    @Override
    public List<SealGrantVO> grants(Long sealId) {
        requireSeal(sealId);
        return grantMapper.selectList(new LambdaQueryWrapper<CtSealGrant>()
                        .eq(CtSealGrant::getSealId, sealId)
                        .orderByDesc(CtSealGrant::getCreateTime))
                .stream().map(SealServiceImpl::toGrantVo).toList();
    }

    @Override
    public byte[] previewImage(Long id) {
        CtSeal seal = requireSeal(id);
        return pdfStampService.previewImage(seal.getImageFileId());
    }

    @Override
    public SealImage downloadImage(Long id) {
        CtSeal seal = requireSeal(id);
        if (seal.getImageFileId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.seal-upload-image-cannot-export-original", "印章未上传图片，无法导出原图: {0}", id);
        }
        byte[] bytes;
        try {
            bytes = fileClient.download(seal.getImageFileId());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.contract.service.failed-seal-original-image-read", "印章原图读取失败: {0}", ex.getMessage());
        }
        if (bytes == null || bytes.length == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.contract.service.seal-original-image-empty", "印章原图为空: {0}", id);
        }
        String contentType = detectImageContentType(bytes);
        String extension = "image/png".equals(contentType) ? ".png"
                : "image/jpeg".equals(contentType) ? ".jpg" : ".bin";
        return new SealImage(bytes, contentType, "seal-" + id + extension);
    }

    /**
     * 按文件头识别图片 MIME 类型。
     *
     * @param bytes 图片字节
     * @return MIME 类型；无法识别时返回 {@code application/octet-stream}
     */
    private String detectImageContentType(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        return "application/octet-stream";
    }

    @Override
    public Long uploadImage(String filename, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.seal-image-empty", "印章图片不能为空");
        }
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.seal-image-image-type", "印章图片必须为图片类型");
        }
        String extension = ".png";
        if (StringUtils.hasText(filename) && filename.lastIndexOf('.') > 0) {
            extension = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        String targetName = "seal-" + System.currentTimeMillis() + extension;
        return fileClient.upload(targetName, contentType, content);
    }

    /**
     * 校验印章存在。
     *
     * @param id 印章ID
     * @return 印章实体
     */
    @Override
    public boolean isGranted(Long sealId, Long userId) {
        if (sealId == null || userId == null) {
            return false;
        }
        CtSeal seal = sealMapper.selectById(sealId);
        if (seal == null || !STATUS_ENABLED.equalsIgnoreCase(seal.getStatus())) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if (seal.getValidFrom() != null && today.isBefore(seal.getValidFrom())) {
            return false;
        }
        if (seal.getValidTo() != null && today.isAfter(seal.getValidTo())) {
            return false;
        }
        return grantMapper.selectList(new LambdaQueryWrapper<CtSealGrant>()
                        .eq(CtSealGrant::getSealId, sealId)
                        .eq(CtSealGrant::getGranteeUserId, userId)
                        .eq(CtSealGrant::getStatus, STATUS_ENABLED))
                .stream().anyMatch(grant -> inValidity(grant, today));
    }

    @Override
    public List<SealVO> grantedSeals(Long userId) {
        if (userId == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        List<Long> sealIds = grantMapper.selectList(new LambdaQueryWrapper<CtSealGrant>()
                        .eq(CtSealGrant::getGranteeUserId, userId)
                        .eq(CtSealGrant::getStatus, STATUS_ENABLED))
                .stream().filter(grant -> inValidity(grant, today))
                .map(CtSealGrant::getSealId).distinct().toList();
        if (sealIds.isEmpty()) {
            return List.of();
        }
        return sealMapper.selectList(new LambdaQueryWrapper<CtSeal>()
                        .in(CtSeal::getId, sealIds)
                        .eq(CtSeal::getStatus, STATUS_ENABLED)
                        .orderByAsc(CtSeal::getName))
                .stream().filter(seal -> (seal.getValidFrom() == null || !today.isBefore(seal.getValidFrom()))
                        && (seal.getValidTo() == null || !today.isAfter(seal.getValidTo())))
                .map(SealServiceImpl::toVo).toList();
    }

    /**
     * 判断授权是否在有效期内。
     *
     * @param grant 授权记录
     * @param today 当前日期
     * @return 是否有效
     */
    private static boolean inValidity(CtSealGrant grant, LocalDate today) {
        if (grant.getValidFrom() != null && today.isBefore(grant.getValidFrom())) {
            return false;
        }
        return grant.getValidTo() == null || !today.isAfter(grant.getValidTo());
    }

    /**
     * 校验印章存在。
     *
     * @param id 印章ID
     * @return 印章实体
     */
    private CtSeal requireSeal(Long id) {
        CtSeal seal = sealMapper.selectById(id);
        if (seal == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.contract.service.seal-found", "印章不存在: {0}", id);
        }
        return seal;
    }

    /**
     * 应用请求字段。
     *
     * @param request 保存请求
     * @param seal    印章实体
     */
    private void apply(SealSaveRequest request, CtSeal seal) {
        seal.setName(request.getName());
        seal.setImageFileId(request.getImageFileId());
        seal.setKeeperUserId(request.getKeeperUserId());
        seal.setValidFrom(request.getValidFrom());
        seal.setValidTo(request.getValidTo());
    }

    /**
     * 校验有效期区间。
     *
     * @param from 起
     * @param to   止
     */
    private void assertDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.contract.service.validity-start-later-than-validity-end", "有效期起不能晚于有效期止");
        }
    }

    /**
     * 解析当前操作人用户ID。
     *
     * @param operator 操作人
     * @return 用户ID
     */
    private Long parseUserId(OperatorContext operator) {
        if (operator == null || !StringUtils.hasText(operator.userId())) {
            return null;
        }
        try {
            return Long.valueOf(operator.userId());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 印章实体转视图。
     *
     * @param seal 印章实体
     * @return 视图对象
     */
    private static SealVO toVo(CtSeal seal) {
        SealVO vo = new SealVO();
        vo.setId(seal.getId() == null ? null : String.valueOf(seal.getId()));
        vo.setName(seal.getName());
        vo.setSealType(seal.getSealType());
        vo.setImageFileId(seal.getImageFileId() == null ? null : String.valueOf(seal.getImageFileId()));
        vo.setKeeperUserId(seal.getKeeperUserId() == null ? null : String.valueOf(seal.getKeeperUserId()));
        vo.setValidFrom(seal.getValidFrom());
        vo.setValidTo(seal.getValidTo());
        vo.setStatus(seal.getStatus());
        vo.setCreateTime(seal.getCreateTime());
        vo.setUpdateTime(seal.getUpdateTime());
        return vo;
    }

    /**
     * 授权实体转视图。
     *
     * @param grant 授权实体
     * @return 视图对象
     */
    private static SealGrantVO toGrantVo(CtSealGrant grant) {
        SealGrantVO vo = new SealGrantVO();
        vo.setId(grant.getId() == null ? null : String.valueOf(grant.getId()));
        vo.setSealId(grant.getSealId() == null ? null : String.valueOf(grant.getSealId()));
        vo.setGranteeUserId(grant.getGranteeUserId() == null ? null : String.valueOf(grant.getGranteeUserId()));
        vo.setContractCategory(grant.getContractCategory());
        vo.setMaxAmount(grant.getMaxAmount());
        vo.setValidFrom(grant.getValidFrom());
        vo.setValidTo(grant.getValidTo());
        vo.setStatus(grant.getStatus());
        vo.setGrantedBy(grant.getGrantedBy() == null ? null : String.valueOf(grant.getGrantedBy()));
        vo.setCreateTime(grant.getCreateTime());
        return vo;
    }
}
