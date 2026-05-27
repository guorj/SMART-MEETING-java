AdminModules.register({
  route: '/observability',
  mount: async function (root) {
    root.innerHTML = `
      <div class="panel">
        <div class="panel-head"><h2>会议数据查看</h2><p>${AdminHints.observability.moduleIntro}</p></div>
        <div class="form-field" style="max-width:36rem">
          <label>会议 ID</label>
          <div style="display:flex;gap:0.5rem;align-items:center">
            <input id="obs-meeting-id" type="text" style="flex:1"/>
            <button class="primary" id="obs-load">加载</button>
          </div>
          ${AdminForm.hint(AdminHints.observability.meetingId)}
        </div>
      </div>
      <div class="panel"><h3>纪要</h3><p class="form-hint">已生成的 Markdown 纪要正文及 generationStatus（GENERATING / READY / FAILED 等）。</p><pre id="obs-minute"></pre></div>
      <div class="panel"><h3>转写（final）</h3><p class="form-hint">ASR 最终片段，按 startTimeMs 排序；最多加载 300 条。</p><pre id="obs-transcript" style="max-height:400px;overflow:auto"></pre></div>`;
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
