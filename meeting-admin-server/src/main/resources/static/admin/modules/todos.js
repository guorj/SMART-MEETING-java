AdminModules.register({
  route: '/todos',
  mount: async function (root) {
    const esc = s => (s == null ? '' : String(s)).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
    const fmtDt = s => s ? String(s).replace('T', ' ').slice(0, 19) : '-';
    const STATUSES = ['PENDING', 'IN_PROGRESS', 'COMPLETED', 'BLOCKED'];
    const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
    let stats = null;

    root.innerHTML = `
      <div class="panel">
        <div class="panel-head">
          <h2>待办追踪</h2>
          <p>查看与管理会议待办事项，支持按会议、责任人、状态筛选，手动推进状态或编辑内容。</p>
        </div>
        <div class="toolbar" style="margin-bottom:0.75rem;">
          <button class="primary" id="td-new">新增待办</button>
          <button class="secondary" id="td-reload">刷新</button>
          <span id="td-msg" class="msg hidden"></span>
        </div>
        <div id="td-stats" class="form-hint"></div>
      </div>

      <div class="panel">
        <h3>筛选</h3>
        <div class="toolbar" style="flex-wrap:wrap;gap:0.5rem;">
          <input id="td-f-meeting" placeholder="会议ID" style="min-width:14rem"/>
          <input id="td-f-assignee" placeholder="责任人ID" style="min-width:12rem"/>
          <select id="td-f-status">
            <option value="">全部状态</option>
            ${STATUSES.map(s => `<option value="${s}">${s}</option>`).join('')}
          </select>
          <input id="td-f-preset" type="number" placeholder="presetTypeCode" style="min-width:7rem"/>
          <input id="td-f-keyword" placeholder="内容/姓名关键词" style="min-width:12rem"/>
          <button class="secondary" id="td-search">查询</button>
        </div>
      </div>

      <div class="panel table-wrap">
        <h3>待办列表</h3>
        <table><thead><tr>
          <th>ID</th><th>会议</th><th>内容</th><th>责任人</th><th>经办人</th><th>状态</th><th>优先级</th><th>截止</th><th>提醒</th><th>创建</th><th>操作</th>
        </tr></thead><tbody id="td-body"></tbody></table>
        <div class="toolbar" style="margin-top:0.5rem;">
          <button class="secondary" id="td-prev">上一页</button>
          <span id="td-page-info" class="form-hint"></span>
          <button class="secondary" id="td-next">下一页</button>
        </div>
      </div>

      <div class="panel hidden form-editor" id="td-editor">
        <h3 id="td-editor-title">待办</h3>
        <input type="hidden" id="td-form-id"/>
        ${AdminForm.field('meetingId', '<input id="td-f-meeting-id" type="text" placeholder="会议UUID"/>', '来源会议ID（必填）。')}
        ${AdminForm.field('presetTypeCode', '<input id="td-f-preset-code" type="number" placeholder="1-5，可选"/>', '会务预设编号，可选。')}
        ${AdminForm.field('content', '<textarea id="td-f-content" rows="3" placeholder="待办内容"></textarea>', '待办内容（必填）。')}
        ${AdminForm.field('assigneeId', '<input id="td-f-assignee-id" type="text" placeholder="飞书user_id"/>', '责任人飞书user_id。')}
        ${AdminForm.field('assigneeName', '<input id="td-f-assignee-name" type="text" placeholder="责任人姓名"/>', '责任人姓名。')}
        ${AdminForm.field('operatorId', '<input id="td-f-operator-id" type="text" placeholder="飞书user_id"/>', '经办人飞书user_id。')}
        ${AdminForm.field('operatorName', '<input id="td-f-operator-name" type="text" placeholder="经办人姓名"/>', '经办人姓名。')}
        <div class="form-field">
          <label>status</label>
          <select id="td-f-status-edit">${STATUSES.map(s => `<option value="${s}">${s}</option>`).join('')}</select>
          ${AdminForm.hint('PENDING=待办，IN_PROGRESS=进行中，COMPLETED=已完成，BLOCKED=阻塞。')}
        </div>
        <div class="form-field">
          <label>priority</label>
          <select id="td-f-priority">${PRIORITIES.map(p => `<option value="${p}">${p}</option>`).join('')}</select>
        </div>
        ${AdminForm.field('deadline', '<input id="td-f-deadline" type="datetime-local"/>', '截止时间，可选。')}
        ${AdminForm.field('completionNote', '<textarea id="td-f-completion-note" rows="2" placeholder="完成说明（可选）"></textarea>', '完成说明。')}
        ${AdminForm.field('blockReason', '<textarea id="td-f-block-reason" rows="2" placeholder="卡点/延期原因（可选）"></textarea>', '卡点/延期原因。')}
        <p id="td-save-msg" class="msg hidden"></p>
        <div class="toolbar" style="justify-content:flex-end;">
          <button type="button" class="primary" id="td-save">保存</button>
          <button type="button" id="td-cancel">取消</button>
        </div>
      </div>

      <div class="panel hidden" id="td-detail">
        <h3>待办详情</h3>
        <div id="td-detail-body" class="form-hint"></div>
        <button type="button" class="secondary" id="td-detail-close">关闭</button>
      </div>
    `;

    const showMsg = (text, isErr) => {
      const el = document.getElementById('td-msg');
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };
    const showSaveMsg = (text, isErr) => {
      const el = document.getElementById('td-save-msg');
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };

    let page = 1;
    const size = 20;
    let totalPages = 1;

    const loadStats = async () => {
      try {
        stats = await AdminApi.fetch('/api/v1/admin/todos/stats');
        const el = document.getElementById('td-stats');
        const bs = stats.byStatus || {};
        el.textContent = `总计 ${stats.total || 0} 条 · 待办 ${bs.PENDING || 0} · 进行中 ${bs.IN_PROGRESS || 0} · 已完成 ${bs.COMPLETED || 0} · 阻塞 ${bs.BLOCKED || 0}`;
      } catch (e) { /* ignore */ }
    };

    const loadList = async () => {
      const params = new URLSearchParams();
      params.set('page', page);
      params.set('size', size);
      const meeting = document.getElementById('td-f-meeting').value.trim();
      const assignee = document.getElementById('td-f-assignee').value.trim();
      const status = document.getElementById('td-f-status').value;
      const preset = document.getElementById('td-f-preset').value.trim();
      const keyword = document.getElementById('td-f-keyword').value.trim();
      if (meeting) params.set('meetingId', meeting);
      if (assignee) params.set('assigneeId', assignee);
      if (status) params.set('status', status);
      if (preset) params.set('presetTypeCode', preset);
      if (keyword) params.set('keyword', keyword);
      const data = await AdminApi.fetch('/api/v1/admin/todos?' + params.toString());
      const rows = data.records || [];
      totalPages = data.pages || 1;
      document.getElementById('td-page-info').textContent = `第 ${page} / ${totalPages} 页，共 ${data.total || 0} 条`;
      document.getElementById('td-body').innerHTML = rows.length ? rows.map(t => `
        <tr>
          <td title="${esc(t.id)}">${esc(String(t.id || '').slice(0, 8))}</td>
          <td title="${esc(t.meetingId)}">${esc(String(t.meetingId || '').slice(0, 8))}</td>
          <td>${esc(t.content || '')}</td>
          <td>${esc(t.assigneeName || t.assigneeId || '')}</td>
          <td>${esc(t.operatorName || t.operatorId || '')}</td>
          <td><span class="tag">${esc(t.status || '')}</span></td>
          <td>${esc(t.priority || '')}</td>
          <td>${fmtDt(t.deadline)}</td>
          <td>${t.remindCount || 0}</td>
          <td>${fmtDt(t.createdAt)}</td>
          <td>
            <button type="button" class="secondary td-detail" data-id="${esc(t.id)}">详情</button>
            <button type="button" class="secondary td-edit" data-id="${esc(t.id)}">编辑</button>
            <button type="button" class="secondary td-force" data-id="${esc(t.id)}">强制改状态</button>
            <button type="button" class="danger td-delete" data-id="${esc(t.id)}">删除</button>
          </td>
        </tr>
      `).join('') : '<tr><td colspan="11" class="form-hint">暂无待办</td></tr>';
      bindRowEvents();
    };

    const bindRowEvents = () => {
      document.querySelectorAll('button.td-edit').forEach(btn => {
        btn.onclick = async () => {
          try {
            const t = await AdminApi.fetch('/api/v1/admin/todos/' + encodeURIComponent(btn.dataset.id));
            openEditor(t);
          } catch (err) {
            showMsg('加载待办失败: ' + err.message, true);
          }
        };
      });
      document.querySelectorAll('button.td-detail').forEach(btn => {
        btn.onclick = async () => {
          try {
            const id = btn.dataset.id;
            const audit = await AdminApi.fetch('/api/v1/admin/todos/' + encodeURIComponent(id) + '/audit');
            const progress = await AdminApi.fetch('/api/v1/admin/todos/' + encodeURIComponent(id) + '/progress');
            const attachments = await AdminApi.fetch('/api/v1/admin/todos/' + encodeURIComponent(id) + '/attachments');
            const auditHtml = (audit && audit.length) ? audit.map(a =>
              `<div>${fmtDt(a.createdAt)} ${esc(a.action)} ${esc(a.oldStatus)}→${esc(a.newStatus)} 操作者:${esc(a.operatorId||a.operatorName)} 原因:${esc(a.reason)}</div>`
            ).join('') : '<p>无审计记录</p>';
            const progHtml = (progress && progress.length) ? progress.map(p =>
              `<div>${fmtDt(p.createdAt)} [${esc(p.authorRole)}] ${esc(p.authorName)}: ${esc(p.progressText)} ${p.progressPercent!=null?p.progressPercent+'%':''}</div>`
            ).join('') : '<p>无进度记录</p>';
            const attHtml = (attachments && attachments.length) ? attachments.map(a =>
              `<div>${esc(a.fileName)} (${a.fileSize||0} bytes) · ${fmtDt(a.createdAt)}</div>`
            ).join('') : '<p>无附件</p>';
            document.getElementById('td-detail-body').innerHTML =
              `<h4>审计日志</h4>${auditHtml}<h4>进度</h4>${progHtml}<h4>附件</h4>${attHtml}`;
            document.getElementById('td-detail').classList.remove('hidden');
          } catch (err) {
            showMsg('加载详情失败: ' + err.message, true);
          }
        };
      });
      document.querySelectorAll('button.td-force').forEach(btn => {
        btn.onclick = async () => {
          const status = prompt('目标状态（PENDING/IN_PROGRESS/COMPLETED/BLOCKED/DELAYED）', 'COMPLETED');
          if (!status) return;
          const reason = prompt('强制改状态原因（必填）', '');
          if (!reason || !reason.trim()) {
            showMsg('必须填写原因', true);
            return;
          }
          try {
            await AdminApi.fetch('/api/v1/admin/todos/' + encodeURIComponent(btn.dataset.id) + '/status', {
              method: 'PATCH',
              body: JSON.stringify({ status: status.trim(), reason: reason.trim() })
            });
            showMsg('状态已强制更新', false);
            await loadStats();
            await loadList();
          } catch (err) {
            showMsg('操作失败: ' + err.message, true);
          }
        };
      });
      document.querySelectorAll('button.td-delete').forEach(btn => {
        btn.onclick = async () => {
          if (!window.confirm('确认删除此待办？此操作不可恢复。')) return;
          const reason = prompt('删除原因（必填）', '');
          if (!reason || !reason.trim()) {
            showMsg('必须填写删除原因', true);
            return;
          }
          try {
            await AdminApi.fetch('/api/v1/admin/todos/' + encodeURIComponent(btn.dataset.id), {
              method: 'DELETE',
              body: JSON.stringify({ reason: reason.trim() })
            });
            showMsg('已删除', false);
            await loadStats();
            await loadList();
          } catch (err) {
            showMsg('删除失败: ' + err.message, true);
          }
        };
      });
    };

    const openEditor = (todo) => {
      const isEdit = !!todo;
      document.getElementById('td-form-id').value = isEdit ? (todo.id || '') : '';
      document.getElementById('td-f-meeting-id').value = isEdit ? (todo.meetingId || '') : '';
      document.getElementById('td-f-preset-code').value = isEdit ? (todo.presetTypeCode || '') : '';
      document.getElementById('td-f-content').value = isEdit ? (todo.content || '') : '';
      document.getElementById('td-f-assignee-id').value = isEdit ? (todo.assigneeId || '') : '';
      document.getElementById('td-f-assignee-name').value = isEdit ? (todo.assigneeName || '') : '';
      document.getElementById('td-f-operator-id').value = isEdit ? (todo.operatorId || '') : '';
      document.getElementById('td-f-operator-name').value = isEdit ? (todo.operatorName || '') : '';
      document.getElementById('td-f-status-edit').value = isEdit ? (todo.status || 'PENDING') : 'PENDING';
      document.getElementById('td-f-priority').value = isEdit ? (todo.priority || 'MEDIUM') : 'MEDIUM';
      if (isEdit && todo.deadline) {
        document.getElementById('td-f-deadline').value = String(todo.deadline).slice(0, 16);
      } else {
        document.getElementById('td-f-deadline').value = '';
      }
      document.getElementById('td-f-completion-note').value = isEdit ? (todo.completionNote || '') : '';
      document.getElementById('td-f-block-reason').value = isEdit ? (todo.blockReason || '') : '';
      document.getElementById('td-save-msg').classList.add('hidden');
      document.getElementById('td-editor-title').textContent = isEdit ? '编辑待办' : '新增待办';
      AdminUi.openEditor(document.getElementById('td-editor'));
    };

    document.getElementById('td-detail-close').onclick = () => document.getElementById('td-detail').classList.add('hidden');
    document.getElementById('td-new').onclick = () => openEditor(null);
    document.getElementById('td-reload').onclick = async () => { await loadStats(); await loadList(); };
    document.getElementById('td-search').onclick = () => { page = 1; loadList(); };
    document.getElementById('td-prev').onclick = () => { if (page > 1) { page--; loadList(); } };
    document.getElementById('td-next').onclick = () => { if (page < totalPages) { page++; loadList(); } };
    document.getElementById('td-cancel').onclick = () => AdminUi.closeEditor(document.getElementById('td-editor'));
    document.getElementById('td-save').onclick = async () => {
      try {
        const body = {
          id: document.getElementById('td-form-id').value.trim() || null,
          meetingId: document.getElementById('td-f-meeting-id').value.trim(),
          presetTypeCode: document.getElementById('td-f-preset-code').value.trim() || null,
          content: document.getElementById('td-f-content').value.trim(),
          assigneeId: document.getElementById('td-f-assignee-id').value.trim(),
          assigneeName: document.getElementById('td-f-assignee-name').value.trim(),
          operatorId: document.getElementById('td-f-operator-id').value.trim(),
          operatorName: document.getElementById('td-f-operator-name').value.trim(),
          status: document.getElementById('td-f-status-edit').value,
          priority: document.getElementById('td-f-priority').value,
          deadline: document.getElementById('td-f-deadline').value || null,
          completionNote: document.getElementById('td-f-completion-note').value.trim(),
          blockReason: document.getElementById('td-f-block-reason').value.trim()
        };
        if (!body.meetingId) return showSaveMsg('meetingId 必填', true);
        if (!body.content) return showSaveMsg('content 必填', true);
        await AdminApi.fetch('/api/v1/admin/todos', { method: 'POST', body: JSON.stringify(body) });
        AdminUi.closeEditor(document.getElementById('td-editor'));
        showMsg(body.id ? '待办已更新' : '待办已创建', false);
        await loadStats();
        await loadList();
      } catch (err) {
        showSaveMsg('保存失败: ' + err.message, true);
      }
    };

    await loadStats();
    await loadList();
  }
});
