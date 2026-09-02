package com.smartmeeting.admin.service;

import com.smartmeeting.admin.api.dto.AdminApiReferenceEntryDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台「数据更新」类 API 目录与示例（与 Controller 保持同步维护）。
 */
@Service
public class AdminDataApiCatalogService {

    private static final String ADMIN_BASE = "http://127.0.0.1:8766";
    private static final String MEETING_BASE = "http://127.0.0.1:8765";
    private static final String ADMIN_TOKEN = "dev-admin-token";
    private static final String INTERNAL_TOKEN = "dev-internal-reload";

    public List<AdminApiReferenceEntryDto> buildCatalog() {
        List<AdminApiReferenceEntryDto> list = new ArrayList<>();
        addAgendaConfig(list);
        addMeetings(list);
        addSystemConfig(list);
        addUsers(list);
        addMeetingServerInternal(list);
        return list;
    }

    private void addAgendaConfig(List<AdminApiReferenceEntryDto> list) {
        String cat = "会务预设 · agenda-config";
        list.add(entry("agenda-bundle-put", cat, "PUT",
                "/api/v1/admin/agenda-config/presets/{code}/agenda-bundle",
                "一体化保存会序与资料（推荐）",
                "按行序写入 host_agenda v2（items[].docs[]）；config_name 跨 preset 1–5 全局唯一。",
                "meeting-admin-server :8766",
                adminAuth(),
                jsonContent(),
                """
                [
                  {
                    "title": "会序1: 开场",
                    "minutes": 5,
                    "bindings": [
                      {
                        "id": 5,
                        "configName": "preset1-weekly-report-out",
                        "resourceSlot": 0,
                        "configRole": "OUTPUT",
                        "bitableDisplayMode": "RAW",
                        "feishuDocUrl": "https://example.feishu.cn/base/xxx",
                        "enabled": 1
                      }
                    ]
                  },
                  {
                    "title": "会序2: 汇报",
                    "minutes": 10,
                    "bindings": []
                  }
                ]
                """.trim(),
                """
                { "code": 0, "message": "ok", "data": null }
                """.trim(),
                curlAdmin("PUT", "/api/v1/admin/agenda-config/presets/1/agenda-bundle", """
                [{"title":"会序1: 开场","minutes":5,"bindings":[]}]
                """),
                List.of("path 参数 code：会务类型 1–5", "管理 UI 失焦自动保存即调用本接口")));

        list.add(entry("preset-put", cat, "PUT",
                "/api/v1/admin/agenda-config/presets/{code}",
                "保存会务预设主表 + host_agenda JSON",
                "更新 int_meeting_type_preset；若含 hostAgendaJson 会校验 JSON 形态。保存后通知 meeting-server 刷新 preset Redis 缓存。",
                "meeting-admin-server :8766",
                adminAuth(),
                jsonContent(),
                """
                {
                  "presetTypeCode": 1,
                  "displayName": "周会",
                  "hostAgendaJson": "{\\"items\\":[{\\"title\\":\\"会序1\\",\\"minutes\\":5}]}"
                }
                """.trim(),
                "{ \"code\": 0, \"message\": \"ok\", \"data\": null }",
                curlAdmin("PUT", "/api/v1/admin/agenda-config/presets/1", """
                {"presetTypeCode":1,"hostAgendaJson":"{\\"items\\":[{\\"title\\":\\"会序1\\",\\"minutes\\":5}]}"}
                """),
                List.of("JSON 高级模式保存 host_agenda 时使用")));

        list.add(entry("agenda-items-put", cat, "PUT",
                "/api/v1/admin/agenda-config/presets/{code}/agenda-items",
                "仅保存会序表（不含资料）",
                "只更新 host_agenda JSON（不含 docs 时资料清空）。",
                "meeting-admin-server :8766",
                adminAuth(),
                jsonContent(),
                """
                [
                  { "title": "会序1: 检点", "minutes": 5 },
                  { "title": "会序2: 汇报", "minutes": 10 }
                ]
                """.trim(),
                "{ \"code\": 0, \"message\": \"ok\", \"data\": null }",
                curlAdmin("PUT", "/api/v1/admin/agenda-config/presets/1/agenda-items",
                        "[{\"title\":\"会序1\",\"minutes\":5}]"),
                List.of("资料绑定请优先使用 agenda-bundle")));

        list.add(entry("refresh-meetings", cat, "POST",
                "/api/v1/admin/agenda-config/presets/{code}/refresh-meetings-host-agenda?dryRun={true|false}",
                "批量刷新未开始会议的 host_agenda（enrich）",
                "经桥接调用 meeting-server internal API；仅处理 ISSUE_COLLECTING / INVITED；dryRun=true 仅预览 meetingIds。",
                "meeting-admin-server → meeting-server :8765",
                adminAuth(),
                null,
                null,
                """
                {
                  "code": 0,
                  "data": {
                    "dryRun": true,
                    "count": 3,
                    "enriched": true,
                    "note": "preset merge + feishu enrich",
                    "meetingIds": ["m1", "m2"]
                  }
                }
                """.trim(),
                curlAdmin("POST", "/api/v1/admin/agenda-config/presets/1/refresh-meetings-host-agenda?dryRun=true", null),
                List.of("dryRun=false 时写回会议表 host_agenda")));

        list.add(entry("refresh-preset-cache", cat, "POST",
                "/api/v1/admin/agenda-config/presets/{code}/refresh-cache",
                "刷新 preset Redis 缓存",
                "桥接 POST meeting-server /api/v1/internal/presets/{code}/refresh-cache。",
                "meeting-admin-server → meeting-server :8765",
                adminAuth(),
                null,
                null,
                "{ \"code\": 0, \"data\": null }",
                curlAdmin("POST", "/api/v1/admin/agenda-config/presets/1/refresh-cache", null),
                List.of("修改 preset / doc 后建议执行")));
    }

