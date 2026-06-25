package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.MeetingAudioAsset;
import com.smartmeeting.enums.AudioAssetRole;
import com.smartmeeting.enums.AudioQualityStatus;
import com.smartmeeting.enums.AudioSourceType;
import com.smartmeeting.model.AudioFormatDescriptor;
import com.smartmeeting.model.AudioQualityReport;
import com.smartmeeting.repository.MeetingAudioAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 会议音频资产持久化。
 */
@Service
@RequiredArgsConstructor
public class MeetingAudioAssetService {

    private final MeetingAudioAssetMapper meetingAudioAssetMapper;

    public MeetingAudioAsset saveAsset(String meetingId,
                                       AudioAssetRole role,
                                       AudioSourceType sourceType,
                                       String path,
                                       AudioFormatDescriptor format,
                                       long fileSize,
                                       Integer durationMs,
                                       AudioQualityReport quality) {
        MeetingAudioAsset asset = new MeetingAudioAsset();
        asset.setId(UUID.randomUUID().toString());
        asset.setMeetingId(meetingId);
        asset.setAssetRole(role.name());
        asset.setSourceType(sourceType != null ? sourceType.name() : AudioSourceType.UNKNOWN.name());
        asset.setPath(path);
        if (format != null) {
            asset.setEncoding(format.getEncoding());
            asset.setSampleRate(format.getSampleRate());
            asset.setChannels(format.getChannels());
            asset.setBitDepth(format.getBitDepth());
        }
        asset.setFileSize(Math.max(0, fileSize));
        asset.setDurationMs(durationMs);
        if (quality != null) {
            asset.setRms(quality.getRms());
            asset.setAbsmax(quality.getAbsmax());
            asset.setNonzeroRatio(quality.getNonzeroRatio());
            asset.setQualityStatus(quality.getStatus().name());
        } else {
            asset.setQualityStatus(AudioQualityStatus.OK.name());
        }
        asset.setCreatedAt(LocalDateTime.now());
        meetingAudioAssetMapper.insert(asset);
        return asset;
    }

    public MeetingAudioAsset findLatest(String meetingId, AudioAssetRole role) {
        return meetingAudioAssetMapper.selectOne(new LambdaQueryWrapper<MeetingAudioAsset>()
                .eq(MeetingAudioAsset::getMeetingId, meetingId)
                .eq(MeetingAudioAsset::getAssetRole, role.name())
                .orderByDesc(MeetingAudioAsset::getCreatedAt)
                .last("LIMIT 1"));
    }
}
