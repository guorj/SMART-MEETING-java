package com.smartmeeting.service.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.api.dto.internal.RefreshHostAgendaRequest;
import com.smartmeeting.api.dto.internal.RefreshHostAgendaResult;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.PostMeetingOrchestrator;
import com.smartmeeting.service.PresetAgendaDocService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InternalMeetingAdminService {

    private static final List<String> REFRESHABLE_STATUSES = List.of("ISSUE_COLLECTING", "INVITED");

    private final MeetingMapper meetingMapper;
    private final PresetAgendaDocService presetAgendaDocService;
    private final PostMeetingOrchestrator postMeetingOrchestrator;

    public RefreshHostAgendaResult refreshHostAgenda(RefreshHostAgendaRequest req) {
        int preset = req.getPresetTypeCode() != null ? req.getPresetTypeCode() : 0;
        if (preset <= 0) {
            throw new BusinessException("invalid presetTypeCode");
        }
        presetAgendaDocService.refreshPresetBundle(preset);

        List<Meeting> targets = resolveTargets(preset, req.getMeetingIds());
        List<String> ids = new ArrayList<>();
        if (!req.isDryRun()) {
            for (Meeting m : targets) {
                List<HostAgendaItemDto> items = presetAgendaDocService.parseHostAgendaItemsPublic(m.getHostAgenda());
                if (items.isEmpty()) {
                    items = null;
                }
                String json = presetAgendaDocService.syncHostAgendaForCreate(preset, items);
                if (json != null) {
                    m.setHostAgenda(json);
                    meetingMapper.updateById(m);
                    ids.add(m.getId());
                }
            }
        } else {
            targets.forEach(m -> ids.add(m.getId()));
        }
        return RefreshHostAgendaResult.builder()
                .dryRun(req.isDryRun())
                .count(ids.size())
                .meetingIds(ids)
                .enriched(true)
                .note("经 PresetAgendaDocService.syncHostAgendaForCreate 合并 preset+doc 飞书绑定")
                .build();
    }

    public void refreshPresetCache(int presetTypeCode) {
        if (presetTypeCode > 0) {
            presetAgendaDocService.refreshPresetBundle(presetTypeCode);
        } else {
            presetAgendaDocService.refreshAllPresetBundles();
        }
    }

    /**
     * 写入妙记 minute_token（Admin 手动补录或 webhook 兜底）。
     * <p>
     * 用于 webhook 未到或丢失时，运维从飞书妙记页面拿到 token 后手动写入，
     * 后续「重新生成纪要」会自动走 File B 转写。
     *
     * @param meetingId   会议 ID
     * @param minuteToken 妙记 token（24 字符）；空串视为清除
     * @param recordingUrl 妙记页面 URL（可选，便于人工核对）
     */
    public void setVcMinuteToken(String meetingId, String minuteToken, String recordingUrl) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
        String token = minuteToken == null ? "" : minuteToken.trim();
        meeting.setVcMinuteToken(token.isBlank() ? null : token);
        if (recordingUrl != null && !recordingUrl.isBlank()) {
            meeting.setVcRecordingUrl(recordingUrl.trim());
        }
        meetingMapper.updateById(meeting);
        if (!token.isBlank()) {
            postMeetingOrchestrator.resumePostMeetingAfterVcReady(meetingId);
        }
    }

    private List<Meeting> resolveTargets(int preset, List<String> meetingIds) {
        if (meetingIds != null && !meetingIds.isEmpty()) {
            LambdaQueryWrapper<Meeting> q = new LambdaQueryWrapper<>();
            q.in(Meeting::getId, meetingIds);
            return meetingMapper.selectList(q).stream()
                    .filter(m -> m.getPresetTypeCode() != null && m.getPresetTypeCode() == preset)
                    .filter(m -> REFRESHABLE_STATUSES.contains(m.getStatus()))
                    .toList();
        }
        LambdaQueryWrapper<Meeting> q = new LambdaQueryWrapper<>();
        q.eq(Meeting::getPresetTypeCode, preset).in(Meeting::getStatus, REFRESHABLE_STATUSES);
        return meetingMapper.selectList(q);
    }
}
