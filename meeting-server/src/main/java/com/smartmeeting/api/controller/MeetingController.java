package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.AgendaDocContentResponse;
import com.smartmeeting.api.dto.AgendaMaterialPreviewDto;
import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.MinuteResponse;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.api.dto.MeetingTodoResponse;
import com.smartmeeting.api.dto.TodoBoardResponse;
import com.smartmeeting.service.FeishuMeetingStartCoordinator;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.MeetingMinuteQueryService;
import com.smartmeeting.service.MeetingRecordingSessionEndService;
import com.smartmeeting.service.MeetingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.agenda.AgendaMaterialDiskStorage;
import com.smartmeeting.config.agenda.HostAgendaJsonCodec;
import com.smartmeeting.service.AgendaMaterialStorageService;
import com.smartmeeting.service.PresetAgendaDocService;
import com.smartmeeting.service.host.MeetingHostSessionService;
import com.smartmeeting.service.TodoService;
import com.smartmeeting.util.JwtUtil;
import com.smartmeeting.util.MeetingWebPageUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会议 REST 控制器。
 * <p>
 * 基础路径 {@code /api/v1/meetings}，提供会议 CRUD、飞书 Web 一键创建并启动、
 * 录音页结束会议、议程文档只读拉取、纪要查询、待办与看板查询等接口。
 *
 * @see MeetingService
 * @see com.smartmeeting.api.dto.MeetingResponse
 */
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final TodoService todoService;
    private final JwtUtil jwtUtil;
    private final FeishuMeetingStartCoordinator feishuMeetingStartCoordinator;
    private final MeetingRecordingSessionEndService meetingRecordingSessionEndService;
    private final PresetAgendaDocService presetAgendaDocService;
    private final MeetingMapper meetingMapper;
    private final MeetingHostSessionService meetingHostSessionService;
    private final MeetingMinuteQueryService meetingMinuteQueryService;
    private final AgendaMaterialStorageService agendaMaterialStorageService;
    private final ObjectMapper objectMapper;
    private final MeetingWebPageUrls meetingWebPageUrls;

    /**
     * 创建会议（未启动）。
     *
     * @param request 创建请求体，含预设类型、议程、参会人等
     * @return 新建会议的完整信息
     */
    @PostMapping
    public ApiResponse<MeetingResponse> createMeeting(@Valid @RequestBody MeetingCreateRequest request) {
        return ApiResponse.ok(meetingService.createMeeting(request));
    }

    /**
     * 飞书 Web 入口：请求头携带 {@code X-Feishu-Web-Entry-Token}（卡片链接中的 JWT），
     * 创建会议、启动并向对应飞书会话推送录音卡片（与飞书内文字指令效果一致）。
     *
     * @param webEntryToken 飞书 Web 入口 JWT，解析出 openId 与 chatId
     * @param request       与 {@link #createMeeting(MeetingCreateRequest)} 相同的创建参数
     * @return 已创建并启动的会议信息
     */
    @PostMapping("/feishu-web/create-and-start")
    public ApiResponse<MeetingResponse> createAndStartFromFeishuWeb(
            @RequestHeader("X-Feishu-Web-Entry-Token") String webEntryToken,
            @Valid @RequestBody MeetingCreateRequest request) {
        JwtUtil.FeishuWebStartMeetingEntry entry = jwtUtil.parseAndVerifyFeishuWebStartMeetingEntry(webEntryToken.trim());
        MeetingResponse out = feishuMeetingStartCoordinator.createMeetingStartAndNotifyFeishu(
                entry.feishuUserId(), entry.chatId(), request);
        return ApiResponse.ok(out);
    }

    /**
     * 启动会议（状态流转为进行中）。
     *
     * @param id 会议 ID
     * @return 更新后的会议信息
     */
    @PostMapping("/{id}/start")
    public ApiResponse<MeetingResponse> startMeeting(@PathVariable String id) {
        return ApiResponse.ok(meetingService.startMeeting(id));
    }

    /**
     * 结束会议（管理端/飞书指令等通用入口）。
     *
     * @param id 会议 ID
     * @return 更新后的会议信息
     */
    @PostMapping("/{id}/end")
    public ApiResponse<MeetingResponse> endMeeting(@PathVariable String id) {
        return ApiResponse.ok(meetingService.endMeeting(id));
    }

    /**
     * 录音页结束会议：须携带与页面 URL 相同的录音 JWT（{@code Authorization: Bearer …}）。
     * 已在录音时走 {@link com.smartmeeting.service.RecordingService#stopRecording}，
     * 否则走 {@link MeetingService#endMeeting}，避免重复触发纪要链。
     *
     * @param id            会议 ID
     * @param authorization {@code Bearer} 录音页 JWT
     * @return 更新后的会议信息
     * @throws com.smartmeeting.exception.BusinessException 凭证缺失、无效或与会议不匹配时
     */
    @PostMapping("/{id}/recording-session/end")
    public ApiResponse<MeetingResponse> endMeetingFromRecordingSession(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {
        String token = bearerToken(authorization);
        jwtUtil.verifyRecordingPageToken(token, id);
        return ApiResponse.ok(meetingRecordingSessionEndService.endFromRecordingPage(id));
    }

    /**
     * 按会序拉取飞书 Docx 纯文本，供主持页只读展示（不经过 LLM）。
     *
     * @param id            会议 ID
     * @param agendaIndex   会序下标（从 0 起）
     * @param authorization {@code Bearer} 录音页 JWT
     * @return 合并后的议程资料纯文本及分项列表
     * @throws com.smartmeeting.exception.BusinessException agendaIndex 为负或会议不存在时
     */
    @GetMapping("/{id}/agenda-doc-content")
    public ApiResponse<AgendaDocContentResponse> agendaDocContent(
            @PathVariable String id,
            @RequestParam int agendaIndex,
            @RequestHeader("Authorization") String authorization) {
        String token = bearerToken(authorization);
        jwtUtil.verifyRecordingPageToken(token, id);
        if (agendaIndex < 0) {
            throw new BusinessException(400, "agendaIndex 不能为负");
        }
        Meeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + id);
        }
        java.util.List<com.smartmeeting.api.dto.FeishuDocRefDto> runtimeDocs = null;
        if (meetingHostSessionService.isActive(id)) {
            runtimeDocs = meetingHostSessionService.getAgendaDocRefs(id, agendaIndex);
        }
        String title = resolveAgendaTitle(meeting, agendaIndex);
        return ApiResponse.ok(presetAgendaDocService.buildAgendaDocContent(
                meeting, agendaIndex, title, runtimeDocs));
    }

    /**
     * 下载会序本地上传资料（主持/录音页 JWT；fileId 须绑定本场会议 host_agenda）。
     */
    @GetMapping("/{id}/agenda-materials/{fileId}")
    public ResponseEntity<Resource> downloadAgendaMaterial(
            @PathVariable String id,
            @PathVariable String fileId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String queryToken) {
        String token = resolveMeetingPageToken(authorization, queryToken);
        jwtUtil.verifyRecordingPageToken(token, id);
        Meeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + id);
        }
        assertMeetingHasLocalFile(meeting, fileId);
        try {
            Optional<AgendaMaterialDiskStorage.StoredMaterial> meta = agendaMaterialStorageService.loadMeta(fileId);
            Optional<Path> path = agendaMaterialStorageService.resolvePath(fileId);
            if (meta.isEmpty() || path.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            AgendaMaterialDiskStorage.StoredMaterial m = meta.get();
            String disposition = agendaMaterialStorageService.isInlineDisposition(m.mimeType()) ? "inline" : "attachment";
            String filename = m.originalFilename() != null ? m.originalFilename() : "file";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(m.mimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            disposition + "; filename=\"" + filename.replace("\"", "") + "\"")
                    .body(new FileSystemResource(path.get()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 会序本地上传 doc/docx 纯文本预览（主持页 JWT）。
     */
    @GetMapping("/{id}/agenda-materials/{fileId}/preview")
    public ApiResponse<AgendaMaterialPreviewDto> previewAgendaMaterial(
            @PathVariable String id,
            @PathVariable String fileId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String queryToken) {
        String token = resolveMeetingPageToken(authorization, queryToken);
        jwtUtil.verifyRecordingPageToken(token, id);
        Meeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + id);
        }
        assertMeetingHasLocalFile(meeting, fileId);
        try {
            Optional<AgendaMaterialPreviewDto> preview = agendaMaterialStorageService.previewMaterial(fileId);
            if (preview.isEmpty()) {
                throw new BusinessException(404, "无法预览该资料");
            }
            return ApiResponse.ok(preview.get());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "预览失败: " + e.getMessage());
        }
    }

    private void assertMeetingHasLocalFile(Meeting meeting, String fileId) {
        String fid = fileId != null ? fileId.trim() : "";
        if (fid.isEmpty()) {
            throw new BusinessException(400, "fileId 无效");
        }
        List<String> ids = HostAgendaJsonCodec.collectLocalFileIds(objectMapper, meeting.getHostAgenda());
        if (!ids.contains(fid)) {
            throw new BusinessException(404, "资料不存在或未绑定本场会议");
        }
    }

    /**
     * 从会议主持议程中解析指定会序的标题，用于文档拉取展示。
     *
     * @param meeting     会议实体
     * @param agendaIndex 会序下标
     * @return 会序标题，未找到时返回空字符串
     */
    private String resolveAgendaTitle(Meeting meeting, int agendaIndex) {
        List<com.smartmeeting.api.dto.host.HostAgendaItemDto> items =
                meetingService.getMeeting(meeting.getId()).getHostAgendaItems();
        if (items != null && agendaIndex >= 0 && agendaIndex < items.size()
                && items.get(agendaIndex).getTitle() != null) {
            return items.get(agendaIndex).getTitle();
        }
        return "";
    }

    /**
     * 从 {@code Authorization} 请求头解析 Bearer Token。
     *
     * @param authorization 请求头值
     * @return JWT 字符串（不含 {@code Bearer} 前缀）
     * @throws com.smartmeeting.exception.BusinessException 缺少或格式不正确时
     */
    private static String bearerToken(String authorization) {
        return resolveMeetingPageToken(authorization, null);
    }

    private static String resolveMeetingPageToken(String authorization, String queryToken) {
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.trim();
        }
        throw new BusinessException(401, "缺少 Authorization: Bearer 或 token 查询参数");
    }

    @GetMapping("/{id}/viewer-page-url")
    public ApiResponse<Map<String, String>> viewerPageUrl(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {
        String token = bearerToken(authorization);
        jwtUtil.verifyHostOperatorToken(token, id);
        String viewerToken = jwtUtil.generateViewerMeetingToken(id);
        String viewerUrl = meetingWebPageUrls.viewerPageUrl(id, viewerToken);
        return ApiResponse.ok(Map.of("viewerUrl", viewerUrl));
    }

    /**
     * 按 ID 查询单个会议详情。
     *
     * @param id 会议 ID
     * @return 会议完整信息（含参会人、主持议程等）
     */
    @GetMapping("/{id}")
    public ApiResponse<MeetingResponse> getMeeting(@PathVariable String id) {
        return ApiResponse.ok(meetingService.getMeeting(id));
    }

    /**
     * 查询会议纪要：库内 Markdown + 飞书 doc_url（录音页 JWT 或公开读均可）。
     *
     * @param id            会议 ID
     * @param authorization 可选 {@code Bearer} 录音页 JWT；携带时会校验与会议匹配
     * @return 纪要内容与来源标识
     */
    @GetMapping("/{id}/minute")
    public ApiResponse<MinuteResponse> getMeetingMinute(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            jwtUtil.verifyRecordingPageToken(token, id);
        }
        return ApiResponse.ok(meetingMinuteQueryService.getMinuteForMeeting(id));
    }

    /**
     * 分页列出会议，可按状态与创建人筛选。
     *
     * @param status    可选，会议状态过滤
     * @param creatorId 可选，创建人 ID 过滤
     * @param page      页码，从 0 起
     * @param size      每页条数，默认 20
     * @return 会议列表
     */
    @GetMapping
    public ApiResponse<List<MeetingResponse>> listMeetings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String creatorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(meetingService.listMeetings(status, creatorId, page, size));
    }

    /**
     * 列出指定会议的全部待办项。
     *
     * @param id 会议 ID
     * @return 待办列表
     */
    @GetMapping("/{id}/todos")
    public ApiResponse<List<MeetingTodoResponse>> listTodos(@PathVariable String id) {
        return ApiResponse.ok(todoService.listTodosByMeeting(id));
    }

    /**
     * 获取会议待办看板（含各状态计数与完整列表）。
     *
     * @param id 会议 ID
     * @return 看板聚合数据
     */
    @GetMapping("/{id}/todo-board")
    public ApiResponse<TodoBoardResponse> getTodoBoard(@PathVariable String id) {
        return ApiResponse.ok(todoService.getTodoBoard(id));
    }
}
