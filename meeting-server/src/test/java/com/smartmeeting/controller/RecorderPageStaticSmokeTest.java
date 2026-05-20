package com.smartmeeting.controller;

import com.smartmeeting.BaseTest;
import org.junit.jupiter.api.Test;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 录音 / 主持入口：直接返回 HTML + no-store；/static/index.html 仍可由静态映射访问（带短缓存）。
 */
class RecorderPageStaticSmokeTest extends BaseTest {

    /** favicon 请求应返回 204 No Content。 */
    @Test
    void faviconReturns204() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNoContent());
    }

    /** /rec 路径应返回合并主持壳 HTML 并设置 no-store 缓存头。 */
    @Test
    void recPathReturnsMergedHostShell() throws Exception {
        mockMvc.perform(get("/rec/1222b46e-7bcb-4b81-a543-cbd1000586c2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("智能会议")))
                .andExpect(content().string(containsString("事项进度通报")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    /** /host 路径应返回主持页 HTML 并设置 no-store 缓存头。 */
    @Test
    void hostPathReturnsHostHtml() throws Exception {
        mockMvc.perform(get("/host/1222b46e-7bcb-4b81-a543-cbd1000586c2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("智能会议")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    /** /static/index.html 应可直接访问并返回 HTML。 */
    @Test
    void staticIndexDirectlyOk() throws Exception {
        mockMvc.perform(get("/static/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html")));
    }
}
