AdminModules.register({
  route: '/observability',
  mount: async function (root) {
    root.innerHTML = `
      <div class="panel">
        <div class="panel-head">
          <h2>会议数据查看</h2>
          <p>${AdminHints.observability.moduleIntro}</p>
          <p class="form-hint">改期、编辑会议字段请使用侧栏 <button type="button" class="link-btn" id="obs-goto-meetings">会议运维</button> → 数据查看 → 编辑。</p>
        </div>
        <div class="form-field" style="max-width:36rem">
          <label>会议 ID</label>
          <div style="display:flex;gap:0.5rem;align-items:center">
            <input id="obs-meeting-id" type="text" style="flex:1"/>
            <button class="primary" id="obs-load" type="button">加载</button>
          </div>
          ${AdminForm.hint(AdminHints.observability.meetingId)}
        </div>
        <span id="obs-msg" class="msg hidden"></span>
      </div>
      <div class="panel"><h3>纪要</h3><p class="form-hint">已生成的 Markdown 纪要正文及 generationStatus（GENERATING / READY / FAILED 等）。</p><pre id="obs-minute"></pre></div>
      <div class="panel"><h3>转写（final）</h3><p class="form-hint">ASR 最终片段，按 startTimeMs 排序；最多加载 300 条。</p><pre id="obs-transcript" style="max-height:400px;overflow:auto"></pre></div>`;

    const showObsMsg = (text, isErr) => {
      const el = document.getElementById('obs-msg');
      if (!el) return;
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };

    document.getElementById('obs-goto-meetings').onclick = () => {
      location.hash = '#/meetings';
    };

    document.getElementById('obs-load').onclick = async () => {
      const id = document.getElementById('obs-meeting-id').value.trim();
      if (!id) {
        showObsMsg('请输入会议 ID', true);
        return;
      }
      const loadBtn = document.getElementById('obs-load');
      const prevLabel = loadBtn.textContent;
      loadBtn.disabled = true;
      loadBtn.textContent = '加载中…';
      showObsMsg('正在加载…', false);
      try {
        const enc = encodeURIComponent(id);
        const minute = await AdminApi.fetch('/api/v1/admin/observability/meetings/' + enc + '/minute');
        document.getElementById('obs-minute').textContent = minute.found
          ? (minute.contentMarkdown || '(无正文)') + '\n\nstatus=' + minute.generationStatus
          : '暂无纪要';
        const lines = await AdminApi.fetch('/api/v1/admin/observability/meetings/' + enc + '/transcripts?limit=300');
        document.getElementById('obs-transcript').textContent = (lines || []).map(l =>
          '[' + (l.startTimeMs || 0) + 'ms] ' + (l.speakerName || '') + ': ' + l.text).join('\n');
        showObsMsg('已加载会议数据: ' + id, false);
      } catch (err) {
        const msg = err.message || String(err);
        showObsMsg('加载失败: ' + msg, true);
        AdminUi.openResultModal({ title: '加载会议数据', body: msg, isError: true });
      } finally {
        loadBtn.disabled = false;
        loadBtn.textContent = prevLabel;
      }
    };
  }
});