    private void addMeetings(List<AdminApiReferenceEntryDto> list) {
        String cat = "会议运维 · meetings";
        list.add(entry("meeting-force-end", cat, "POST",
                "/api/v1/admin/meetings/{meetingId}/force-end",
                "强制结束会议",
                "将会议状态置为结束（运维兜底）。",
                "meeting-admin-server :8766",
                adminAuth(),
                null,
                null,
                "{ \"code\": 0, \"data\": { \"success\": true } }",
                curlAdmin("POST", "/api/v1/admin/meetings/abc-meeting-id/force-end", null),
                null));
    }

    private void addSystemConfig(List<AdminApiReferenceEntryDto> list) {
        String cat = "系统参数 · system-config";
        list.add(entry("config-upsert", cat, "PUT",
                "/api/v1/admin/system-config/entries/{key}",
                "写入/覆盖系统参数",
                "写入 meeting_system_config；成功后尝试触发 meeting-server runtime reload。",
                "meeting-admin-server :8766",
                adminAuth(),
                jsonContent(),
                """
                { "valueJson": "\\"3600\\"" }
                """.trim(),
                "{ \"code\": 0, \"data\": { \"reloaded\": true } }",
                curlAdmin("PUT", "/api/v1/admin/system-config/entries/meeting.issue.collecting.timeout.seconds",
                        "{\"valueJson\":\"3600\"}"),
                List.of("valueJson 为 JSON 字符串（含引号转义）", "BOOLEAN 用 \"true\" / \"false\"")));

        list.add(entry("config-delete", cat, "DELETE",
                "/api/v1/admin/system-config/entries/{key}",
                "删除 DB 覆盖（恢复 YAML 默认）",
                "删除 meeting_system_config 行并触发 reload。",
                "meeting-admin-server :8766",
                adminAuth(),
                null,
                null,
                "{ \"code\": 0, \"data\": { \"reloaded\": true } }",
                curlAdmin("DELETE", "/api/v1/admin/system-config/entries/meeting.issue.collecting.timeout.seconds", null),
                null));

        list.add(entry("config-reload", cat, "POST",
                "/api/v1/admin/system-config/reload-runtime",
                "热加载 meeting-server 运行时配置",
                "桥接 internal /runtime-config/reload。",
                "meeting-admin-server → meeting-server :8765",
                adminAuth(),
                null,
                null,
                "{ \"code\": 0, \"data\": { \"reloaded\": true } }",
                curlAdmin("POST", "/api/v1/admin/system-config/reload-runtime", null),
                null));
    }

    private void addUsers(List<AdminApiReferenceEntryDto> list) {
        String cat = "用户管理 · users";
        list.add(entry("user-mapping-post", cat, "POST",
                "/api/v1/admin/users/mappings",
                "新建 OA↔飞书用户映射",
                "写入 int_user_mapping_feishu；userId 为主键不可重复。",
                "meeting-admin-server :8766",
                adminAuth(),
                jsonContent(),
                """
                {
                  "userId": 1001,
                  "userName": "张三",
                  "feishuUserId": "116afd4c"
                }
                """.trim(),
                "{ \"code\": 0, \"data\": { \"userId\": 1001 } }",
                curlAdmin("POST", "/api/v1/admin/users/mappings", """
                {"userId":1001,"userName":"张三","feishuUserId":"116afd4c"}
                """),
                null));

        list.add(entry("voiceprint-put", cat, "PUT",
                "/api/v1/admin/users/voiceprints/{id}",
                "更新声纹记录",
                "运维补录或修正 featureId / 过期时间等；正常注册流程由 meeting-server 写入。",
                "meeting-admin-server :8766",
                adminAuth(),
                jsonContent(),
                """
                {
                  "userId": 1001,
                  "userName": "张三",
                  "featureId": "xf-feature-id",
                  "expiresAt": "2036-05-27T10:00:00"
                }
                """.trim(),
                "{ \"code\": 0, \"data\": null }",
                curlAdmin("PUT", "/api/v1/admin/users/voiceprints/uuid-here", """
                {"userId":1001,"userName":"张三","featureId":"xf-feature-id"}
                """),
                List.of("列表支持 expiryFilter=VALID|EXPIRING|EXPIRED|ALL")));

        list.add(entry("user-profile-put", cat, "PUT",
                "/api/v1/admin/users/{userId}",
                "保存用户完整档案（映射 + 声纹）",
                "一次请求更新 int_user_mapping_feishu 与主声纹；clearVoiceprint=true 时删除全部声纹。",
                "meeting-admin-server :8766",
                adminAuth(),
                jsonContent(),
                """
                {
                  "mapping": {
                    "userId": 1,
                    "userName": "付靖怡",
                    "feishuUserId": "116afd4c"
                  },
                  "voiceprint": {
                    "featureId": "xf-xxx",
                    "groupId": null,
                    "registeredAt": null,
                    "expiresAt": null
                  },
                  "clearVoiceprint": false
                }
                """.trim(),
                "{ \"code\": 0, \"data\": null }",
                curlAdmin("PUT", "/api/v1/admin/users/1", """
                {"mapping":{"userId":1,"userName":"付靖怡"},"voiceprint":{"featureId":"xf-1"}}
                """),
                List.of("推荐管理 UI 使用本接口；列表 GET /api/v1/admin/users")));
    }

