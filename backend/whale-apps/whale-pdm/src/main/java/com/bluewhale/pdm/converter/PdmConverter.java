package com.bluewhale.pdm.converter;

import com.bluewhale.pdm.document.dto.PdmDocDocumentSaveRequest;
import com.bluewhale.pdm.document.dto.PdmDocFolderSaveRequest;
import com.bluewhale.pdm.document.entity.PdmDocAcl;
import com.bluewhale.pdm.document.entity.PdmDocDocument;
import com.bluewhale.pdm.document.entity.PdmDocFolder;
import com.bluewhale.pdm.document.entity.PdmDocVersion;
import com.bluewhale.pdm.document.vo.PdmDocAclVO;
import com.bluewhale.pdm.document.vo.PdmDocDocumentVO;
import com.bluewhale.pdm.document.vo.PdmDocFolderVO;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;
import com.bluewhale.pdm.dto.PdmProductSaveRequest;
import com.bluewhale.pdm.entity.PdmProduct;
import com.bluewhale.pdm.vo.PdmProductVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * PDM 模块实体与 VO/DTO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段对齐。</p>
 * @author BlueWhale
 */
@Mapper
public interface PdmConverter {

    /** 单例（无状态映射器） */
    PdmConverter INSTANCE = Mappers.getMapper(PdmConverter.class);

    /**
     * 产品实体转视图对象
     *
     * @param entity 产品实体
     * @return 产品视图对象
     */
    PdmProductVO toProductVo(PdmProduct entity);

    /**
     * 产品保存请求转实体
     *
     * @param request 保存请求
     * @return 产品实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    PdmProduct toEntity(PdmProductSaveRequest request);

    /**
     * 产品保存请求覆盖既有实体
     *
     * @param request 保存请求
     * @param entity  待更新的产品实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(PdmProductSaveRequest request, @MappingTarget PdmProduct entity);

    /**
     * 目录实体转视图
     *
     * @param entity 目录实体
     * @return 目录视图
     */
    @Mapping(target = "children", ignore = true)
    PdmDocFolderVO toFolderVo(PdmDocFolder entity);

    /**
     * 目录保存请求转实体
     *
     * @param request 保存请求
     * @return 目录实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "path", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    PdmDocFolder toFolderEntity(PdmDocFolderSaveRequest request);

    /**
     * 文档实体转视图
     *
     * @param entity 文档实体
     * @return 文档视图
     */
    PdmDocDocumentVO toDocumentVo(PdmDocDocument entity);

    /**
     * 文档保存请求转实体（版本、检出与状态字段由服务维护）
     *
     * @param request 保存请求
     * @return 文档实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "ownerName", ignore = true)
    @Mapping(target = "latestVersionId", ignore = true)
    @Mapping(target = "latestVersionNo", ignore = true)
    @Mapping(target = "currentVersionId", ignore = true)
    @Mapping(target = "checkedOutBy", ignore = true)
    @Mapping(target = "checkedOutTime", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    PdmDocDocument toDocumentEntity(PdmDocDocumentSaveRequest request);

    /**
     * 版本实体转视图
     *
     * @param entity 版本实体
     * @return 版本视图
     */
    PdmDocVersionVO toVersionVo(PdmDocVersion entity);

    /**
     * ACL 规则实体转视图
     *
     * @param entity ACL 规则实体
     * @return ACL 规则视图
     */
    PdmDocAclVO toAclVo(PdmDocAcl entity);
}
