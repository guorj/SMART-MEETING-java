AdminModules.register({
  route: '/integrations',
  mount: async function (root) {
    const links = await AdminApi.fetch('/api/v1/admin/integrations/links');
    let healthHtml = '<p class="muted">加载健康状态…</p>';
    root.innerHTML = `
      <div class="panel">
        <div class="panel-head"><h2>集成与入口</h2><p>${AdminHints.integrations.moduleIntro}</p></div>
        <h3>三进程入口</h3>
        <ul>
          <li>会议管理后台（本进程）：<a href="/admin" target="_blank">/admin</a></li>
          <li>会中服务 meeting-server：<a href="${links.meetingServer}" target="_blank">${links.meetingServer}</a></li>
          <li>定时 bot feishu-scheduled-bot：<a href="${links.feishuBot}" target="_blank">${links.feishuBot}</a></li>
        </ul>
      </div>
      <div class="panel" id="int-health"><h3>健康探测</h3><p class="form-hint">${AdminHints.integrations.health}</p>${healthHtml}</div>
      <div class="panel"><h3>运维快捷</h3>
        <ul>
          <li>推送调度模块：<a href="#/push-bot">#/push-bot</a> — 配置 INTERNAL/EXTERNAL 推送任务</li>
          <li>对比任务模块：<a href="#/weekly-jobs">#/weekly-jobs</a> — 周报事项对比 Cron</li>
          <li>主持页示例：<code>${links.meetingHostExample}</code></li>
        </ul>
        <p class="form-hint">${AdminHints.integrations.reverseProxy}</p>
        <p class="msg">${links.reverseProxyHint}</p>
      </div>`;
    try {
      const health = await AdminApi.fetch('/api/v1/admin/integrations/health');
      const bot = health.bot || {};
      document.getElementById('int-health').innerHTML = '<h3>健康探测</h3><p class="form-hint">' + AdminHints.integrations.health + '</p><ul>'
        + '<li>feishu-scheduled-bot：' + (bot.reachable ? '✅ 可达' : '❌ ' + (bot.message || '不可达')) + '</li>'
        + '<li>meeting-server 桥接：' + (health.meetingServer && health.meetingServer.configured ? '已配置 internal token' : '未配置') + '</li>'
        + '</ul><p class="form-hint">Bot 环境变量：MEETING_NOTIFY_BOT_URL；密钥 SCHEDULED_BOT_APIKEY（默认 jq_int_meeting_key，三进程须一致）</p>';
    } catch (e) {
      document.getElementById('int-health').innerHTML = '<h3>健康探测</h3><p class="form-hint">' + AdminHints.integrations.health + '</p><p class="msg msg-err">' + e.message + '</p>';
    }
  }
});
