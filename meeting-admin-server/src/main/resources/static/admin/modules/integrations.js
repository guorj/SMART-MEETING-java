AdminModules.register({
  route: '/integrations',
  mount: async function (root) {
    const links = await AdminApi.fetch('/api/v1/admin/integrations/links');
    root.innerHTML = `
      <div class="panel"><h3>三进程入口</h3>
        <ul>
          <li>会议管理后台（本进程）：<a href="/admin" target="_blank">/admin</a></li>
          <li>会中服务 meeting-server：<a href="${links.meetingServer}" target="_blank">${links.meetingServer}</a></li>
          <li>定时 bot feishu-scheduled-bot：<a href="${links.feishuBot}" target="_blank">${links.feishuBot}</a></li>
        </ul>
      </div>
      <div class="panel"><h3>运维快捷</h3>
        <ul>
          <li>Bot 手动触发对比：<code>${links.feishuBotExecute}</code>（POST，见 USER-MANUAL）</li>
          <li>主持页示例：<code>${links.meetingHostExample}</code></li>
        </ul>
        <p class="msg">${links.reverseProxyHint}</p>
      </div>`;
  }
});
