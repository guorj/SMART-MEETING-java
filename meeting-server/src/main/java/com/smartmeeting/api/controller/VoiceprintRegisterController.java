package com.smartmeeting.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.service.VoiceprintRegisterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

/**
 * 声纹注册控制器。
 * <p>
 * 端点：
 * <ul>
 *   <li>{@code GET /voiceprint?token=xxx} — 声纹注册页面（HTML）</li>
 *   <li>{@code POST /api/v1/voiceprint/register} — 提交音频完成注册</li>
 * </ul>
 *
 * @see VoiceprintRegisterService
 */
@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class VoiceprintRegisterController {

    private final VoiceprintRegisterService registerService;

    /**
     * 声纹注册页面：校验 token 后返回内嵌录音 UI 的 HTML。
     *
     * @param token 注册会话 token（飞书指令下发链接中的参数）
     * @return HTML 页面；token 无效时返回错误页
     */
    @GetMapping("/voiceprint")
    public ResponseEntity<String> getRegisterPage(@RequestParam("token") String token) {
        VoiceprintRegisterService.RegisterSession session = registerService.getRegisterSession(token);
        
        if (session == null) {
            return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(buildErrorPage("注册链接已过期或无效"));
        }
        
        String userName = session.getUserName();
        return ResponseEntity.ok()
            .header("Content-Type", "text/html; charset=UTF-8")
            .body(buildRegisterPage(token, userName));
    }

    /**
     * 提交声纹注册：接收 Base64 编码的 WebM 音频并完成特征入库。
     *
     * @param body 请求体，须含 {@code token} 与 {@code audio}（Base64）
     * @return 含 success、message、featureId 的结果 Map
     */
    @PostMapping("/api/v1/voiceprint/register")
    public ResponseEntity<Map<String, Object>> submitRegister(@RequestBody Map<String, Object> body) {
        String token = (String) body.get("token");
        String audioBase64 = (String) body.get("audio");
        
        if (token == null || audioBase64 == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "缺少token或audio参数"
            ));
        }
        
        try {
            byte[] audioData = Base64.getDecoder().decode(audioBase64);
            
            VoiceprintRegisterService.RegisterResult result = registerService.submitRegister(token, audioData);
            
            return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage(),
                "featureId", result.getFeatureId() != null ? result.getFeatureId() : ""
            ));
            
        } catch (Exception e) {
            log.error("声纹注册提交失败", e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "处理失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 构建注册页面 HTML（内联样式与录音脚本，避免额外静态资源依赖）。
     *
     * @param token    注册 token，注入前端提交请求
     * @param userName 展示用用户姓名
     * @return 完整 HTML 字符串
     */
    private String buildRegisterPage(String token, String userName) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>声纹注册</title>\n");
        html.append("<style>\n");
        html.append("body{font-family:sans-serif;background:#f5f5f5;display:flex;justify-content:center;align-items:center;min-height:100vh;}\n");
        html.append(".card{background:#fff;border-radius:16px;padding:24px;box-shadow:0 2px 12px rgba(0,0,0,0.08);max-width:400px;}\n");
        html.append(".header{text-align:center;margin-bottom:20px;}\n");
        html.append(".user-info{background:#f0f7ff;border-radius:8px;padding:12px;text-align:center;margin-bottom:16px;}\n");
        html.append(".user-info .name{font-size:18px;font-weight:600;color:#1677ff;}\n");
        html.append(".phrases{background:#fafafa;border-radius:8px;padding:16px;margin-bottom:20px;font-size:14px;color:#666;}\n");
        html.append(".btn{width:100%;height:48px;border:none;border-radius:24px;font-size:16px;font-weight:600;cursor:pointer;}\n");
        html.append(".btn-start{background:#1677ff;color:#fff;}\n");
        html.append(".btn-recording{background:#ff4d4f;color:#fff;}\n");
        html.append(".btn-submit{background:#52c41a;color:#fff;}\n");
        html.append(".btn:disabled{background:#ccc;color:#999;cursor:not-allowed;}\n");
        html.append(".timer{text-align:center;margin:16px 0;font-size:32px;font-weight:600;color:#ff4d4f;}\n");
        html.append(".status{text-align:center;padding:12px;border-radius:8px;margin-top:16px;}\n");
        html.append(".success{background:#f6ffed;color:#52c41a;}\n");
        html.append(".error{background:#fff2f0;color:#ff4d4f;}\n");
        html.append(".info{background:#f0f7ff;color:#1677ff;}\n");
        html.append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("<div class=\"card\">\n");
        html.append("<div class=\"header\"><h1>声纹注册</h1><p>请朗读以下句子完成注册</p></div>\n");
        html.append("<div class=\"user-info\"><div class=\"name\">").append(userName).append("</div></div>\n");
        html.append("<div class=\"phrases\">\n");
        html.append("1. 我是智能会议系统的用户，现在正在进行声纹注册<br/>\n");
        html.append("2. 今天天气真不错，适合出去散散步放松一下心情<br/>\n");
        html.append("3. 科技创新改变生活，人工智能让工作变得更加高效\n");
        html.append("</div>\n");
        html.append("<button id=\"recordBtn\" class=\"btn btn-start\">开始录音</button>\n");
        html.append("<div id=\"timer\" class=\"timer\" style=\"display:none;\">00:00</div>\n");
        html.append("<div id=\"status\" class=\"status info\" style=\"display:none;\">正在处理...</div>\n");
        html.append("<p style=\"text-align:center;color:#999;font-size:12px;margin-top:20px;\">注册链接30分钟内有效</p>\n");
        html.append("</div>\n");
        html.append("<script>\n");
        html.append("let mediaRecorder=null,audioChunks=[],startTime=null,timerInterval=null;\n");
        html.append("const token='" + token + "',MIN_DURATION=5;\n");
        html.append("const recordBtn=document.getElementById('recordBtn'),timerDiv=document.getElementById('timer'),statusDiv=document.getElementById('status');\n");
        html.append("function showStatus(msg,type){statusDiv.textContent=msg;statusDiv.className='status '+type;statusDiv.style.display='block';}\n");
        html.append("function updateTimer(){const elapsed=Math.floor((Date.now()-startTime)/1000);timerDiv.textContent=Math.floor(elapsed/60).toString().padStart(2,'0')+':'+(elapsed%60).toString().padStart(2,'0');}\n");
        html.append("async function startRecording(){\n");
        html.append("try{\n");
        html.append("const stream=await navigator.mediaDevices.getUserMedia({audio:true});\n");
        html.append("mediaRecorder=new MediaRecorder(stream);audioChunks=[];\n");
        html.append("mediaRecorder.ondataavailable=(e)=>{audioChunks.push(e.data);};\n");
        html.append("mediaRecorder.onstop=async()=>{\n");
        html.append("clearInterval(timerInterval);\n");
        html.append("const elapsed=(Date.now()-startTime)/1000;\n");
        html.append("if(elapsed<MIN_DURATION){showStatus('录音时长不足5秒，请重新录制','error');recordBtn.className='btn btn-start';recordBtn.textContent='开始录音';timerDiv.style.display='none';return;}\n");
        html.append("showStatus('正在处理音频...','info');recordBtn.disabled=true;\n");
        html.append("const blob=new Blob(audioChunks,{type:'audio/webm'});\n");
        html.append("const arrayBuffer=await blob.arrayBuffer();\n");
        html.append("const base64=btoa(String.fromCharCode(...new Uint8Array(arrayBuffer)));\n");
        html.append("try{\n");
        html.append("const res=await fetch('/api/v1/voiceprint/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token,audio:base64})});\n");
        html.append("const data=await res.json();\n");
        html.append("if(data.success){showStatus(data.message,'success');recordBtn.textContent='注册成功';recordBtn.className='btn btn-submit';}else{showStatus(data.message,'error');recordBtn.disabled=false;recordBtn.className='btn btn-start';recordBtn.textContent='重新录音';}\n");
        html.append("}catch(err){showStatus('提交失败:'+err.message,'error');recordBtn.disabled=false;recordBtn.className='btn btn-start';recordBtn.textContent='重新录音';}\n");
        html.append("};\n");
        html.append("mediaRecorder.start();startTime=Date.now();timerDiv.style.display='block';timerInterval=setInterval(updateTimer,1000);\n");
        html.append("recordBtn.className='btn btn-recording';recordBtn.textContent='停止录音';statusDiv.style.display='none';\n");
        html.append("}catch(err){showStatus('无法访问麦克风:'+err.message,'error');}\n");
        html.append("}\n");
        html.append("function stopRecording(){if(mediaRecorder&&mediaRecorder.state==='recording'){mediaRecorder.stop();mediaRecorder.stream.getTracks().forEach(t=>t.stop());}}\n");
        html.append("recordBtn.addEventListener('click',()=>{if(mediaRecorder&&mediaRecorder.state==='recording'){stopRecording();}else{startRecording();}});\n");
        html.append("</script>\n");
        html.append("</body>\n");
        html.append("</html>");
        return html.toString();
    }

    /**
     * 构建 token 无效或过期时的错误提示页。
     *
     * @param message 展示给用户的错误说明
     * @return 完整 HTML 字符串
     */
    private String buildErrorPage(String message) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head><meta charset=\"UTF-8\"><title>错误</title>\n");
        html.append("<style>body{font-family:sans-serif;background:#f5f5f5;display:flex;justify-content:center;align-items:center;min-height:100vh;}.card{background:#fff;border-radius:16px;padding:32px;text-align:center;box-shadow:0 2px 12px rgba(0,0,0,0.08);}.icon{font-size:48px;color:#ff4d4f;}.message{color:#333;margin-top:16px;}</style>\n");
        html.append("</head>\n");
        html.append("<body><div class=\"card\"><div class=\"icon\">X</div><div class=\"message\">").append(message).append("</div></div></body>\n");
        html.append("</html>");
        return html.toString();
    }
}