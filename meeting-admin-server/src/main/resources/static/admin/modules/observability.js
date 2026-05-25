AdminModules.register({
  route: '/observability',
  mount: async function (root) {
    root.innerHTML = `
      <div class="panel">
        <label>会议 ID <input id="obs-meeting-id" style="width:320px"/></label>
        <button class="primary" id="obs-load">加载</button>
      </div>
      <div class="panel"><h3>纪要</h3><pre id="obs-minute"></pre></div>
      <div class="panel"><h3>转写（final）</h3><pre id="obs-transcript" style="max-height:400px;overflow:auto"></pre></div>`;
    document.getElementById('obs-load').onclick = async () => {
      const id = document.getElementById('obs-meeting-id').value.trim();
      if (!id) return alert('请输入会议 ID');
      const minute = await AdminApi.fetch('/api/v1/admin/observability/meetings/' + id + '/minute');
      document.getElementById('obs-minute').textContent = minute.found
        ? (minute.contentMarkdown || '(无正文)') + '\n\nstatus=' + minute.generationStatus
        : '暂无纪要';
      const lines = await AdminApi.fetch('/api/v1/admin/observability/meetings/' + id + '/transcripts?limit=300');
      document.getElementById('obs-transcript').textContent = lines.map(l =>
        '[' + (l.startTimeMs || 0) + 'ms] ' + (l.speakerName || '') + ': ' + l.text).join('\n');
    };
  }
});
