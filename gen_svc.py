import os

base = "D:/openclaw/workspace-clone/projects/smart-meeting-java"

# 1. Add fetchDocxBlocks method to FeishuService
svc_path = os.path.join(base, "meeting-server/src/main/java/com/smartmeeting/service/FeishuService.java")
with open(svc_path, "r", encoding="utf-8") as f:
    content = f.read()

# Add import for structured exporters
import_line = "import com.smartmeeting.service.structured.DocxBlockStructuredExporter;"
if import_line not in content:
    # Find last import line and add after it
    last_import_idx = content.rfind("import ")
    end_of_line = content.index(";", last_import_idx) + 1
    content = content[:end_of_line] + "\n" + import_line + content[end_of_line:]

import_line2 = "import com.smartmeeting.service.structured.BitableStructuredExporter;"
if import_line2 not in content:
    content = content[:content.index(";", content.rfind("import ")) + 1] + "\n" + import_line2 + content[content.index(";", content.rfind("import ")) + 1:]

import_line3 = "import com.smartmeeting.service.structured.SheetStructuredExporter;"
if import_line3 not in content:
    content = content[:content.index(";", content.rfind("import ")) + 1] + "\n" + import_line3 + content[content.index(";", content.rfind("import ")) + 1:]

import_line4 = "import com.smartmeeting.api.dto.structured.*;"
if import_line4 not in content:
    content = content[:content.index(";", content.rfind("import ")) + 1] + "\n" + import_line4 + content[content.index(";", content.rfind("import ")) + 1:]

# Add fetchDocxBlocks method before the closing brace
method_code = '''
    /**
     * 飞书 Docx 结构化拉取：返回 block 列表（保留富文本 runs、image key 等结构信息）。
     */
    public java.util.List<DocxBlockDto> fetchDocxBlocks(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("document_id 为空");
        }
        String tenantToken = getTenantToken();
        String pageToken = null;
        com.fasterxml.jackson.databind.ArrayNode allItems = objectMapper.createArrayNode();
        do {
            org.springframework.web.util.UriComponentsBuilder ub = org.springframework.web.util.UriComponentsBuilder
                    .fromUriString(baseUrl + "/open-apis/docx/v1/documents/" + documentId + "/blocks")
                    .queryParam("page_size", 500)
                    .queryParam("document_revision_id", -1);
            if (pageToken != null && !pageToken.isBlank()) {
                ub.queryParam("page_token", pageToken);
            }
            String url = ub.toUriString();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(tenantToken);
            org.springframework.http.HttpEntity<Void> request = new org.springframework.http.HttpEntity<>(headers);
            try {
                org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = restTemplate.exchange(
                        url, org.springframework.http.HttpMethod.GET, request, com.fasterxml.jackson.databind.JsonNode.class);
                com.fasterxml.jackson.databind.JsonNode json = response.getBody();
                if (json == null) {
                    break;
                }
                com.fasterxml.jackson.databind.JsonNode items = json.path("data").path("items");
                if (items.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode item : items) {
                        allItems.add(item);
                    }
                }
                pageToken = json.path("data").path("page_token").asText(null);
                if (pageToken == null || pageToken.isBlank()) {
                    break;
                }
            } catch (Exception e) {
                log.warn("fetchDocxBlocks page failed docId={}: {}", documentId, e.getMessage());
                break;
            }
        } while (true);
        return DocxBlockStructuredExporter.exportBlocks(allItems);
    }

    /**
     * 飞书 Bitable 结构化拉取：返回含字段类型和记录的结构化数据。
     */
    public BitableStructuredDto fetchBitableStructured(String appToken, String tableId) {
        if (appToken == null || appToken.isBlank()) {
            throw new IllegalArgumentException("base app_token 为空");
        }
        String app = appToken.trim();
        String tableName = tableId;
        if (tableId == null || tableId.isBlank()) {
            return fetchAllBitableTablesStructured(app);
        }
        // Try to get table name
        try {
            java.util.List<com.smartmeeting.matterprogress.feishu.BitableTableInfo> tables = listBitableTables(app);
            for (var t : tables) {
                if (t.tableId().equals(tableId)) {
                    tableName = t.tableName();
                    break;
                }
            }
        } catch (Exception ignored) {}
        java.util.List<com.fasterxml.jackson.databind.JsonNode> items = searchBitableRecordItems(app, tableId);
        return BitableStructuredExporter.export(items, tableName != null ? tableName : tableId);
    }

    private BitableStructuredDto fetchAllBitableTablesStructured(String appToken) {
        java.util.List<com.smartmeeting.matterprogress.feishu.BitableTableInfo> tables = listBitableTables(appToken);
        if (tables.isEmpty()) {
            return BitableStructuredDto.builder().tableName("无数据表").columns(java.util.List.of())
                    .records(java.util.List.of()).build();
        }
        // Return first table's structured data; frontend can request others
        var first = tables.get(0);
        java.util.List<com.fasterxml.jackson.databind.JsonNode> items = searchBitableRecordItems(appToken, first.tableId());
        return BitableStructuredExporter.export(items, first.tableName());
    }

    /**
     * 飞书电子表格结构化拉取：返回含合并范围的结构化数据。
     */
    public SheetStructuredDto fetchSheetStructured(String spreadsheetToken) {
        if (spreadsheetToken == null || spreadsheetToken.isBlank()) {
            throw new IllegalArgumentException("spreadsheet_token 为空");
        }
        return SheetStructuredExporter.fetch(restTemplate, baseUrl, getTenantToken(), spreadsheetToken,
                matterProgressFetchProperties.toLimits());
    }

    /**
     * 代理下载飞书图片：返回图片二进制数据。
     */
    public byte[] downloadImage(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            throw new IllegalArgumentException("image_key 为空");
        }
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/im/v1/images/" + imageKey;
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        org.springframework.http.HttpEntity<Void> request = new org.springframework.http.HttpEntity<>(headers);
        try {
            org.springframework.http.ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, request, byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("飞书图片下载失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            throw new RuntimeException("飞书图片下载失败: " + e.getMessage(), e);
        }
    }
'''

