package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.AppealStatus;
import com.bluewhale.music.domain.model.SongAppeal;
import com.bluewhale.music.domain.repository.SongAppealRepository;
import com.bluewhale.music.infrastructure.persistence.entity.SongAppealEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.SongAppealMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 版权下架申诉仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SongAppealRepositoryImpl implements SongAppealRepository {

    private final SongAppealMapper songAppealMapper;

    @Override
    public Optional<SongAppeal> findById(Long id) {
        return Optional.ofNullable(songAppealMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsPending(Long songId) {
        return songAppealMapper.selectCount(new LambdaQueryWrapper<SongAppealEntity>()
                .eq(SongAppealEntity::getSongId, songId)
                .eq(SongAppealEntity::getStatus, AppealStatus.PENDING.name())) > 0L;
    }

    @Override
    public void insert(SongAppeal appeal) {
        SongAppealEntity entity = new SongAppealEntity();
        BeanUtils.copyProperties(appeal, entity);
        entity.setStatus(appeal.getStatus().name());
        songAppealMapper.insert(entity);
        appeal.setId(entity.getId());
        appeal.setCreateTime(entity.getCreateTime());
        appeal.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(SongAppeal appeal) {
        SongAppealEntity entity = new SongAppealEntity();
        BeanUtils.copyProperties(appeal, entity);
        entity.setStatus(appeal.getStatus().name());
        songAppealMapper.updateById(entity);
    }

    @Override
    public List<SongAppeal> pageAudit(Long songId, AppealStatus status, long offset, long limit) {
        long pageNo = limit <= 0 ? 1L : offset / limit + 1L;
        Page<SongAppealEntity> result = songAppealMapper.selectPage(new Page<>(pageNo, limit),
                auditWrapper(songId, status));
        return result.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countAudit(Long songId, AppealStatus status) {
        return songAppealMapper.selectCount(auditWrapper(songId, status));
    }

    /**
     * 构造复核分页查询条件（倒序，歌曲 / 状态可组合过滤）。
     *
     * @param songId 歌曲 ID，可为空
     * @param status 申诉状态，可为空
     * @return 查询条件
     */
    private LambdaQueryWrapper<SongAppealEntity> auditWrapper(Long songId, AppealStatus status) {
        LambdaQueryWrapper<SongAppealEntity> wrapper = new LambdaQueryWrapper<>();
        if (songId != null) {
            wrapper.eq(SongAppealEntity::getSongId, songId);
        }
        if (status != null) {
            wrapper.eq(SongAppealEntity::getStatus, status.name());
        }
        return wrapper.orderByDesc(SongAppealEntity::getCreateTime)
                .orderByDesc(SongAppealEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 申诉实体
     * @return 申诉领域模型
     */
    private SongAppeal toDomain(SongAppealEntity entity) {
        SongAppeal appeal = new SongAppeal();
        BeanUtils.copyProperties(entity, appeal);
        appeal.setStatus(AppealStatus.valueOf(entity.getStatus()));
        return appeal;
    }
}
