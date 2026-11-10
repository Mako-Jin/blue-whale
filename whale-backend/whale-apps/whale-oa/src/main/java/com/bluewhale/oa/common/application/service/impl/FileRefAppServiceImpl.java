package com.bluewhale.oa.common.application.service.impl;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.command.FileRefRegisterCommand;
import com.bluewhale.oa.common.application.service.AttachmentOwnershipGuard;
import com.bluewhale.oa.common.application.service.FileRefAppService;
import com.bluewhale.oa.common.application.view.FileRefView;
import com.bluewhale.oa.common.domain.model.FileMetadata;
import com.bluewhale.oa.common.domain.model.FileRef;
import com.bluewhale.oa.common.domain.repository.FileRefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 文件引用应用服务实现。
 *
 * <p>登记前的附件归属校验统一委托 {@link AttachmentOwnershipGuard}（OA 内唯一实现），
 * 本类不再自带一套校验：文件必须存在、未删除、状态正常，且上传人必须为当前登录用户。
 * 文件名 / 大小 / MIME 类型取守卫返回的元数据，不采信调用方传入值。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FileRefAppServiceImpl implements FileRefAppService {

    private final FileRefRepository fileRefRepository;

    private final AttachmentOwnershipGuard attachmentOwnershipGuard;

    @Override
    public List<FileRefView> listByBiz(String bizType, Long bizId) {
        if (!StringUtils.hasText(bizType) || bizId == null) {
            throw OaErrorCode.exception(OaErrorCode.FILE_REF_INVALID);
        }
        return fileRefRepository.listByBiz(bizType, bizId).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileRefView register(FileRefRegisterCommand command) {
        if (!StringUtils.hasText(command.getBizType()) || command.getBizId() == null || command.getFileId() == null) {
            throw OaErrorCode.exception(OaErrorCode.FILE_REF_INVALID);
        }
        Long uploadedBy = command.getUploadedBy() != null
                ? command.getUploadedBy()
                : RequestContextHolder.getUserId();
        FileMetadata meta = attachmentOwnershipGuard.assertReferable(command.getFileId(), uploadedBy);
        if (meta == null) {
            throw OaErrorCode.exception(OaErrorCode.FILE_REF_INVALID);
        }
        FileRef fileRef = new FileRef();
        fileRef.setBizType(command.getBizType());
        fileRef.setBizId(command.getBizId());
        fileRef.setFileId(command.getFileId());
        fileRef.setFileName(meta.originalName() != null ? meta.originalName() : command.getFileName());
        fileRef.setFileSize(meta.fileSize() != null && meta.fileSize() > 0 ? meta.fileSize() : command.getFileSize());
        fileRef.setContentType(StringUtils.hasText(meta.contentType())
                ? meta.contentType() : command.getContentType());
        fileRef.setUploadedBy(uploadedBy);
        fileRef.setPurpose(command.getPurpose());
        FileRef saved = fileRefRepository.insertIfAbsent(fileRef)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.FILE_REF_EXISTS));
        return toView(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        fileRefRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.FILE_REF_NOT_FOUND));
        fileRefRepository.deleteById(id);
    }

    /**
     * 领域模型转视图。
     *
     * @param fileRef 文件引用
     * @return 文件引用视图
     */
    private FileRefView toView(FileRef fileRef) {
        FileRefView view = new FileRefView();
        view.setId(fileRef.getId());
        view.setBizType(fileRef.getBizType());
        view.setBizId(fileRef.getBizId());
        view.setFileName(fileRef.getFileName());
        view.setFileSize(fileRef.getFileSize());
        view.setContentType(fileRef.getContentType());
        view.setUploadedBy(fileRef.getUploadedBy());
        view.setPurpose(fileRef.getPurpose());
        view.setCreateTime(fileRef.getCreateTime());
        return view;
    }
}