    private void addMeetingServerInternal(List<AdminApiReferenceEntryDto> list) {
        String cat = "会中服务 Internal（经桥接或直接调用）";
        List<String> internalAuth = List.of("X-Internal-Token: " + INTERNAL_TOKEN);

        list.add(entry("internal-refresh-host-agenda", cat, "POST",
                "/api/v1/internal/meetings/refresh-host-agenda",
                "合并 preset+doc 写回会议 host_agenda（含飞书 enrich）",
                "管理端 refresh-meetings-host-agenda 最终调用此接口。需配置 meeting.runtime.meeting-server-base-url 与 internal-reload-token。",
                "meeting-server :8765",
                internalAuth,
                jsonContent(),
                """
                {
                  "presetTypeCode": 1,
                  "dryRun": false,
                  "meetingIds": []
                }
                """.trim(),
                """
                {
                  "code": 0,
                  "data": {
                    "dryRun": false,
                    "count": 2,
                    "enriched": true,
                    "meetingIds": ["id1", "id2"]
                  }
                }
                """.trim(),
                curlInternal("POST", "/api/v1/internal/meetings/refresh-host-agenda", """
                {"presetTypeCode":1,"dryRun":false}
                """),
                List.of("meetingIds 为空则刷新该 preset 下所有可刷新会议")));

        list.add(entry("internal-preset-cache", cat, "POST",
                "/api/v1/internal/presets/{code}/refresh-cache",
                "刷新单个 preset 缓存",
                "重载 Redis 中 preset/doc 快照。",
                "meeting-server :8765",
                internalAuth,
                null,
                null,
                "{ \"code\": 0, \"data\": null }",
                curlInternal("POST", "/api/v1/internal/presets/1/refresh-cache", null),
                null));

        list.add(entry("internal-runtime-reload", cat, "POST",
                "/api/v1/internal/runtime-config/reload",
                "热加载 meeting-server 配置",
                "系统参数变更后由 admin 桥接触发。",
                "meeting-server :8765",
                internalAuth,
                null,
                null,
                "{ \"code\": 0, \"data\": null }",
                curlInternal("POST", "/api/v1/internal/runtime-config/reload", null),
                null));
    }

    private static AdminApiReferenceEntryDto entry(
            String id, String category, String method, String path, String title, String description,
            String targetProcess, List<String> authHeaders, String requestContentType,
            String requestExample, String responseExample, String curlExample, List<String> notes) {
        return AdminApiReferenceEntryDto.builder()
                .id(id)
                .category(category)
                .method(method)
                .path(path)
                .title(title)
                .description(description)
                .targetProcess(targetProcess)
                .authHeaders(authHeaders)
                .requestContentType(requestContentType)
                .requestExample(requestExample)
                .responseExample(responseExample)
                .curlExample(curlExample)
                .notes(notes != null ? notes : List.of())
                .build();
    }

    private static List<String> adminAuth() {
        return List.of("X-Admin-Token: " + ADMIN_TOKEN, "Content-Type: application/json（有 body 时）");
    }

    private static String jsonContent() {
        return "application/json";
    }

    private static String curlAdmin(String method, String path, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X ").append(method).append(" '").append(ADMIN_BASE).append(path).append("'");
        sb.append(" \\\n  -H 'X-Admin-Token: ").append(ADMIN_TOKEN).append("'");
        if (body != null && !body.isBlank()) {
            sb.append(" \\\n  -H 'Content-Type: application/json'");
            sb.append(" \\\n  -d '").append(body.replace("'", "'\\''")).append("'");
        }
        return sb.toString();
    }

    private static String curlInternal(String method, String path, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X ").append(method).append(" '").append(MEETING_BASE).append(path).append("'");
        sb.append(" \\\n  -H 'X-Internal-Token: ").append(INTERNAL_TOKEN).append("'");
        if (body != null && !body.isBlank()) {
            sb.append(" \\\n  -H 'Content-Type: application/json'");
            sb.append(" \\\n  -d '").append(body.replace("'", "'\\''")).append("'");
        }
        return sb.toString();
    }
}
