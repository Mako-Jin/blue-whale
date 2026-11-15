package com.bluewhale.file.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.file.domain.share.aggregate.FileShareAggregate;
import com.bluewhale.file.domain.share.model.FileShareItem;
import com.bluewhale.file.domain.share.model.FileSharePage;
import com.bluewhale.file.domain.share.model.FileShareQuery;
import com.bluewhale.file.domain.share.repository.FileShareRepository;
import com.bluewhale.file.infrastructure.persistence.assembler.FileSharePersistenceAssembler;
import com.bluewhale.file.mapper.FileShareMapper;
import com.bluewhale.file.po.FileSharePo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link FileShareRepository} 的 MyBatis-Plus 实现。
 *
 * <p>查询与写入均依赖租户拦截器追加 {@code tenant_id} 条件，跨租户令牌在本层不可见。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class FileShareRepositoryImpl implements FileShareRepository {

    private final FileShareMapper fileShareMapper;

    @Override
    public FileShareAggregate save(FileShareAggregate share) {
        FileSharePo po = FileSharePersistenceAssembler.toPo(share);
        fileShareMapper.insert(po);
        return FileSharePersistenceAssembler.toDomain(po);
    }

    @Override
    public Optional<FileShareAggregate> findById(Long id) {
        return Optional.ofNullable(fileShareMapper.selectById(id))
                .map(FileSharePersistenceAssembler::toDomain);
    }

    @Override
    public Optional<FileShareAggregate> findByToken(String token) {
        LambdaQueryWrapper<FileSharePo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileSharePo::getToken, token);
        return Optional.ofNullable(fileShareMapper.selectOne(wrapper))
                .map(FileSharePersistenceAssembler::toDomain);
    }

    @Override
    public boolean revoke(Long id) {
        return fileShareMapper.revokeById(id) > 0;
    }

    @Override
    public boolean consumeDownload(Long id) {
        return fileShareMapper.consumeDownload(id) > 0;
    }

    @Override
    public FileSharePage page(FileShareQuery query) {
        long pageCurrent = Math.max(query.current(), 1L);
        long pageSize = Math.max(query.size(), 1L);
        LambdaQueryWrapper<FileSharePo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.creatorId() != null, FileSharePo::getCreatorId, query.creatorId());
        // 追加主键二级排序：create_time 相同的行需要稳定顺序，否则分页边界可能重复或遗漏
        wrapper.orderByDesc(FileSharePo::getCreateTime);
        wrapper.orderByDesc(FileSharePo::getId);
        Page<FileSharePo> page = fileShareMapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);
        List<FileShareItem> records = page.getRecords().stream()
                .map(FileSharePersistenceAssembler::toItem)
                .toList();
        return new FileSharePage(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