# Insert before the last closing brace of the class
last_brace = content.rfind("}")
content = content[:last_brace] + method_code + "\n}" + content[last_brace + 1:]

with open(svc_path, "w", encoding="utf-8") as f:
    f.write(content)
print("Updated FeishuService")

# 2. Add proxy-image endpoint to MeetingController
ctrl_path = os.path.join(base, "meeting-server/src/main/java/com/smartmeeting/api/controller/MeetingController.java")
with open(ctrl_path, "r", encoding="utf-8") as f:
    content = f.read()

# Add import if needed
proxy_import = "import com.smartmeeting.api.dto.structured.ImageRefDto;"
if proxy_import not in content:
    last_import_idx = content.rfind("import ")
    end_of_line = content.index(";", last_import_idx) + 1
    content = content[:end_of_line] + "\n" + proxy_import + content[end_of_line:]

# Add proxy-image endpoint before the last closing brace of the class
endpoint_code = '''
    /**
     * 代理下载飞书图片：主持页通过此接口加载飞书 image_key 对应的图片。
     */
    @GetMapping("/{id}/agenda-materials/proxy-image")
    public ResponseEntity<Resource> proxyImage(
            @PathVariable String id,
            @RequestParam String imageKey,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = resolveMeetingPageToken(authorization, queryToken);
        jwtUtil.verifyRecordingPageToken(token, id);
        try {
            byte[] imageBytes = feishuService.downloadImage(imageKey);
            String mime = imageKey.toLowerCase().endsWith(".png") ? "image/png"
                    : imageKey.toLowerCase().endsWith(".gif") ? "image/gif"
                    : "image/jpeg";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mime))
                    .cacheControl(org.springframework.http.CacheControl.maxAge(1, java.util.concurrent.TimeUnit.HOURS))
                    .body(new org.springframework.core.io.ByteArrayResource(imageBytes));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 代理下载本地资料的页面/幻灯片图片。
     */
    @GetMapping("/{id}/agenda-materials/proxy-file-image")
    public ResponseEntity<Resource> proxyFileImage(
            @PathVariable String id,
            @RequestParam String fileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = resolveMeetingPageToken(authorization, queryToken);
        jwtUtil.verifyRecordingPageToken(token, id);
        try {
            java.nio.file.Path imagePath = agendaMaterialStorageService.resolveGeneratedImagePath(fileId, page);
            if (imagePath != null && java.nio.file.Files.exists(imagePath)) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(org.springframework.http.CacheControl.maxAge(1, java.util.concurrent.TimeUnit.HOURS))
                        .body(new FileSystemResource(imagePath));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.notFound().build();
    }
'''

last_brace = content.rfind("}")
content = content[:last_brace] + endpoint_code + "\n}" + content[last_brace + 1:]

with open(ctrl_path, "w", encoding="utf-8") as f:
    f.write(content)
print("Updated MeetingController")

# 3. Add resolveGeneratedImagePath to AgendaMaterialStorageService
storage_path = os.path.join(base, "meeting-server/src/main/java/com/smartmeeting/service/AgendaMaterialStorageService.java")
with open(storage_path, "r", encoding="utf-8") as f:
    content = f.read()

new_method = '''
    /**
     * 返回本地资料生成的图片路径（PPT/PDF 页面图片）。
     */
    public java.nio.file.Path resolveGeneratedImagePath(String fileId, int page) throws java.io.IOException {
        Optional<java.nio.file.Path> pathOpt = resolvePath(fileId);
        if (pathOpt.isEmpty()) return null;
        java.nio.file.Path dataPath = pathOpt.get();
        String imagesDir = dataPath.getParent().resolve(".generated").resolve(fileId).toString();
        java.nio.file.Path imagePath = java.nio.file.Paths.get(imagesDir, "page_" + page + ".png");
        if (java.nio.file.Files.exists(imagePath)) {
            return imagePath;
        }
        // Try slide_ prefix for PPT
        imagePath = java.nio.file.Paths.get(imagesDir, "slide_" + page + ".png");
        if (java.nio.file.Files.exists(imagePath)) {
            return imagePath;
        }
        return null;
    }
'''

last_brace = content.rfind("}")
content = content[:last_brace] + new_method + "\n}" + content[last_brace + 1:]

with open(storage_path, "w", encoding="utf-8") as f:
    f.write(content)
print("Updated AgendaMaterialStorageService")
