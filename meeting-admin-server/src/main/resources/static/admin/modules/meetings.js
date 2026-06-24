AdminModules.register({
  route: '/meetings',
  mount: async function (root) {
    const esc = s => (s == null ? '' : String(s)).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
    const fmtDt = s => s ? String(s).replace('T', ' ').slice(0, 19) : '-';
    const meetingStartTime = m => fmtDt(m.actualStartTime || m.scheduledTime);
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
    let obsPanelReady = false;
    let selectedMeetingIds = new Set();
    let lastLoadedMeetings = [];

    const ensureObsPanel = () => {
      if (obsPanelReady) return;
      const host = document.getElementById('m-obs-panel');
      if (!host) return;
      try {
        host.innerHTML = `
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
              <button type="button" class="secondary" id="obs-meeting-view-edit">编辑</button>
            </div>
          </div>
          <div id="obs-meeting-detail-readable"></div>
          <pre id="obs-meeting-detail-raw" class="hidden"></pre>
          <div id="obs-meeting-detail-edit" class="hidden"></div>
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
        </div>`;
        document.getElementById('obs-load').onclick = () => loadObservability();
        document.getElementById('obs-meeting-view-readable').onclick = () => {
          meetingDetailMode = 'readable';
          applyMeetingDetailView();
        };
        document.getElementById('obs-meeting-view-raw').onclick = () => {
          meetingDetailMode = 'raw';
          applyMeetingDetailView();
        };
        document.getElementById('obs-meeting-view-edit').onclick = () => {
          meetingDetailMode = 'edit';
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
        obsPanelReady = true;
      } catch (err) {
        showMsg('数据查看面板初始化失败: ' + (err.message || String(err)), true);
      }
    };

    const setTab = (name) => {
      if (name === 'obs') ensureObsPanel();
      const ops = document.getElementById('m-ops-panel');
      const obs = document.getElementById('m-obs-panel');
      const tabOps = document.getElementById('m-tab-ops');
      const tabObs = document.getElementById('m-tab-obs');
      if (!ops || !obs || !tabOps || !tabObs) return;
      ops.classList.toggle('hidden', name !== 'ops');
      obs.classList.toggle('hidden', name !== 'obs');
      tabOps.classList.toggle('primary', name === 'ops');
      tabObs.classList.toggle('primary', name === 'obs');
      if (name === 'obs') {
        obs.scrollIntoView({ behavior: 'smooth', block: 'start' });
        const obsInput = document.getElementById('obs-meeting-id');
        const hasId = obsInput && String(obsInput.value || '').trim();
        if (!hasId) {
          showMsg('请输入会议 ID 并点「加载」，或从会议列表点击「数据查看」', false);
        }
      }
    };
    const valText = v => (v == null || String(v).trim() === '' ? '-' : String(v));
    const renderMeetingReadable = (detail) => {
      if (!detail || typeof detail !== 'object') {
        return '<div class="muted">暂无会议详情</div>';
      }
      const m = detail.meeting || detail.summary || detail;
      const links = detail.links || {};
      const participants = Array.isArray(detail.participants) ? detail.participants : [];
      const onlineCount = participants.filter(p => String(p && p.attendanceMode || '').toUpperCase() === 'ONLINE').length;
      const offlineCount = participants.filter(p => String(p && p.attendanceMode || '').toUpperCase() === 'OFFLINE').length;
      const infos = [
        ['会议ID', m.id], ['标题', m.title], ['状态', m.status], ['会务类型', m.presetTypeCode],
        ['公司', m.company], ['部门', m.department], ['会议组', m.groupName],
        ['创建人', m.creatorId], ['群 chatId', m.chatId], ['飞书 event_id (roomId)', m.roomId],
        ['场景', m.meetingScenario], ['上次会议', m.previousMeetingId],
        ['计划时间', fmtDt(m.scheduledTime)], ['开始时间', fmtDt(m.actualStartTime)],
        ['结束时间', fmtDt(m.actualEndTime)], ['时长(秒)', m.durationSeconds],
        ['创建时间', fmtDt(m.createdAt)], ['更新时间', fmtDt(m.updatedAt)],
        ['sourceAudioUrl', m.sourceAudioUrl], ['audioPath', m.audioPath],
        ['docUrl', m.docUrl], ['docToken', m.docToken],
        ['recordingUrl', m.recordingUrl], ['recordingToken', m.recordingToken ? '(已设置)' : '-'],
        ['主持页面', links.hostUrl || '-'], ['录音页面', links.recorderUrl || '-'],
        ['参会人数', participants.length || 0], ['线上/线下', onlineCount + ' / ' + offlineCount]
      ];
      const agendaBlock = m.agendaJson ? '<pre class="code-block">' + esc(m.agendaJson) + '</pre>' : '-';
      const hostAgendaBlock = (m.hostAgendaJson || detail.hostAgendaJson)
        ? '<pre class="code-block">' + esc(m.hostAgendaJson || detail.hostAgendaJson) + '</pre>' : '-';
      const partRows = participants.map(p =>
        '<tr><td>' + esc(p.name) + '</td><td>' + esc(p.userId) + '</td><td>' + esc(p.status)
        + '</td><td>' + esc(p.attendanceMode) + '</td><td>' + fmtDt(p.checkedInAt) + '</td></tr>').join('');
      return `
        <div class="meeting-readable-grid">
          ${infos.map(item => '<div class="meeting-readable-item"><span class="k">' + esc(item[0]) + '</span><span class="v">' + esc(valText(item[1])) + '</span></div>').join('')}
        </div>
        <h4 style="margin-top:1rem">议程 agenda</h4>${agendaBlock}
        <h4 style="margin-top:1rem">主持议程 hostAgenda</h4>${hostAgendaBlock}
        <h4 style="margin-top:1rem">参会人</h4>
        <table class="data-table"><thead><tr><th>姓名</th><th>userId</th><th>状态</th><th>到场</th><th>检点</th></tr></thead>
        <tbody>${partRows || '<tr><td colspan="5" class="muted">无</td></tr>'}</tbody></table>
      `;
    };

    const toDatetimeLocal = s => {
      if (!s) return '';
      return String(s).replace(' ', 'T').slice(0, 16);
    };

    const renderMeetingEditForm = (detail) => {
      const m = (detail && detail.meeting) || (detail && detail.summary) || {};
      const notStarted = m.status === 'ISSUE_COLLECTING' || m.status === 'INVITED';
      const dis = notStarted ? '' : ' disabled';
      return `
        <form id="obs-meeting-edit-form" class="form-stack">
          <h4>基本信息</h4>
          <label>标题<input name="title" value="${esc(m.title || '')}"/></label>
          <label>公司<input name="company" value="${esc(m.company || '')}"${dis}/></label>
          <label>部门<input name="department" value="${esc(m.department || '')}"${dis}/></label>
          <label>会议组<input name="groupName" value="${esc(m.groupName || '')}"${dis}/></label>
          <h4>飞书关联</h4>
          <label>创建人 creatorId<input name="creatorId" value="${esc(m.creatorId || '')}"${dis}/></label>
          <label>群 chatId<input name="chatId" value="${esc(m.chatId || '')}"${dis}/></label>
          <label>场景 meetingScenario<input name="meetingScenario" value="${esc(m.meetingScenario || '')}"${dis}/></label>
          <label>上次会议 previousMeetingId<input name="previousMeetingId" value="${esc(m.previousMeetingId || '')}"${dis}/></label>
          <h4>计划时间与改期</h4>
          <label>计划开始时间<input name="scheduledTime" type="datetime-local" value="${toDatetimeLocal(m.scheduledTime)}"${dis}/></label>
          <label><input type="checkbox" name="syncCalendar" checked${dis}/> 保存改期时同步飞书日历</label>
          <h4>议程 JSON</h4>
          <label>agenda（JSON 数组）<textarea name="agendaJson" rows="3">${esc(m.agendaJson || '[]')}</textarea></label>
          <label>hostAgenda<textarea name="hostAgendaJson" rows="4">${esc(m.hostAgendaJson || detail.hostAgendaJson || '{}')}</textarea></label>
          <h4>运维字段</h4>
          <label>sourceAudioUrl<input name="sourceAudioUrl" value="${esc(m.sourceAudioUrl || '')}"/></label>
          <label>audioPath<input name="audioPath" value="${esc(m.audioPath || '')}"/></label>
          <label>docUrl<input name="docUrl" value="${esc(m.docUrl || '')}"/></label>
          <label>docToken<input name="docToken" value="${esc(m.docToken || '')}"/></label>
          <h4>状态</h4>
          <label>status（仅可填 CANCELLED 取消未开始会议）
            <select name="status"${dis}><option value="">不修改</option><option value="CANCELLED">CANCELLED</option></select>
          </label>
          <p class="form-hint">进行中会议请使用「结束」；roomId 为飞书 calendar event_id，只读。</p>
          <button type="submit" class="primary">保存</button>
          <span id="obs-meeting-edit-msg" class="msg hidden"></span>
        </form>`;
    };
    const applyMeetingDetailView = () => {
      const btnReadable = document.getElementById('obs-meeting-view-readable');
      const btnRaw = document.getElementById('obs-meeting-view-raw');
      const btnEdit = document.getElementById('obs-meeting-view-edit');
      const readable = document.getElementById('obs-meeting-detail-readable');
      const raw = document.getElementById('obs-meeting-detail-raw');
      const edit = document.getElementById('obs-meeting-detail-edit');
      if (!btnReadable || !btnRaw || !readable || !raw) return;
      btnReadable.classList.toggle('primary', meetingDetailMode === 'readable');
      btnRaw.classList.toggle('primary', meetingDetailMode === 'raw');
      if (btnEdit) btnEdit.classList.toggle('primary', meetingDetailMode === 'edit');
      readable.classList.toggle('hidden', meetingDetailMode !== 'readable');
      raw.classList.toggle('hidden', meetingDetailMode !== 'raw');
      if (edit) edit.classList.toggle('hidden', meetingDetailMode !== 'edit');
    };
    const bindMeetingEditForm = () => {
      const form = document.getElementById('obs-meeting-edit-form');
      if (!form || !meetingDetailRaw) return;
      form.onsubmit = async (e) => {
        e.preventDefault();
        const msgEl = document.getElementById('obs-meeting-edit-msg');
        const mid = (meetingDetailRaw.meeting || meetingDetailRaw.summary || {}).id;
        if (!mid) return;
        try {
          const fd = new FormData(form);
          let agenda;
          try { agenda = JSON.parse(fd.get('agendaJson') || '[]'); } catch (_) { throw new Error('agenda 须为 JSON 数组'); }
          const body = {
            title: fd.get('title'),
            company: fd.get('company'),
            department: fd.get('department'),
            groupName: fd.get('groupName'),
            creatorId: fd.get('creatorId'),
            chatId: fd.get('chatId'),
            meetingScenario: fd.get('meetingScenario'),
            previousMeetingId: fd.get('previousMeetingId'),
            hostAgendaJson: fd.get('hostAgendaJson'),
            agenda,
            sourceAudioUrl: fd.get('sourceAudioUrl'),
            audioPath: fd.get('audioPath'),
            docUrl: fd.get('docUrl'),
            docToken: fd.get('docToken')
          };
          const st = fd.get('status');
          if (st) body.status = st;
          await AdminApi.fetch('/api/v1/admin/meetings/' + encodeURIComponent(mid), {
            method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
          });
          const stRaw = fd.get('scheduledTime');
          const orig = toDatetimeLocal((meetingDetailRaw.meeting || {}).scheduledTime);
          if (stRaw && stRaw !== orig) {
            const schedBody = {
              scheduledTime: String(stRaw).length === 16 ? stRaw + ':00' : stRaw,
              syncCalendar: !!form.querySelector('[name=syncCalendar]').checked,
              notifyChat: true
            };
            const sr = await AdminApi.fetch('/api/v1/admin/meetings/' + encodeURIComponent(mid) + '/schedule', {
              method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(schedBody)
            });
            if (msgEl) {
              msgEl.className = 'msg msg-ok';
              msgEl.textContent = '已保存；改期' + (sr.calendarSynced ? '已同步日历' : ('日历: ' + (sr.calendarReason || '')));
            }
          } else if (msgEl) {
            msgEl.className = 'msg msg-ok';
            msgEl.textContent = '已保存';
          }
          if (msgEl) msgEl.classList.remove('hidden');
          const detail = await AdminApi.fetch('/api/v1/admin/meetings/' + encodeURIComponent(mid));
          renderMeetingDetail(detail);
          meetingDetailMode = 'readable';
          applyMeetingDetailView();
        } catch (err) {
          if (msgEl) {
            msgEl.className = 'msg msg-err';
            msgEl.textContent = err.message || String(err);
            msgEl.classList.remove('hidden');
          }
        }
      };
    };
    const renderMeetingDetail = (detail) => {
      meetingDetailRaw = detail || null;
      const readable = document.getElementById('obs-meeting-detail-readable');
      const raw = document.getElementById('obs-meeting-detail-raw');
      const edit = document.getElementById('obs-meeting-detail-edit');
      if (readable) readable.innerHTML = renderMeetingReadable(detail);
      if (raw) raw.textContent = JSON.stringify(detail || {}, null, 2);
      if (edit) {
        edit.innerHTML = renderMeetingEditForm(detail);
        bindMeetingEditForm();
      }
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
          <button type="button" class="secondary" id="m-batch-delete" disabled title="勾选会议后批量删除">批量删除</button>
          <span id="m-select-count" class="toolbar-note hidden"></span>
        </div>
      </div>
      <div class="panel table-wrap"><table id="m-table"><thead><tr>
        <th style="width:2.5rem"><input type="checkbox" id="m-select-all" title="全选本页"/></th>
        <th>ID</th><th>标题</th><th>计划时间</th><th>会议开始时间</th><th>状态</th><th>会务</th><th>创建人</th><th>操作</th>
      </tr></thead><tbody></tbody></table></div>
      </div>

      <div id="m-obs-panel" class="hidden"></div>

      `;

    const updateBatchDeleteUi = () => {
      const btn = document.getElementById('m-batch-delete');
      const countEl = document.getElementById('m-select-count');
      const n = selectedMeetingIds.size;
      if (btn) btn.disabled = n === 0;
      if (countEl) {
        countEl.classList.toggle('hidden', n === 0);
        countEl.textContent = '已选 ' + n + ' 场';
      }
      const selectAll = document.getElementById('m-select-all');
      if (selectAll && lastLoadedMeetings.length) {
        const allSelected = lastLoadedMeetings.every(m => selectedMeetingIds.has(m.id));
        selectAll.checked = allSelected;
        selectAll.indeterminate = !allSelected && n > 0;
      } else if (selectAll) {
        selectAll.checked = false;
        selectAll.indeterminate = false;
      }
    };

    const load = async () => {
      const status = document.getElementById('m-status').value;
      const preset = document.getElementById('m-preset').value;
      let url = '/api/v1/admin/meetings?page=1&size=40';
      if (status) url += '&status=' + encodeURIComponent(status);
      if (preset) url += '&presetTypeCode=' + preset;
      const page = await AdminApi.fetch(url);
      lastLoadedMeetings = page.records || [];
      const tbody = document.querySelector('#m-table tbody');
      tbody.innerHTML = '';
      lastLoadedMeetings.forEach(m => {
        const tr = document.createElement('tr');
        const checked = selectedMeetingIds.has(m.id) ? ' checked' : '';
        tr.innerHTML = '<td><input type="checkbox" class="m-row-check" data-id="' + m.id + '"' + checked + '/></td>'
          + '<td>' + m.id + '</td><td>' + esc(m.title || '') + '</td><td>' + fmtDt(m.scheduledTime) + '</td><td>' + meetingStartTime(m) + '</td><td>' + m.status + '</td><td>' + m.presetTypeCode + '</td><td>' + esc(m.creatorId || '') + '</td>'
          + '<td><button type="button" class="link-btn m-det" data-id="' + esc(m.id) + '">数据查看</button>'
          + '<button type="button" class="link-btn m-end" data-id="' + esc(m.id) + '" title="' + AdminHints.meetings.forceEnd.replace(/"/g, '&quot;') + '">结束</button></td>';
        tbody.appendChild(tr);
      });
      tbody.querySelectorAll('input.m-row-check').forEach(cb => {
        cb.onchange = () => {
          const id = cb.dataset.id;
          if (cb.checked) selectedMeetingIds.add(id);
          else selectedMeetingIds.delete(id);
          updateBatchDeleteUi();
        };
      });
      tbody.querySelectorAll('button.m-det').forEach(btn => {
        btn.onclick = async () => {
          const meetingId = btn.dataset.id;
          setTab('obs');
          const obsInput = document.getElementById('obs-meeting-id');
          if (obsInput) obsInput.value = meetingId;
          await loadObservability(meetingId);
        };
      });
      tbody.querySelectorAll('button.m-end').forEach(btn => {
        btn.onclick = async () => {
          const meetingId = btn.dataset.id;
          const ok = await AdminUi.openConfirmModal({
            title: '强制结束会议',
            body: '强制结束会议 ' + meetingId + '？\n\n' + AdminHints.meetings.forceEnd
          });
          if (!ok) return;
          try {
            await AdminApi.fetch('/api/v1/admin/meetings/' + meetingId + '/force-end', { method: 'POST' });
            AdminUi.openResultModal({ title: '结束会议', body: '已请求结束会议: ' + meetingId });
            load();
          } catch (err) {
            AdminUi.openResultModal({ title: '结束会议', body: err.message || '结束失败', isError: true });
          }
        };
      });
      updateBatchDeleteUi();
    };

    const loadObservability = async (idArg) => {
      ensureObsPanel();
      const id = (idArg || (document.getElementById('obs-meeting-id') || {}).value || '').trim();
      if (!id) return showMsg('请输入会议 ID', true);
      const loadBtn = document.getElementById('obs-load');
      const prevLabel = loadBtn ? loadBtn.textContent : '';
      if (loadBtn) {
        loadBtn.disabled = true;
        loadBtn.textContent = '加载中…';
      }
      showMsg('正在加载会议数据…', false);
      try {
        const enc = encodeURIComponent(id);
        const [detail, minute, lines, pushPage, execRows] = await Promise.all([
          AdminApi.fetch('/api/v1/admin/meetings/' + enc),
          AdminApi.fetch('/api/v1/admin/observability/meetings/' + enc + '/minute'),
          AdminApi.fetch('/api/v1/admin/observability/meetings/' + enc + '/transcripts?limit=300'),
          AdminApi.fetch('/api/v1/admin/push-logs?page=0&size=50&meetingId=' + enc),
          AdminApi.fetch('/api/v1/admin/pipeline/executions?meetingId=' + enc)
        ]);
        renderMeetingDetail(detail);

        document.getElementById('obs-minute-readable').textContent = minute.found
          ? (minute.contentMarkdown || '(无正文)') + '\n\nstatus=' + minute.generationStatus
          : '暂无纪要';
        document.getElementById('obs-minute-raw').textContent = JSON.stringify(minute || {}, null, 2);
        document.getElementById('obs-transcript-readable').textContent = (lines || []).map(l =>
          '[' + (l.startTimeMs || 0) + 'ms] ' + (l.speakerName || '') + ': ' + l.text).join('\n');
        document.getElementById('obs-transcript-raw').textContent = JSON.stringify(lines || [], null, 2);

        const pushRows = (pushPage && pushPage.content) ? pushPage.content : [];
        document.getElementById('obs-push-readable').textContent = pushRows.length
          ? pushRows.map(r => '[' + (r.sendTime || '-') + '] ' + (r.status || '-') + ' ' + (r.taskName || r.taskId || '-') + ' -> ' + (r.targetType || '') + ':' + (r.targetId || '')).join('\n')
          : '暂无推送日志';
        document.getElementById('obs-push-raw').textContent = JSON.stringify(pushRows || [], null, 2);

        document.getElementById('obs-pipeline-readable').textContent = (execRows || []).length
          ? (execRows || []).map(e => '#' + e.id + ' [' + (e.stage || '-') + '] ' + (e.status || '-') + ' retry=' + (e.retryCount || 0) + '/' + (e.maxRetries || 0) + (e.lastError ? (' err=' + e.lastError) : '')).join('\n')
          : '暂无流水线执行记录';
        document.getElementById('obs-pipeline-raw').textContent = JSON.stringify(execRows || [], null, 2);

        applySectionView('minute');
        applySectionView('transcript');
        applySectionView('push');
        applySectionView('pipeline');

        showMsg('已加载会议数据: ' + id, false);
        const obsPanel = document.getElementById('m-obs-panel');
        if (obsPanel) obsPanel.scrollIntoView({ behavior: 'smooth', block: 'start' });
      } catch (err) {
        const msg = err.message || String(err);
        showMsg('加载会议数据失败: ' + msg, true);
        AdminUi.openResultModal({ title: '加载会议数据', body: msg, isError: true });
      } finally {
        if (loadBtn) {
          loadBtn.disabled = false;
          loadBtn.textContent = prevLabel || '加载';
        }
      }
    };

    document.getElementById('m-tab-ops').onclick = () => setTab('ops');
    document.getElementById('m-tab-obs').onclick = () => setTab('obs');
    document.getElementById('m-reload').onclick = load;
    document.getElementById('m-select-all').onchange = e => {
      const on = e.target.checked;
      lastLoadedMeetings.forEach(m => {
        if (on) selectedMeetingIds.add(m.id);
        else selectedMeetingIds.delete(m.id);
      });
      document.querySelectorAll('#m-table tbody input.m-row-check').forEach(cb => { cb.checked = on; });
      updateBatchDeleteUi();
    };
    document.getElementById('m-batch-delete').onclick = async () => {
      const ids = Array.from(selectedMeetingIds);
      if (!ids.length) return;
      const summary = ids.slice(0, 8).join('\n') + (ids.length > 8 ? '\n…等共 ' + ids.length + ' 场' : '');
      const ok = await AdminUi.openConfirmModal({
        title: '批量删除会议',
        body: '将删除以下会议及其关联数据（参会人、转写、纪要等）。进行中会议会自动跳过。\n\n' + summary,
        okLabel: '确认删除'
      });
      if (!ok) return;
      try {
        const res = await AdminApi.fetch('/api/v1/admin/meetings/batch-delete', {
          method: 'POST',
          body: JSON.stringify({ meetingIds: ids })
        });
        const skipped = (res && res.skipped) ? res.skipped : [];
        let body = '已删除 ' + (res.deleted || 0) + ' 场';
        if (skipped.length) {
          body += '\n\n跳过 ' + skipped.length + ' 场:\n'
            + skipped.map(s => s.id + ' — ' + s.reason).join('\n');
        }
        ids.forEach(id => selectedMeetingIds.delete(id));
        await load();
        AdminUi.openResultModal({
          title: '批量删除',
          body: body,
          isError: skipped.length > 0 && (res.deleted || 0) === 0
        });
      } catch (err) {
        AdminUi.openResultModal({ title: '批量删除', body: err.message || '删除失败', isError: true });
      }
    };
    setTab('ops');
    load();
  }
});
