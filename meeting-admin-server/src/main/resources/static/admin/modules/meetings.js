AdminModules.register({
  route: '/meetings',
  mount: async function (root) {
    const esc = s => (s == null ? '' : String(s)).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
    const fmtDt = s => s ? String(s).replace('T', ' ').slice(0, 19) : '-';
    let meetingDetailRaw = null;
    let meetingDetailMode = 'readable';
    const sectionMode = {
      minute: 'readable',
      transcript: 'readable',
      push: 'readable',
      pipeline: 'readable'
    };
    const showMsg = (text, isErr) => {
      const el = document.getElementById('m-msg');
      if (!el) return;
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };
    const setTab = (name) => {
      const ops = document.getElementById('m-ops-panel');
      const obs = document.getElementById('m-obs-panel');
      const tabOps = document.getElementById('m-tab-ops');
      const tabObs = document.getElementById('m-tab-obs');
      if (!ops || !obs || !tabOps || !tabObs) return;
      ops.classList.toggle('hidden', name !== 'ops');
      obs.classList.toggle('hidden', name !== 'obs');
      tabOps.classList.toggle('primary', name === 'ops');
      tabObs.classList.toggle('primary', name === 'obs');
    };
    const valText = v => (v == null || String(v).trim() === '' ? '-' : String(v));
    const renderMeetingReadable = (detail) => {
      if (!detail || typeof detail !== 'object') {
        return '<div class="muted">暂无会议详情</div>';
      }
      const summary = detail.summary || detail;
      const links = detail.links || {};
      const participants = Array.isArray(detail.participants) ? detail.participants : [];
      const onlineCount = participants.filter(p => String(p && p.attendanceMode || '').toUpperCase() === 'ONLINE').length;
      const offlineCount = participants.filter(p => String(p && p.attendanceMode || '').toUpperCase() === 'OFFLINE').length;
      const pendingCount = participants.filter(p => String(p && p.status || '').toUpperCase() === 'PENDING').length;
      const checkedCount = participants.filter(p => p && p.checkedInAt).length;
      const infos = [
        ['会议ID', summary.id],
        ['标题', summary.title],
        ['状态', summary.status],
        ['会务类型', summary.presetTypeCode],
        ['创建时间', fmtDt(summary.createdAt)],
        ['计划时间', fmtDt(summary.scheduledTime)],
        ['开始时间', fmtDt(summary.actualStartTime || summary.startedAt)],
        ['结束时间', fmtDt(summary.actualEndTime || summary.endedAt)],
        ['主持页面', links.hostUrl || '-'],
        ['录音页面', links.recorderUrl || '-'],
        ['参会人数', participants.length || 0],
        ['线上/线下', onlineCount + ' / ' + offlineCount],
        ['待检点/已检点', pendingCount + ' / ' + checkedCount]
      ];
      return `
        <div class="meeting-readable-grid">
          ${infos.map(item => '<div class="meeting-readable-item"><span class="k">' + esc(item[0]) + '</span><span class="v">' + esc(valText(item[1])) + '</span></div>').join('')}
        </div>
      `;
    };
    const applyMeetingDetailView = () => {
      const btnReadable = document.getElementById('obs-meeting-view-readable');
      const btnRaw = document.getElementById('obs-meeting-view-raw');
      const readable = document.getElementById('obs-meeting-detail-readable');
      const raw = document.getElementById('obs-meeting-detail-raw');
      if (!btnReadable || !btnRaw || !readable || !raw) return;
      const showReadable = meetingDetailMode !== 'raw';
      btnReadable.classList.toggle('primary', showReadable);
      btnRaw.classList.toggle('primary', !showReadable);
      readable.classList.toggle('hidden', !showReadable);
      raw.classList.toggle('hidden', showReadable);
    };
    const renderMeetingDetail = (detail) => {
      meetingDetailRaw = detail || null;
      const readable = document.getElementById('obs-meeting-detail-readable');
      const raw = document.getElementById('obs-meeting-detail-raw');
      if (readable) readable.innerHTML = renderMeetingReadable(detail);
      if (raw) raw.textContent = JSON.stringify(detail || {}, null, 2);
      applyMeetingDetailView();
    };
    const applySectionView = (name) => {
      const readable = document.getElementById('obs-' + name + '-readable');
      const raw = document.getElementById('obs-' + name + '-raw');
      const btnReadable = document.getElementById('obs-' + name + '-view-readable');
      const btnRaw = document.getElementById('obs-' + name + '-view-raw');
      if (!readable || !raw || !btnReadable || !btnRaw) return;
      const showReadable = sectionMode[name] !== 'raw';
      readable.classList.toggle('hidden', !showReadable);
      raw.classList.toggle('hidden', showReadable);
      btnReadable.classList.toggle('primary', showReadable);
      btnRaw.classList.toggle('primary', !showReadable);
    };

    root.innerHTML = `
      <div class="panel">
        <div class="panel-head"><h2>会议运维 + 数据查看</h2><p>${AdminHints.meetings.moduleIntro}</p></div>
        <div class="toolbar" style="margin-bottom:0.75rem;">
          <button type="button" class="primary" id="m-tab-ops">会议运维</button>
          <button type="button" id="m-tab-obs">数据查看</button>
          <span id="m-msg" class="msg hidden"></span>
        </div>
      </div>

      <div id="m-ops-panel">
      <div class="panel">
        <div class="toolbar">
          <label class="field-inline">状态<select id="m-status" title="${AdminHints.meetings.status.replace(/"/g, '&quot;')}"><option value="">全部</option>
            <option>ISSUE_COLLECTING</option><option>INVITED</option><option>STARTED</option><option>RECORDING</option>
            <option>PROCESSING</option><option>COMPLETED</option><option>PAUSED</option><option>CANCELLED</option></select></label>
          <label class="field-inline">会务<select id="m-preset" title="${AdminHints.meetings.preset.replace(/"/g, '&quot;')}"><option value="">全部</option><option>1</option><option>2</option><option>3</option><option>4</option><option>5</option></select></label>
          <button type="button" class="primary" id="m-reload">刷新</button>
        </div>
      </div>
      <div class="panel table-wrap"><table id="m-table"><thead><tr>
        <th>ID</th><th>标题</th><th title="会议生命周期状态">状态</th><th title="会务类型 presetTypeCode">会务</th><th>操作</th>
      </tr></thead><tbody></tbody></table></div>
      </div>

      <div id="m-obs-panel" class="hidden">
        <div class="panel">
          <div class="form-field" style="max-width:36rem">
            <label>会议 ID</label>
            <div style="display:flex;gap:0.5rem;align-items:center">
              <input id="obs-meeting-id" type="text" style="flex:1"/>
              <button class="primary" id="obs-load">加载</button>
            </div>
            ${AdminForm.hint(AdminHints.observability.meetingId + ' 输入后将一次聚合加载：会议详情、纪要、转写、推送日志、流水线执行。')}
          </div>
        </div>
        <div class="panel">
          <div class="toolbar" style="justify-content:space-between;margin-bottom:0.5rem;">
            <h3 style="margin:0;">会议详情</h3>
            <div class="btn-group">
              <button type="button" class="secondary" id="obs-meeting-view-readable">可读视图</button>
              <button type="button" class="secondary" id="obs-meeting-view-raw">原始JSON</button>
            </div>
          </div>
          <div id="obs-meeting-detail-readable"></div>
          <pre id="obs-meeting-detail-raw" class="hidden"></pre>
        </div>
        <div class="panel">
          <div class="toolbar" style="justify-content:space-between;margin-bottom:0.5rem;">
            <h3 style="margin:0;">纪要</h3>
            <div class="btn-group">
              <button type="button" class="secondary" id="obs-minute-view-readable">可读视图</button>
              <button type="button" class="secondary" id="obs-minute-view-raw">原始JSON</button>
            </div>
          </div>
          <p class="form-hint">已生成的 Markdown 纪要正文及 generationStatus（GENERATING / READY / FAILED 等）。</p>
          <pre id="obs-minute-readable"></pre>
          <pre id="obs-minute-raw" class="hidden"></pre>
        </div>
        <div class="panel">
          <div class="toolbar" style="justify-content:space-between;margin-bottom:0.5rem;">
            <h3 style="margin:0;">转写（final）</h3>
            <div class="btn-group">
              <button type="button" class="secondary" id="obs-transcript-view-readable">可读视图</button>
              <button type="button" class="secondary" id="obs-transcript-view-raw">原始JSON</button>
            </div>
          </div>
          <p class="form-hint">ASR 最终片段，按 startTimeMs 排序；最多加载 300 条。</p>
          <pre id="obs-transcript-readable" style="max-height:400px;overflow:auto"></pre>
          <pre id="obs-transcript-raw" class="hidden" style="max-height:400px;overflow:auto"></pre>
        </div>
        <div class="panel">
          <div class="toolbar" style="justify-content:space-between;margin-bottom:0.5rem;">
            <h3 style="margin:0;">推送日志（meetingId）</h3>
            <div class="btn-group">
              <button type="button" class="secondary" id="obs-push-view-readable">可读视图</button>
              <button type="button" class="secondary" id="obs-push-view-raw">原始JSON</button>
            </div>
          </div>
          <pre id="obs-push-readable" style="max-height:280px;overflow:auto"></pre>
          <pre id="obs-push-raw" class="hidden" style="max-height:280px;overflow:auto"></pre>
        </div>
        <div class="panel">
          <div class="toolbar" style="justify-content:space-between;margin-bottom:0.5rem;">
            <h3 style="margin:0;">流水线执行（meetingId）</h3>
            <div class="btn-group">
              <button type="button" class="secondary" id="obs-pipeline-view-readable">可读视图</button>
              <button type="button" class="secondary" id="obs-pipeline-view-raw">原始JSON</button>
            </div>
          </div>
          <pre id="obs-pipeline-readable" style="max-height:280px;overflow:auto"></pre>
          <pre id="obs-pipeline-raw" class="hidden" style="max-height:280px;overflow:auto"></pre>
        </div>
      </div>

      `;

    const load = async () => {
      const status = document.getElementById('m-status').value;
      const preset = document.getElementById('m-preset').value;
      let url = '/api/v1/admin/meetings?page=1&size=40';
      if (status) url += '&status=' + encodeURIComponent(status);
      if (preset) url += '&presetTypeCode=' + preset;
      const page = await AdminApi.fetch(url);
      const tbody = document.querySelector('#m-table tbody');
      tbody.innerHTML = '';
      (page.records || []).forEach(m => {
        const tr = document.createElement('tr');
        tr.innerHTML = '<td>' + m.id + '</td><td>' + (m.title || '') + '</td><td>' + m.status + '</td><td>' + m.presetTypeCode + '</td><td><a href="#" class="m-det" data-id="' + m.id + '">数据查看</a> <a href="#" class="m-end" data-id="' + m.id + '" title="' + AdminHints.meetings.forceEnd.replace(/"/g, '&quot;') + '">结束</a></td>';
        tbody.appendChild(tr);
      });
      tbody.querySelectorAll('a.m-det').forEach(a => {
        a.onclick = async e => {
          e.preventDefault();
          const meetingId = a.dataset.id;
          document.getElementById('obs-meeting-id').value = meetingId;
          setTab('obs');
          await loadObservability(meetingId);
        };
      });
      tbody.querySelectorAll('a.m-end').forEach(a => {
        a.onclick = async e => {
          e.preventDefault();
          if (!confirm('强制结束会议 ' + a.dataset.id + '？\n\n' + AdminHints.meetings.forceEnd)) return;
          try {
            await AdminApi.fetch('/api/v1/admin/meetings/' + a.dataset.id + '/force-end', { method: 'POST' });
            showMsg('已请求结束会议: ' + a.dataset.id, false);
            load();
          } catch (err) {
            showMsg('结束会议失败: ' + err.message, true);
          }
        };
      });
    };

    const loadObservability = async (idArg) => {
      const id = (idArg || document.getElementById('obs-meeting-id').value || '').trim();
      if (!id) return showMsg('请输入会议 ID', true);
      try {
        const detail = await AdminApi.fetch('/api/v1/admin/meetings/' + encodeURIComponent(id));
        renderMeetingDetail(detail);

        const minute = await AdminApi.fetch('/api/v1/admin/observability/meetings/' + encodeURIComponent(id) + '/minute');
        document.getElementById('obs-minute-readable').textContent = minute.found
          ? (minute.contentMarkdown || '(无正文)') + '\n\nstatus=' + minute.generationStatus
          : '暂无纪要';
        document.getElementById('obs-minute-raw').textContent = JSON.stringify(minute || {}, null, 2);
        const lines = await AdminApi.fetch('/api/v1/admin/observability/meetings/' + encodeURIComponent(id) + '/transcripts?limit=300');
        document.getElementById('obs-transcript-readable').textContent = (lines || []).map(l =>
          '[' + (l.startTimeMs || 0) + 'ms] ' + (l.speakerName || '') + ': ' + l.text).join('\n');
        document.getElementById('obs-transcript-raw').textContent = JSON.stringify(lines || [], null, 2);

        const pushPage = await AdminApi.fetch('/api/v1/admin/push-logs?page=0&size=50&meetingId=' + encodeURIComponent(id));
        const pushRows = (pushPage && pushPage.content) ? pushPage.content : [];
        document.getElementById('obs-push-readable').textContent = pushRows.length
          ? pushRows.map(r => '[' + (r.sendTime || '-') + '] ' + (r.status || '-') + ' ' + (r.taskName || r.taskId || '-') + ' -> ' + (r.targetType || '') + ':' + (r.targetId || '')).join('\n')
          : '暂无推送日志';
        document.getElementById('obs-push-raw').textContent = JSON.stringify(pushRows || [], null, 2);

        const execRows = await AdminApi.fetch('/api/v1/admin/pipeline/executions?meetingId=' + encodeURIComponent(id));
        document.getElementById('obs-pipeline-readable').textContent = (execRows || []).length
          ? (execRows || []).map(e => '#' + e.id + ' [' + (e.stage || '-') + '] ' + (e.status || '-') + ' retry=' + (e.retryCount || 0) + '/' + (e.maxRetries || 0) + (e.lastError ? (' err=' + e.lastError) : '')).join('\n')
          : '暂无流水线执行记录';
        document.getElementById('obs-pipeline-raw').textContent = JSON.stringify(execRows || [], null, 2);

        applySectionView('minute');
        applySectionView('transcript');
        applySectionView('push');
        applySectionView('pipeline');

        showMsg('已加载会议数据: ' + id, false);
      } catch (err) {
        showMsg('加载会议数据失败: ' + err.message, true);
      }
    };

    document.getElementById('m-tab-ops').onclick = () => setTab('ops');
    document.getElementById('m-tab-obs').onclick = () => setTab('obs');
    document.getElementById('m-reload').onclick = load;
    document.getElementById('obs-load').onclick = loadObservability;
    document.getElementById('obs-meeting-view-readable').onclick = () => {
      meetingDetailMode = 'readable';
      applyMeetingDetailView();
    };
    document.getElementById('obs-meeting-view-raw').onclick = () => {
      meetingDetailMode = 'raw';
      applyMeetingDetailView();
    };
    document.getElementById('obs-minute-view-readable').onclick = () => { sectionMode.minute = 'readable'; applySectionView('minute'); };
    document.getElementById('obs-minute-view-raw').onclick = () => { sectionMode.minute = 'raw'; applySectionView('minute'); };
    document.getElementById('obs-transcript-view-readable').onclick = () => { sectionMode.transcript = 'readable'; applySectionView('transcript'); };
    document.getElementById('obs-transcript-view-raw').onclick = () => { sectionMode.transcript = 'raw'; applySectionView('transcript'); };
    document.getElementById('obs-push-view-readable').onclick = () => { sectionMode.push = 'readable'; applySectionView('push'); };
    document.getElementById('obs-push-view-raw').onclick = () => { sectionMode.push = 'raw'; applySectionView('push'); };
    document.getElementById('obs-pipeline-view-readable').onclick = () => { sectionMode.pipeline = 'readable'; applySectionView('pipeline'); };
    document.getElementById('obs-pipeline-view-raw').onclick = () => { sectionMode.pipeline = 'raw'; applySectionView('pipeline'); };
    renderMeetingDetail(meetingDetailRaw);
    applySectionView('minute');
    applySectionView('transcript');
    applySectionView('push');
    applySectionView('pipeline');
    setTab('ops');
    load();
  }
});
