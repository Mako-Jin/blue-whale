package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.AppealCreateRequest;
import com.bluewhale.forum.dto.AppealHandleRequest;
import com.bluewhale.forum.dto.AppealMaterialContent;
import com.bluewhale.forum.dto.AppealMaterialVO;
import com.bluewhale.forum.dto.AppealVO;
import com.bluewhale.forum.entity.ForumAppeal;
import com.bluewhale.forum.entity.ForumAppealMaterial;
import com.bluewhale.forum.entity.ForumPost;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.entity.ForumUserBan;
import com.bluewhale.forum.mapper.ForumAppealMapper;
import com.bluewhale.forum.mapper.ForumAppealMaterialMapper;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.AppealService;
import com.bluewhale.forum.service.BanService;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.support.ForumFileMetadataClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 申诉服务实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppealServiceImpl implements AppealService {

    /** 已处置状态（帖子 / 楼层共用字面量 REMOVED） */
    private static final String REMOVED = "REMOVED";

    /** outbox 业务类型 */
    private static final String BIZ_APPEAL = "APPEAL";

    private final ForumAppealMapper appealMapper;

    private final ForumAppealMaterialMapper materialMapper;

    private final ForumFileMetadataClient fileMetadataClient;

    private final ForumThreadMapper threadMapper;

    private final ForumPostMapper postMapper;

    private final BanService banService;

    private final ForumNotifyService notifyService;

    private final ForumProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppealVO submit(AppealCreateRequest body, ForumActor actor) {
        String type = normalizeType(body.getTargetType());
        Long targetId = body.getTargetId();
        assertAppealable(type, targetId, actor);
        // 仅待审唯一：唯一键无法表达「部分唯一」，判重放服务层；驳回后可再次申诉
        long pending = appealMapper.selectCount(new LambdaQueryWrapper<ForumAppeal>()
                .eq(ForumAppeal::getUserId, actor.userId())
                .eq(ForumAppeal::getTargetType, type)
                .eq(ForumAppeal::getTargetId, targetId)
                .eq(ForumAppeal::getStatus, STATUS_PENDING));
        if (pending > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该对象已有待处理的申诉，请勿重复提交");
        }
        // 先校验材料再落工单：文件服务不可用时不留半张工单，也少一次事务内网络调用
        List<ForumFileMetadataClient.FileMeta> files = resolveMaterials(body.getMaterialFileIds(), actor.userId());
        ForumAppeal appeal = new ForumAppeal();
        appeal.setTenantId(TenantContext.requireId());
        appeal.setUserId(actor.userId());
        appeal.setTargetType(type);
        appeal.setTargetId(targetId);
        appeal.setReason(body.getReason().trim());
        appeal.setStatus(STATUS_PENDING);
        appealMapper.insert(appeal);
        List<ForumAppealMaterial> materials = saveMaterials(files, appeal);
        return toVO(appeal, materials);
    }

    @Override
    public PageResult<AppealVO> myAppeals(long current, long size, ForumActor actor) {
        long pageNo = current < 1 ? 1 : current;
        long pageSize = normalizeSize(size);
        Page<ForumAppeal> page = appealMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<ForumAppeal>()
                        .eq(ForumAppeal::getUserId, actor.userId())
                        .orderByDesc(ForumAppeal::getCreateTime)
                        .orderByDesc(ForumAppeal::getId));
        Map<Long, List<ForumAppealMaterial>> materials = loadMaterials(
                page.getRecords().stream().map(ForumAppeal::getId).toList());
        List<AppealVO> records = page.getRecords().stream()
                .map(appeal -> toVO(appeal, materials.getOrDefault(appeal.getId(), List.of())))
                .toList();
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    @Override
    public PageResult<AppealVO> page(String status, long current, long size) {
        long pageNo = current < 1 ? 1 : current;
        long pageSize = normalizeSize(size);
        String normalized = status == null || status.isBlank() ? null : normalizeStatus(status);
        Page<ForumAppeal> page = appealMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<ForumAppeal>()
                        .eq(normalized != null, ForumAppeal::getStatus, normalized)
                        .orderByDesc(ForumAppeal::getCreateTime)
                        .orderByDesc(ForumAppeal::getId));
        Map<Long, List<ForumAppealMaterial>> materials = loadMaterials(
                page.getRecords().stream().map(ForumAppeal::getId).toList());
        List<AppealVO> records = page.getRecords().stream()
                .map(appeal -> toVO(appeal, materials.getOrDefault(appeal.getId(), List.of())))
                .toList();
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppealVO handle(Long appealId, AppealHandleRequest body, ForumActor actor) {
        ForumAppeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "申诉工单不存在");
        }
        if (!STATUS_PENDING.equals(appeal.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该申诉已处理，不能重复复核");
        }
        String next = Boolean.TRUE.equals(body.getApproved()) ? STATUS_APPROVED : STATUS_REJECTED;
        appeal.setStatus(next);
        appeal.setHandlerId(actor.userId());
        appeal.setHandleRemark(body.getRemark() == null ? null : body.getRemark().trim());
        appeal.setHandleTime(LocalDateTime.now());
        appealMapper.updateById(appeal);

        // 仅禁言申诉通过时解禁：内容恢复仍走处置模块，复核不做跨聚合写
        if (STATUS_APPROVED.equals(next) && TARGET_BAN.equals(appeal.getTargetType())) {
            banService.release(appeal.getUserId(), actor.userId());
        }
        boolean approved = STATUS_APPROVED.equals(next);
        notifyService.enqueue(appeal.getUserId(), BIZ_APPEAL, appeal.getId(),
                "forum:appeal:" + appeal.getId() + ":" + next,
                approved ? "申诉已通过" : "申诉被驳回",
                approved ? "你的申诉已复核通过，处置已调整。" : "你的申诉经复核未通过，如有异议可补充材料后再次提交。");
        log.info("申诉复核：appealId={}, type={}, targetId={}, status={}",
                appealId, appeal.getTargetType(), appeal.getTargetId(), next);
        return toVO(appeal, loadMaterials(List.of(appealId)).getOrDefault(appealId, List.of()));
    }

    /**
     * 校验受理范围与归属。
     *
     * @param type 归一化类型
     * @param targetId 对象ID
     * @param actor 当前操作者
     */
    private void assertAppealable(String type, Long targetId, ForumActor actor) {
        switch (type) {
            case TARGET_THREAD -> {
                ForumThread thread = threadMapper.selectById(targetId);
                if (thread == null || !actor.userId().equals(thread.getAuthorId())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "只能申诉本人的帖子");
                }
                if (!REMOVED.equals(thread.getStatus())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "该帖未被处置，无需申诉");
                }
            }
            case TARGET_POST -> {
                ForumPost post = postMapper.selectById(targetId);
                if (post == null || !actor.userId().equals(post.getAuthorId())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "只能申诉本人的楼层");
                }
                if (!REMOVED.equals(post.getStatus())) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "该楼层未被处置，无需申诉");
                }
            }
            case TARGET_BAN -> {
                if (!actor.userId().equals(targetId)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "只能申诉本人的禁言处置");
                }
                ForumUserBan ban = banService.activeBan(actor.userId());
                if (ban == null) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "当前没有生效的禁言处置，无需申诉");
                }
            }
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "不支持的申诉对象类型");
        }
    }

    /**
     * 归一化对象类型。
     *
     * @param targetType 原始类型
     * @return THREAD / POST / BAN
     */
    private String normalizeType(String targetType) {
        String type = targetType == null ? "" : targetType.trim().toUpperCase();
        if (!TARGET_THREAD.equals(type) && !TARGET_POST.equals(type) && !TARGET_BAN.equals(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "申诉对象类型仅支持 THREAD / POST / BAN");
        }
        return type;
    }

    /**
     * 归一化工单状态。
     *
     * @param status 原始状态
     * @return PENDING / APPROVED / REJECTED
     */
    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase();
        if (!STATUS_PENDING.equals(normalized) && !STATUS_APPROVED.equals(normalized)
                && !STATUS_REJECTED.equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工单状态仅支持 PENDING / APPROVED / REJECTED");
        }
        return normalized;
    }

    @Override
    public AppealMaterialContent previewMaterial(Long fileId, ForumActor actor) {
        ForumAppealMaterial material = materialMapper.selectOne(
                new LambdaQueryWrapper<ForumAppealMaterial>()
                        .eq(ForumAppealMaterial::getFileId, fileId)
                        .orderByAsc(ForumAppealMaterial::getId)
                        .last("LIMIT 1"));
        if (material == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "申诉材料不存在");
        }
        ForumAppeal appeal = appealMapper.selectById(material.getAppealId());
        if (appeal == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "申诉材料不存在");
        }
        // 本人看自己的材料，复核人看工单材料；其余一律拒绝——内网内容接口不做归属校验
        boolean owner = actor != null && actor.userId() != null && actor.userId().equals(appeal.getUserId());
        if (!owner && (actor == null || !actor.hasPermission(ForumPermissions.MANAGE_DISPOSE))) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该申诉材料");
        }
        String contentType = material.getContentType() == null ? "" : material.getContentType();
        if (!contentType.startsWith("image/")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该材料不支持在线预览");
        }
        ForumFileMetadataClient.FileContent content = fileMetadataClient.loadContent(fileId);
        return new AppealMaterialContent(content.bytes(), contentType);
    }

    /**
     * 校验并解析材料文件元数据。
     *
     * <p>按提交顺序去重：同一文件重复出现只登记一次，避免唯一键在插第 2 行时才报错。</p>
     *
     * @param fileIds 文件ID列表（可空）
     * @param userId 当前用户ID
     * @return 文件元数据列表（与提交顺序一致）
     */
    private List<ForumFileMetadataClient.FileMeta> resolveMaterials(List<Long> fileIds, Long userId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long fileId : fileIds) {
            if (fileId != null) {
                unique.add(fileId);
            }
        }
        if (unique.size() > properties.getAppealMaterialLimit()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "申诉材料不能超过 " + properties.getAppealMaterialLimit() + " 个");
        }
        List<ForumFileMetadataClient.FileMeta> files = new ArrayList<>(unique.size());
        for (Long fileId : unique) {
            files.add(fileMetadataClient.requireOwned(fileId, userId));
        }
        return files;
    }

    /**
     * 落库材料登记行。
     *
     * @param files 已校验的文件元数据
     * @param appeal 工单
     * @return 已登记的材料
     */
    private List<ForumAppealMaterial> saveMaterials(List<ForumFileMetadataClient.FileMeta> files,
                                                    ForumAppeal appeal) {
        if (files.isEmpty()) {
            return List.of();
        }
        List<ForumAppealMaterial> saved = new ArrayList<>(files.size());
        int sortNo = 0;
        for (ForumFileMetadataClient.FileMeta file : files) {
            ForumAppealMaterial material = new ForumAppealMaterial();
            material.setId(IdWorker.getId());
            material.setTenantId(appeal.getTenantId());
            material.setAppealId(appeal.getId());
            material.setFileId(file.fileId());
            material.setFileName(file.originalName());
            material.setFileSize(file.fileSize());
            material.setContentType(file.contentType());
            material.setSortNo(sortNo++);
            material.setCreateTime(LocalDateTime.now());
            materialMapper.insert(material);
            saved.add(material);
        }
        return saved;
    }

    /**
     * 批量加载工单材料，按工单分组。
     *
     * @param appealIds 工单ID集合
     * @return 工单ID → 材料列表
     */
    private Map<Long, List<ForumAppealMaterial>> loadMaterials(Collection<Long> appealIds) {
        if (appealIds == null || appealIds.isEmpty()) {
            return Map.of();
        }
        List<ForumAppealMaterial> rows = materialMapper.selectList(
                new LambdaQueryWrapper<ForumAppealMaterial>()
                        .in(ForumAppealMaterial::getAppealId, appealIds)
                        .orderByAsc(ForumAppealMaterial::getSortNo)
                        .orderByAsc(ForumAppealMaterial::getId));
        Map<Long, List<ForumAppealMaterial>> grouped = new LinkedHashMap<>();
        for (ForumAppealMaterial row : rows) {
            grouped.computeIfAbsent(row.getAppealId(), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    /**
     * 实体转视图。
     *
     * @param appeal 工单
     * @param materials 材料
     * @return 视图
     */
    private AppealVO toVO(ForumAppeal appeal, List<ForumAppealMaterial> materials) {
        AppealVO vo = new AppealVO();
        vo.setId(appeal.getId());
        vo.setUserId(appeal.getUserId());
        vo.setTargetType(appeal.getTargetType());
        vo.setTargetId(appeal.getTargetId());
        vo.setReason(appeal.getReason());
        vo.setStatus(appeal.getStatus());
        vo.setHandleRemark(appeal.getHandleRemark());
        vo.setHandleTime(appeal.getHandleTime());
        vo.setCreateTime(appeal.getCreateTime());
        vo.setMaterials(materials.stream().map(this::toMaterialVO).toList());
        return vo;
    }

    /**
     * 材料实体转视图。
     *
     * @param material 材料实体
     * @return 材料视图
     */
    private AppealMaterialVO toMaterialVO(ForumAppealMaterial material) {
        AppealMaterialVO vo = new AppealMaterialVO();
        vo.setFileId(material.getFileId());
        vo.setFileName(material.getFileName());
        vo.setFileSize(material.getFileSize());
        vo.setContentType(material.getContentType());
        vo.setDownloadUrl("/file/v1/" + material.getFileId() + "/download");
        return vo;
    }

    /**
     * 归一化分页条数。
     *
     * @param size 原始条数
     * @return 合法条数
     */
    private long normalizeSize(long size) {
        if (size < 1) {
            return properties.getPageSizeDefault();
        }
        return Math.min(size, properties.getPageSizeMax());
    }
}
