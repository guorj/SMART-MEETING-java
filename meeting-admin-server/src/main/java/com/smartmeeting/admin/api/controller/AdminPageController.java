package com.smartmeeting.admin.api.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.concurrent.TimeUnit;

@Controller
public class AdminPageController {

    /** 无尾斜杠的 /admin 统一 302 到 /admin/，避免相对静态资源路径解析错误。 */
    @GetMapping("/admin")
    public String redirectAdminSlash() {
        return "redirect:/admin/";
    }

    /** 管理后台 SPA 入口页。 */
    @GetMapping("/admin/")
    public ResponseEntity<Resource> adminIndex() {
        return serve("static/admin/index.html");
    }

    private static ResponseEntity<Resource> serve(String path) {
        Resource res = new ClassPathResource(path);
        if (!res.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(res);
    }
}
