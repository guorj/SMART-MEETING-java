AdminModules.register({
  route: '/push-bot',
  mount: async function (root) {
    const params = new URLSearchParams(location.hash.split('?')[1] || '');
    let tab = params.get('tab') || 'tasks';
    let logMeetingId = params.get('meetingId') || '';

    root.innerHTML = `
      <div class="panel">
        <p class="module-intro">${AdminHints.pushBot.moduleIntro}</p>
        <div class="toolbar">
          <button type="button" class="primary" id="pb-tab-tasks">推送任务</button>
          <button type="button" id="pb-tab-logs">推送日志</button>
          <button type="button" id="pb-reload">同步 Quartz</button>
          <span class="toolbar-note" title="${AdminHints.pushBot.syncQuartz.replace(/"/g, '&quot;')}">ⓘ 同步 Quartz：重载 INTERNAL 任务 Cron</span>
          <span id="pb-msg" class="msg hidden"></span>
        </div>
      </div>
      <div id="pb-tasks-panel"></div>
      <div id="pb-logs-panel" class="hidden"></div>`;

    const showMsg = (text, isErr) => {
      const el = document.getElementById('pb-msg');
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };

    const formatPushError = (r) => {
      if (!r) return '未知错误';
      if (r.errorMessage) {
        const m = r.errorMessage;
        const codeMatch = m.match(/"code":(\d+)/);
        const msgMatch = m.match(/"msg":"((?:\\.|[^"\\])*)"/);
        if (codeMatch && msgMatch) {
          return '飞书 ' + codeMatch[1] + ': ' + msgMatch[1].replace(/\\"/g, '"');
        }
        return m.length > 240 ? m.slice(0, 240) + '…' : m;
      }
      const failed = (r.results || []).find(x => x.status === 'FAILED');
      if (failed && failed.errorMessage) return formatPushError(failed);
      return r.status || '未知错误';
    };

    const logDetail = (l) => {
      if (l.status === 'FAILED' && l.errorMessage) return l.errorMessage;
      if (l.status === 'SKIPPED' && l.skipReason) return l.skipReason;
      return '-';
    };

    const setTab = (name) => {
      tab = name;
      document.getElementById('pb-tasks-panel').classList.toggle('hidden', name !== 'tasks');
      document.getElementById('pb-logs-panel').classList.toggle('hidden', name !== 'logs');
      document.getElementById('pb-tab-tasks').classList.toggle('primary', name === 'tasks');
      document.getElementById('pb-tab-logs').classList.toggle('primary', name === 'logs');
      if (name === 'tasks') loadTasks();
      else loadLogs();
    };

    document.getElementById('pb-tab-tasks').onclick = () => setTab('tasks');
    document.getElementById('pb-tab-logs').onclick = () => setTab('logs');
    document.getElementById('pb-reload').onclick = async () => {
      try {
        await AdminApi.fetch('/api/v1/admin/bot/reload-schedule', { method: 'POST' });
        showMsg('Quartz 已同步', false);
      } catch (e) {
        showMsg('同步失败: ' + e.message, true);
      }
    };

    async function loadTasks() {
      const panel = document.getElementById('pb-tasks-panel');
      panel.innerHTML = '<p class="muted">加载中…</p>';
      try {
        const page = await AdminApi.fetch('/api/v1/admin/push-tasks?page=0&size=50');
        const tasks = page.content || [];
        panel.innerHTML = `
          <div class="panel"><button class="primary" id="pb-new">新建任务</button></div>
          <div class="panel table-wrap"><table><thead><tr>
            <th title="任务显示名">名称</th><th title="关闭后不参与 Cron">启用</th><th title="Quartz Cron，INTERNAL 自动触发">Cron</th>
            <th title="INTERNAL=服务端定时；EXTERNAL=仅外部/手动触发">模式</th><th title="USER/GROUP + open_id 或 chat_id">目标</th><th>操作</th>
          </tr></thead><tbody id="pb-task-tbody"></tbody></table></div>
          <div class="panel hidden form-editor" id="pb-editor">
            <h3 id="pb-editor-title">编辑任务</h3>
            <input type="hidden" id="pb-id"/>
            ${AdminForm.field('任务名', '<input id="pb-name" type="text"/>', AdminHints.pushBot.taskName)}
            <div class="form-field">
              <label>Cron</label>
              <div style="display:flex;gap:0.5rem;align-items:center;flex-wrap:wrap">
                <input id="pb-cron" type="text" placeholder="0 30 9 * * MON-FRI" style="flex:1;min-width:12rem"/>
                <button type="button" id="pb-cron-preview">预览</button>
              </div>
              ${AdminForm.hint(AdminHints.pushBot.cron)}
              <p id="pb-cron-hint" class="form-hint muted"></p>
            </div>
            <div class="form-field">
              <label>调度模式</label>
              <select id="pb-mode"><option value="INTERNAL">INTERNAL — 服务端 Cron 自动推送</option><option value="EXTERNAL">EXTERNAL — 仅外部/手动触发</option></select>
              <p id="pb-mode-hint" class="form-hint"></p>
            </div>
            <div class="form-field">
              <label>目标类型</label>
              <select id="pb-ttype"><option value="USER">USER — 单聊用户</option><option value="GROUP">GROUP — 群聊</option></select>
              <p id="pb-ttype-hint" class="form-hint"></p>
            </div>
            ${AdminForm.field('目标 ID', '<input id="pb-tid" type="text"/>', AdminHints.pushBot.targetId)}
            ${AdminForm.field('消息', '<textarea id="pb-msg-text" rows="4"></textarea>', AdminHints.pushBot.message)}
            <div class="form-field">
              <span class="form-field-caption">选项</span>
              <div class="form-check-row">
                <label class="check-label"><input type="checkbox" id="pb-skip-hol"/> 跳过假日</label>
                <label class="check-label"><input type="checkbox" id="pb-enabled" checked/> 启用</label>
              </div>
              ${AdminForm.hint(AdminHints.pushBot.skipHolidays + ' ' + AdminHints.pushBot.enabled)}
            </div>
            <p id="pb-save-msg" class="msg hidden"></p>
            <button class="primary" id="pb-save">保存</button>
            <button id="pb-cancel">取消</button>
          </div>`;

        const tbody = document.getElementById('pb-task-tbody');
        tasks.forEach(t => {
          const tr = document.createElement('tr');
          const enabled = t.enabled !== false;
          tr.innerHTML = '<td>' + (t.taskName || '') + '</td><td>' + (enabled ? '是' : '否') + '</td><td>' + (t.cronExpression || '-') + '</td>'
            + '<td>' + (t.scheduleMode || '') + '</td><td>' + (t.targetType || '') + ' ' + (t.targetId || '') + '</td>'
            + '<td class="actions"><span class="btn-group">'
            + '<button type="button" class="secondary pb-edit" data-id="' + t.id + '">编辑</button> '
            + '<button type="button" class="secondary pb-exec" data-id="' + t.id + '">执行</button> '
            + '<button type="button" class="secondary pb-toggle" data-id="' + t.id + '" data-enabled="' + enabled + '">'
            + (enabled ? '停用' : '启用') + '</button> '
            + '<button type="button" class="danger pb-del" data-id="' + t.id + '">删除</button></span></td>';
          tbody.appendChild(tr);
        });

        const showEditor = (task) => {
          document.getElementById('pb-editor').classList.remove('hidden');
          document.getElementById('pb-editor-title').textContent = task ? '编辑任务' : '新建任务';
          document.getElementById('pb-save-msg').classList.add('hidden');
          document.getElementById('pb-id').value = task ? task.id : '';
          document.getElementById('pb-name').value = task ? task.taskName : '';
          document.getElementById('pb-cron').value = task ? (task.cronExpression || '') : '';
          document.getElementById('pb-mode').value = task ? (task.scheduleMode || 'INTERNAL') : 'INTERNAL';
          document.getElementById('pb-ttype').value = task ? (task.targetType || 'USER') : 'USER';
          document.getElementById('pb-tid').value = task ? (task.targetId || '') : '';
          document.getElementById('pb-msg-text').value = task ? (task.messageContent || '') : '';
          document.getElementById('pb-skip-hol').checked = task ? !!task.skipHolidays : false;
          document.getElementById('pb-enabled').checked = task ? task.enabled !== false : true;
          AdminForm.bindSelectHint('pb-mode', 'pb-mode-hint', AdminHints.pushBot.scheduleMode);
          AdminForm.bindSelectHint('pb-ttype', 'pb-ttype-hint', AdminHints.pushBot.targetType);
        };

        tbody.querySelectorAll('.pb-edit').forEach(btn => {
          btn.onclick = async e => {
            e.preventDefault();
            const t = await AdminApi.fetch('/api/v1/admin/push-tasks/' + btn.dataset.id);
            showEditor(t);
          };
        });
        tbody.querySelectorAll('.pb-exec').forEach(btn => {
          btn.onclick = async e => {
            e.preventDefault();
            if (btn.disabled) return;
            if (!confirm('立即执行推送任务？')) return;
            btn.disabled = true;
            const oldText = btn.textContent;
            btn.textContent = '执行中…';
            try {
              const r = await AdminApi.fetch('/api/v1/admin/push-tasks/' + btn.dataset.id + '/execute', { method: 'POST' });
              const st = r.status || '';
              if (st === 'SUCCESS') {
                showMsg('推送成功', false);
              } else if (st === 'SKIPPED') {
                const reason = r.skipReason || '';
                if (reason === 'DUPLICATE') {
                  showMsg('去重跳过（DUPLICATE）：当前 feishu-scheduled-bot 仍是旧版本，/execute 有当日去重。请重新编译并重启 bot（mvn package + 重启进程）后再试。', true);
                } else {
                  showMsg('已跳过: ' + reason, false);
                }
              } else if (st === 'REJECTED') {
                showMsg('未执行: ' + (r.errorMessage || st), true);
              } else if (st === 'FAILED') {
                showMsg('推送失败: ' + formatPushError(r), true);
              } else if (st === 'PARTIAL_SUCCESS') {
                showMsg('部分失败: ' + formatPushError(r), true);
              } else {
                showMsg('结果: ' + (r.errorMessage || r.skipReason || st || JSON.stringify(r)), st === 'SUCCESS');
              }
            } catch (err) {
              showMsg('执行失败: ' + err.message, true);
            } finally {
              btn.disabled = false;
              btn.textContent = oldText;
            }
          };
        });
        tbody.querySelectorAll('.pb-toggle').forEach(btn => {
          btn.onclick = async e => {
            e.preventDefault();
            if (btn.disabled) return;
            btn.disabled = true;
            const oldText = btn.textContent;
            btn.textContent = '…';
            try {
              await AdminApi.fetch('/api/v1/admin/push-tasks/' + btn.dataset.id + '/toggle', { method: 'PATCH' });
              showMsg('已' + (btn.dataset.enabled === 'true' ? '停用' : '启用') + '任务', false);
              await loadTasks();
            } catch (err) {
              showMsg('启停失败: ' + err.message, true);
              btn.disabled = false;
              btn.textContent = oldText;
            }
          };
        });
        tbody.querySelectorAll('.pb-del').forEach(btn => {
          btn.onclick = async e => {
            e.preventDefault();
            const id = btn.dataset.id;
            const taskName = btn.closest('tr')?.querySelector('td')?.textContent?.trim() || id;
            if (!confirm('确定删除任务「' + taskName + '」？此操作不可恢复。')) return;
            btn.disabled = true;
            try {
              await AdminApi.fetch('/api/v1/admin/push-tasks/' + id, { method: 'DELETE' });
              showMsg('已删除任务', false);
              const editorId = document.getElementById('pb-id');
              if (editorId && editorId.value === id) {
                document.getElementById('pb-editor').classList.add('hidden');
              }
              await loadTasks();
            } catch (err) {
              showMsg('删除失败: ' + err.message, true);
              btn.disabled = false;
            }
          };
        });
        document.getElementById('pb-new').onclick = () => showEditor(null);
        document.getElementById('pb-cancel').onclick = () => document.getElementById('pb-editor').classList.add('hidden');
        document.getElementById('pb-cron-preview').onclick = async () => {
          const cron = document.getElementById('pb-cron').value.trim();
          if (!cron) return;
          try {
            const r = await AdminApi.fetch('/api/v1/admin/push-tasks/cron-preview', {
              method: 'POST',
              body: JSON.stringify({ cronExpression: cron })
            });
            document.getElementById('pb-cron-hint').textContent = (r.nextFireTimes || []).join(' · ');
          } catch (e) {
            document.getElementById('pb-cron-hint').textContent = e.message;
          }
        };
        document.getElementById('pb-save').onclick = async () => {
          const id = document.getElementById('pb-id').value;
          const body = {
            taskName: document.getElementById('pb-name').value.trim(),
            cronExpression: document.getElementById('pb-cron').value.trim(),
            scheduleMode: document.getElementById('pb-mode').value,
            targetType: document.getElementById('pb-ttype').value,
            targetId: document.getElementById('pb-tid').value.trim(),
            messageContent: document.getElementById('pb-msg-text').value,
            skipHolidays: document.getElementById('pb-skip-hol').checked,
            enabled: document.getElementById('pb-enabled').checked
          };
          if (!body.taskName || !body.messageContent) {
            document.getElementById('pb-save-msg').className = 'msg msg-err';
            document.getElementById('pb-save-msg').textContent = '请填写任务名与消息';
            document.getElementById('pb-save-msg').classList.remove('hidden');
            return;
          }
          try {
            if (id) {
              await AdminApi.fetch('/api/v1/admin/push-tasks/' + id, { method: 'PUT', body: JSON.stringify(body) });
            } else {
              await AdminApi.fetch('/api/v1/admin/push-tasks', { method: 'POST', body: JSON.stringify(body) });
            }
            document.getElementById('pb-editor').classList.add('hidden');
            loadTasks();
          } catch (e) {
            document.getElementById('pb-save-msg').className = 'msg msg-err';
            document.getElementById('pb-save-msg').textContent = e.message;
            document.getElementById('pb-save-msg').classList.remove('hidden');
          }
        };
      } catch (e) {
        panel.innerHTML = '<p class="msg msg-err">无法加载任务（请确认 bot 已启动且配置了 API Key）：' + e.message + '</p>';
      }
    }

    async function loadLogs() {
      const panel = document.getElementById('pb-logs-panel');
      panel.innerHTML = `
        <div class="panel">
          <p class="module-intro">查询历史推送记录，可按会议、任务、状态筛选。</p>
          <div class="toolbar filter-bar">
            <label class="field-inline">会议 ID
              <input id="pb-log-mid" type="text" value="${logMeetingId.replace(/"/g, '&quot;')}" title="${AdminHints.pushBot.logMeetingId.replace(/"/g, '&quot;')}"/>
            </label>
            <label class="field-inline">任务 ID
              <input id="pb-log-task" type="text" title="${AdminHints.pushBot.logTaskId.replace(/"/g, '&quot;')}"/>
            </label>
            <label class="field-inline">状态
              <select id="pb-log-status"><option value="">全部</option><option>SUCCESS</option><option>FAILED</option><option>SKIPPED</option></select>
            </label>
            <button class="primary" id="pb-log-search">查询</button>
          </div>
        </div>
        <div class="panel table-wrap"><table><thead><tr>
          <th>时间</th><th>任务</th><th>会议</th><th>状态</th><th title="CRON / MANUAL / MEETING 等">触发</th><th>目标</th><th>原因/错误</th>
        </tr></thead><tbody id="pb-log-tbody"></tbody></table></div>`;
      document.getElementById('pb-log-search').onclick = async () => {
        const mid = document.getElementById('pb-log-mid').value.trim();
        const tid = document.getElementById('pb-log-task').value.trim();
        const st = document.getElementById('pb-log-status').value;
        let url = '/api/v1/admin/push-logs?page=0&size=50';
        if (mid) url += '&meetingId=' + encodeURIComponent(mid);
        if (tid) url += '&taskId=' + encodeURIComponent(tid);
        if (st) url += '&status=' + encodeURIComponent(st);
        try {
          const page = await AdminApi.fetch(url);
          const tbody = document.getElementById('pb-log-tbody');
          tbody.innerHTML = '';
          (page.content || []).forEach(l => {
            const tr = document.createElement('tr');
            const detail = logDetail(l);
            const detailCell = detail.length > 80
              ? '<span title="' + detail.replace(/"/g, '&quot;') + '">' + detail.slice(0, 80) + '…</span>'
              : detail;
            tr.innerHTML = '<td>' + (l.sendTime || '') + '</td><td>' + (l.taskName || l.taskId) + '</td>'
              + '<td>' + (l.meetingId || '-') + '</td><td>' + l.status + '</td><td>' + (l.triggerType || '') + '</td>'
              + '<td>' + (l.targetType || '') + ' ' + (l.targetId || '') + '</td>'
              + '<td class="log-detail">' + detailCell + '</td>';
            tbody.appendChild(tr);
          });
        } catch (e) {
          alert('查询失败: ' + e.message);
        }
      };
      document.getElementById('pb-log-search').click();
    }

    setTab(tab);
  }
});
