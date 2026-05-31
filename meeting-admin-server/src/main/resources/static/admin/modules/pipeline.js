AdminModules.register({
  route: '/pipeline',
  mount: async function (root) {
    const esc = s => (s == null ? '' : String(s)).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
    let templates = [];
    let executions = [];
    const stepsByTemplateId = {};
    let formMode = 'template';
    let formTemplateId = null;
    let formStepId = null;
    let activeTemplateId = null;
    const STAGES = ['PRE', 'MID', 'POST'];
    const stageTitle = { PRE: '会前 PRE', MID: '会中 MID', POST: '会后 POST' };
    const STEP_TYPES = [
      'preset-sync', 'settings-reload', 'weekly-job', 'push-notification',
      'pre-confirm-card', 'pre-inventory-card', 'pre-push-doc-link', 'pre-voiceprint-check', 'pre-agenda-notify',
      'pre-agenda-owner-confirm-notify',
      'pre-agenda-leader-notify',
      'pre-agenda-fill-init', 'pre-agenda-fill-notify',
      'post-todo-remind', 'post-auto-delayed', 'post-auto-next-meeting',
      'pre-confirm-persist', 'pre-key-decliner-alert', 'pre-agenda-confirm', 'pre-calendar-create',
      'mid-topic-timeout', 'post-feishu-task-sync', 'post-todo-action', 'mid-prev-progress-tts', 'post-agenda-carry',
      'post-voiceprint-identify', 'post-offline-llm-correction'
    ];

    root.innerHTML = `
      <div class="panel">
        <div class="panel-head">
          <h2>流水线编排</h2>
          <p>按 PRE/MID/POST 分阶段配置模板与步骤，执行时按步骤顺序串行运行。</p>
        </div>
        <div class="toolbar" style="margin-bottom:0.75rem;">
          <button class="primary" id="pl-new-template">新增模板</button>
          <button class="secondary" id="pl-reload">刷新</button>
          <span id="pl-msg" class="msg hidden"></span>
        </div>
        <div class="form-hint">
          使用顺序：1）先建模板（选阶段）→ 2）给模板加步骤（设置顺序）→ 3）优先按 presetTypeCode 触发执行（可选 meetingId 单场触发）。
        </div>
      </div>

      <div class="panel pipeline-guide">
        <h3>使用说明（页面内）</h3>
        <div class="pipeline-guide-grid">
          <section>
            <h4>3 步跑通</h4>
            <ol>
              <li>新增模板：选阶段（PRE/MID/POST），定义模板 code。</li>
              <li>给模板新增步骤：支持拖拽排序 / 上下移动，形成串行链路。</li>
              <li>触发执行：优先填 <code>presetTypeCode</code> 批量触发，也可填 <code>meetingId</code> 单场触发。</li>
            </ol>
          </section>
          <section>
            <h4>阶段含义</h4>
            <ul>
              <li><strong>PRE</strong>：会前准备（同步资料、校验配置）。</li>
              <li><strong>MID</strong>：会中动作（状态推送、过程处理）。</li>
              <li><strong>POST</strong>：会后收尾（纪要、通知、归档）。</li>
            </ul>
          </section>
          <section>
            <h4>步骤类型示例</h4>
            <ul>
              <li><code>preset-sync</code>：同步会务预设缓存。</li>
              <li><code>pre-*</code>：会前确认、盘点、资料推送。</li>
              <li><code>mid-*</code>：会中策略注入（议题超时、进度通报）。</li>
              <li><code>post-*</code>：会后待办/闭环/校正流程。</li>
            </ul>
          </section>
        </div>
      </div>

      <div class="panel">
        <h3>阶段流程视图</h3>
        <div id="pl-board" class="pipeline-board"></div>
      </div>

      <div class="panel">
        <h3>触发执行</h3>
        <div class="toolbar">
          <input id="pl-preset-type-code" placeholder="presetTypeCode（推荐）" />
          <input id="pl-meeting-id" placeholder="meetingId（可选）" />
          <select id="pl-stage">
            <option value="PRE">PRE</option><option value="MID">MID</option><option value="POST">POST</option>
          </select>
          <input id="pl-template-code" placeholder="templateCode(可选，不填则按 stage 跑默认模板)" />
          <label class="check-label"><input id="pl-skip-existing" type="checkbox" checked/> 跳过已有执行</label>
          <button id="pl-exec" class="primary">执行</button>
        </div>
      </div>
      <div class="panel table-wrap">
        <h3>最近执行记录</h3>
        <table><thead><tr>
          <th>ID</th><th>meetingId</th><th>stage</th><th>status</th><th>retry</th><th>lastError</th>
        </tr></thead><tbody id="pl-exec-body"></tbody></table>
      </div>
      <div class="panel"><pre id="pl-result" class="msg"></pre></div>
      <div class="panel hidden" id="pl-step-editor">
        <h3 id="pl-step-editor-title">步骤编排</h3>
        <p class="form-hint">支持拖拽排序、上下移动、编辑步骤与启停步骤；排序变更会自动回写 orderNo。</p>
        <div id="pl-step-editor-body" class="pipeline-step-editor-list"></div>
      </div>

      <div class="panel hidden form-editor" id="pl-editor">
        <h3 id="pl-editor-title">流水线编排</h3>
        <p class="form-hint">建议命名：模板 code = 阶段_业务（如 pre_agenda_sync），步骤 code = 模板前缀_动作（如 pre_sync_cache）。</p>
        <input type="hidden" id="pl-form-mode"/>
        <input type="hidden" id="pl-form-template-id"/>
        <input type="hidden" id="pl-form-step-id"/>
        <div id="pl-template-fields">
          ${AdminForm.field('templateCode', '<input id="pl-f-template-code" type="text" placeholder="示例：pre_agenda_sync"/>', '模板编码，建议英文下划线，保持唯一。')}
          ${AdminForm.field('templateName', '<input id="pl-f-template-name" type="text" placeholder="示例：会前议程同步"/>', '模板显示名，用于运营识别。')}
          <div class="form-field">
            <label>stage</label>
            <select id="pl-f-template-stage">
              <option value="PRE">PRE</option>
              <option value="MID">MID</option>
              <option value="POST">POST</option>
            </select>
            ${AdminForm.hint('PRE=会前，MID=会中，POST=会后。')}
          </div>
          ${AdminForm.field('description', '<textarea id="pl-f-template-desc" rows="3" placeholder="模板说明（可选）"></textarea>')}
          <div class="form-check-row">
            <label class="check-label"><input id="pl-f-template-enabled" type="checkbox" checked/> 启用模板</label>
          </div>
        </div>
        <div id="pl-step-fields" class="hidden">
          ${AdminForm.field('stepCode', '<input id="pl-f-step-code" type="text" placeholder="示例：pre_sync_cache"/>', '步骤唯一编码（同模板内不可重复），建议使用 模板前缀_动作。')}
          ${AdminForm.field('stepName', '<input id="pl-f-step-name" type="text" placeholder="示例：同步预设缓存"/>', '步骤显示名，建议写成可运维理解的动宾短语。')}
          ${AdminForm.field('stepType', `<select id="pl-f-step-type">${
            STEP_TYPES.map(t => `<option value="${t}">${t}</option>`).join('')
          }</select>`, '步骤类型：支持 pre/mid/post 全量执行器。建议按阶段选择前缀一致的 stepType。')}
          <div class="form-field">
            <label>stage</label>
            <select id="pl-f-step-stage">
              <option value="PRE">PRE</option>
              <option value="MID">MID</option>
              <option value="POST">POST</option>
            </select>
            ${AdminForm.hint('步骤所属阶段。通常应与模板阶段一致，跨阶段步骤仅用于特殊串联场景。')}
          </div>
          ${AdminForm.field('orderNo', '<input id="pl-f-step-order" type="number" value="1"/>', '执行顺序，数字越小越先执行；建议连续编号。拖拽/上下移动会自动重排。')}
          ${AdminForm.field('timeoutSeconds', '<input id="pl-f-step-timeout" type="number" value="120"/>', '单步骤超时秒数，超时后记录失败并进入重试/失败分支。')}
          ${AdminForm.field('configJson', '<textarea id="pl-f-step-config" rows="4">{}</textarea>', '步骤配置 JSON。支持 condition 分支，例如 {"condition":{"key":"meeting.status","equals":"INVITED"}}。')}
          <div class="form-check-row">
            <label class="check-label"><input id="pl-f-step-enabled" type="checkbox" checked/> 启用步骤</label>
          </div>
        </div>
        <p id="pl-save-msg" class="msg hidden"></p>
        <div class="toolbar" style="justify-content:flex-end;">
          <button type="button" class="primary" id="pl-save">保存</button>
          <button type="button" id="pl-cancel">取消</button>
        </div>
      </div>
    `;

    const showMsg = (text, isErr) => {
      const el = document.getElementById('pl-msg');
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };
    const showSaveMsg = (text, isErr) => {
      const el = document.getElementById('pl-save-msg');
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };

    const openTemplateEditor = (template) => {
      formMode = template && template.id ? 'template-edit' : 'template';
      formTemplateId = template && template.id ? Number(template.id) : null;
      formStepId = null;
      document.getElementById('pl-form-mode').value = formMode;
      document.getElementById('pl-form-template-id').value = formTemplateId ? String(formTemplateId) : '';
      document.getElementById('pl-form-step-id').value = '';
      document.getElementById('pl-editor-title').textContent = formMode === 'template-edit' ? '编辑模板' : '新增模板';
      document.getElementById('pl-template-fields').classList.remove('hidden');
      document.getElementById('pl-step-fields').classList.add('hidden');
      document.getElementById('pl-save-msg').classList.add('hidden');
      document.getElementById('pl-f-template-code').value = template ? (template.templateCode || '') : '';
      document.getElementById('pl-f-template-name').value = template ? (template.templateName || '') : '';
      document.getElementById('pl-f-template-stage').value = template ? (template.stage || 'PRE') : 'PRE';
      document.getElementById('pl-f-template-desc').value = template ? (template.description || '') : '';
      document.getElementById('pl-f-template-enabled').checked = template ? !!template.enabled : true;
      AdminUi.openEditor(document.getElementById('pl-editor'));
    };

    const openStepForm = (opts) => {
      const isEdit = !!(opts && opts.step && opts.step.id);
      formMode = isEdit ? 'step-edit' : 'step';
      formTemplateId = Number(opts.templateId);
      formStepId = isEdit ? Number(opts.step.id) : null;
      document.getElementById('pl-form-mode').value = formMode;
      document.getElementById('pl-form-template-id').value = String(formTemplateId);
      document.getElementById('pl-form-step-id').value = formStepId ? String(formStepId) : '';
      document.getElementById('pl-editor-title').textContent = isEdit ? '编辑步骤' : '新增步骤';
      document.getElementById('pl-template-fields').classList.add('hidden');
      document.getElementById('pl-step-fields').classList.remove('hidden');
      document.getElementById('pl-save-msg').classList.add('hidden');
      document.getElementById('pl-f-step-code').value = isEdit ? (opts.step.stepCode || '') : '';
      document.getElementById('pl-f-step-name').value = isEdit ? (opts.step.stepName || '') : '';
      document.getElementById('pl-f-step-type').value = isEdit ? (opts.step.stepType || 'preset-sync') : 'preset-sync';
      document.getElementById('pl-f-step-stage').value = isEdit ? (opts.step.stage || opts.stage || 'PRE') : (opts.stage || 'PRE');
      document.getElementById('pl-f-step-order').value = isEdit ? String(opts.step.orderNo || 1) : String(opts.orderNo || 1);
      document.getElementById('pl-f-step-timeout').value = isEdit ? String(opts.step.timeoutSeconds || 120) : '120';
      document.getElementById('pl-f-step-config').value = isEdit ? (opts.step.configJson || '{}') : '{}';
      document.getElementById('pl-f-step-enabled').checked = isEdit ? !!opts.step.enabled : true;
      AdminUi.openEditor(document.getElementById('pl-editor'));
    };

    const openStepEditor = (templateId, stage) => {
      const existing = (stepsByTemplateId[templateId] || []).slice();
      const nextOrder = existing.length ? Math.max(...existing.map(s => Number(s.orderNo || 0))) + 1 : 1;
      openStepForm({ templateId, stage, orderNo: nextOrder });
    };

    const persistStep = (step) => AdminApi.fetch('/api/v1/admin/pipeline/steps', {
      method: 'POST',
      body: JSON.stringify({
        id: step.id,
        templateId: step.templateId,
        stepCode: step.stepCode,
        stepName: step.stepName,
        stepType: step.stepType,
        stage: step.stage,
        orderNo: step.orderNo,
        timeoutSeconds: step.timeoutSeconds,
        configJson: step.configJson,
        enabled: !!step.enabled
      })
    });

    const persistTemplate = (template) => AdminApi.fetch('/api/v1/admin/pipeline/templates', {
      method: 'POST',
      body: JSON.stringify({
        id: template.id,
        templateCode: template.templateCode,
        templateName: template.templateName,
        stage: template.stage,
        enabled: !!template.enabled,
        versionNo: template.versionNo || 1,
        description: template.description || ''
      })
    });

    const saveStepOrder = async (templateId, orderedSteps) => {
      const tasks = [];
      orderedSteps.forEach((s, idx) => {
        const nextOrder = idx + 1;
        if (Number(s.orderNo) !== nextOrder) {
          const row = Object.assign({}, s, { orderNo: nextOrder });
          tasks.push(persistStep(row));
        }
      });
      if (tasks.length) {
        await Promise.all(tasks);
      }
    };

    const renderStepEditorPanel = (templateId) => {
      const panel = document.getElementById('pl-step-editor');
      const body = document.getElementById('pl-step-editor-body');
      const title = document.getElementById('pl-step-editor-title');
      const tpl = (templates || []).find(t => String(t.id) === String(templateId));
      const steps = (stepsByTemplateId[templateId] || []).slice().sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0));
      activeTemplateId = templateId;
      panel.classList.remove('hidden');
      title.textContent = '步骤编排 · ' + (tpl ? (tpl.templateName || tpl.templateCode) : ('模板#' + templateId));
      if (!steps.length) {
        body.innerHTML = '<div class="pipeline-empty">该模板暂无步骤，先点击“新增步骤”。</div>';
        return;
      }
      body.innerHTML = steps.map((s, idx) => `
        <article class="pipeline-step-editor-item" draggable="true" data-id="${s.id}" data-index="${idx}">
          <div class="pipeline-step-editor-main">
            <div><strong>${s.orderNo || 0}. ${esc(s.stepName || s.stepCode || '-')}</strong></div>
            <div class="form-hint">${esc(s.stepCode || '')} · ${esc(s.stepType || '')} · timeout=${s.timeoutSeconds || 120}s · ${s.enabled ? '启用' : '停用'}</div>
          </div>
          <div class="btn-group">
            <button type="button" class="secondary pl-step-edit" data-id="${s.id}">编辑</button>
            <button type="button" class="secondary pl-step-toggle" data-id="${s.id}">${s.enabled ? '停用' : '启用'}</button>
            <button type="button" class="secondary pl-step-up" data-id="${s.id}" ${idx === 0 ? 'disabled' : ''}>上移</button>
            <button type="button" class="secondary pl-step-down" data-id="${s.id}" ${idx === steps.length - 1 ? 'disabled' : ''}>下移</button>
            <button type="button" class="danger pl-step-delete" data-id="${s.id}">删除</button>
          </div>
        </article>
      `).join('');
      let draggingId = null;
      body.querySelectorAll('.pipeline-step-editor-item').forEach(item => {
        item.ondragstart = (e) => {
          draggingId = item.dataset.id;
          item.classList.add('dragging');
          e.dataTransfer.effectAllowed = 'move';
          e.dataTransfer.setData('text/plain', draggingId);
        };
        item.ondragend = () => {
          draggingId = null;
          item.classList.remove('dragging');
          body.querySelectorAll('.pipeline-step-editor-item').forEach(x => x.classList.remove('drag-over'));
        };
        item.ondragover = (e) => {
          e.preventDefault();
          if (!draggingId || draggingId === item.dataset.id) return;
          item.classList.add('drag-over');
        };
        item.ondragleave = () => item.classList.remove('drag-over');
        item.ondrop = async (e) => {
          e.preventDefault();
          item.classList.remove('drag-over');
          if (!draggingId || draggingId === item.dataset.id) return;
          try {
            const ordered = (stepsByTemplateId[templateId] || []).slice().sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0));
            const from = ordered.findIndex(x => String(x.id) === draggingId);
            const to = ordered.findIndex(x => String(x.id) === item.dataset.id);
            if (from < 0 || to < 0 || from === to) return;
            const [moved] = ordered.splice(from, 1);
            ordered.splice(to, 0, moved);
            await saveStepOrder(templateId, ordered);
            await reload();
            renderStepEditorPanel(templateId);
            showMsg('拖拽排序已保存', false);
          } catch (err) {
            showMsg('拖拽排序失败: ' + err.message, true);
          }
        };
      });
      body.querySelectorAll('button.pl-step-edit').forEach(btn => {
        btn.onclick = () => {
          const step = steps.find(x => String(x.id) === btn.dataset.id);
          if (!step) return;
          openStepForm({ templateId, stage: step.stage || 'PRE', step });
        };
      });
      body.querySelectorAll('button.pl-step-toggle').forEach(btn => {
        btn.onclick = async () => {
          try {
            const step = steps.find(x => String(x.id) === btn.dataset.id);
            if (!step) return;
            await persistStep(Object.assign({}, step, { enabled: !step.enabled }));
            await reload();
            renderStepEditorPanel(templateId);
            showMsg('步骤状态已更新', false);
          } catch (err) {
            showMsg('步骤状态更新失败: ' + err.message, true);
          }
        };
      });
      body.querySelectorAll('button.pl-step-up').forEach(btn => {
        btn.onclick = async () => {
          try {
            const cur = (stepsByTemplateId[templateId] || []).slice().sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0));
            const i = cur.findIndex(x => String(x.id) === btn.dataset.id);
            if (i <= 0) return;
            const [moved] = cur.splice(i, 1);
            cur.splice(i - 1, 0, moved);
            await saveStepOrder(templateId, cur);
            await reload();
            renderStepEditorPanel(templateId);
            showMsg('步骤顺序已更新', false);
          } catch (err) {
            showMsg('更新步骤顺序失败: ' + err.message, true);
          }
        };
      });
      body.querySelectorAll('button.pl-step-down').forEach(btn => {
        btn.onclick = async () => {
          try {
            const cur = (stepsByTemplateId[templateId] || []).slice().sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0));
            const i = cur.findIndex(x => String(x.id) === btn.dataset.id);
            if (i < 0 || i >= cur.length - 1) return;
            const [moved] = cur.splice(i, 1);
            cur.splice(i + 1, 0, moved);
            await saveStepOrder(templateId, cur);
            await reload();
            renderStepEditorPanel(templateId);
            showMsg('步骤顺序已更新', false);
          } catch (err) {
            showMsg('更新步骤顺序失败: ' + err.message, true);
          }
        };
      });
      body.querySelectorAll('button.pl-step-delete').forEach(btn => {
        btn.onclick = async () => {
          const step = steps.find(x => String(x.id) === btn.dataset.id);
          if (!step) return;
          const ok = window.confirm('确认删除步骤「' + (step.stepName || step.stepCode || step.id) + '」？此操作不可恢复。');
          if (!ok) return;
          try {
            await AdminApi.fetch('/api/v1/admin/pipeline/steps/' + encodeURIComponent(String(step.id)), {
              method: 'DELETE'
            });
            await reload();
            renderStepEditorPanel(templateId);
            showMsg('步骤已删除', false);
          } catch (err) {
            showMsg('删除步骤失败: ' + err.message, true);
          }
        };
      });
    };

    const renderTemplateBoard = async () => {
      const board = document.getElementById('pl-board');
      const byStage = { PRE: [], MID: [], POST: [] };
      (templates || []).forEach(t => {
        const st = STAGES.includes(t.stage) ? t.stage : 'PRE';
        byStage[st].push(t);
      });
      board.innerHTML = STAGES.map(stage => {
        const cards = (byStage[stage] || []).map(t => {
          const steps = (stepsByTemplateId[t.id] || []).slice().sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0));
          const stepFlow = steps.length
            ? `<div class="pipeline-step-flow">${
                steps.map(s => `<span class="pipeline-step-pill ${s.enabled ? '' : 'pipeline-step-pill-off'}" data-template-id="${t.id}" data-step-id="${s.id}" title="${esc(s.stepType || '')}">${s.orderNo || 0}. ${esc(s.stepName || s.stepCode || '')}</span>`).join('<span class="pipeline-arrow">→</span>')
              }</div>`
            : '<div class="form-hint">暂无步骤，建议先新增 1~3 个步骤形成闭环。</div>';
          return `<article class="pipeline-card ${t.enabled ? '' : 'pipeline-card-off'}">
            <div class="pipeline-card-head">
              <strong>${esc(t.templateName || t.templateCode || '-')}</strong>
              <span class="tag">${esc(t.templateCode || '')}</span>
            </div>
            <div class="form-hint">ID ${t.id} · ${t.enabled ? '启用' : '停用'} · ${esc(t.description || '')}</div>
            ${stepFlow}
            <div class="toolbar" style="margin-top:0.5rem;">
              <a href="#" class="pl-steps" data-id="${t.id}">查看步骤详情</a>
              <a href="#" class="pl-step-add" data-id="${t.id}" data-stage="${t.stage || stage}">新增步骤</a>
              <a href="#" class="pl-template-edit" data-id="${t.id}">编辑模板</a>
              <a href="#" class="pl-template-toggle" data-id="${t.id}">${t.enabled ? '停用模板' : '启用模板'}</a>
              <a href="#" class="pl-template-clone" data-id="${t.id}">复制模板</a>
              <a href="#" class="pl-use-template" data-code="${esc(t.templateCode || '')}" data-stage="${t.stage || stage}">用于执行</a>
              <a href="#" class="pl-template-delete danger" data-id="${t.id}">删除模板</a>
            </div>
          </article>`;
        }).join('');
        return `<section class="pipeline-stage-col">
          <h4>${stageTitle[stage]}</h4>
          ${cards || '<div class="pipeline-empty">暂无模板</div>'}
        </section>`;
      }).join('');

      board.querySelectorAll('a.pl-steps').forEach(a => {
        a.onclick = async (e) => {
          e.preventDefault();
          const templateId = a.dataset.id;
          try {
            const steps = (stepsByTemplateId[templateId] && stepsByTemplateId[templateId].length)
              ? stepsByTemplateId[templateId]
              : await AdminApi.fetch('/api/v1/admin/pipeline/steps?templateId=' + templateId);
            const text = (steps || [])
              .slice()
              .sort((a, b) => (a.orderNo || 0) - (b.orderNo || 0))
              .map(s => `#${s.orderNo} ${s.stepCode} (${s.stepType}) timeout=${s.timeoutSeconds || 120}s stage=${s.stage || '-'}\nconfig=${s.configJson || '{}'}\n`)
              .join('\n') || '(无步骤)';
            document.getElementById('pl-result').textContent = text;
            renderStepEditorPanel(templateId);
          } catch (err) {
            document.getElementById('pl-result').textContent = '加载步骤失败: ' + err.message;
          }
        };
      });
      board.querySelectorAll('a.pl-step-add').forEach(a => {
        a.onclick = (e) => {
          e.preventDefault();
          openStepEditor(a.dataset.id, a.dataset.stage || 'PRE');
        };
      });
      board.querySelectorAll('a.pl-template-edit').forEach(a => {
        a.onclick = (e) => {
          e.preventDefault();
          const tpl = (templates || []).find(t => String(t.id) === a.dataset.id);
          if (!tpl) return;
          openTemplateEditor(tpl);
        };
      });
      board.querySelectorAll('a.pl-template-toggle').forEach(a => {
        a.onclick = async (e) => {
          e.preventDefault();
          try {
            const tpl = (templates || []).find(t => String(t.id) === a.dataset.id);
            if (!tpl) return;
            await persistTemplate(Object.assign({}, tpl, { enabled: !tpl.enabled }));
            await reload();
            showMsg('模板状态已更新', false);
          } catch (err) {
            showMsg('模板状态更新失败: ' + err.message, true);
          }
        };
      });
      board.querySelectorAll('a.pl-template-clone').forEach(a => {
        a.onclick = async (e) => {
          e.preventDefault();
          try {
            const tpl = (templates || []).find(t => String(t.id) === a.dataset.id);
            if (!tpl) return;
            const clonedCode = (tpl.templateCode || 'template') + '_copy_' + String(Date.now()).slice(-4);
            const clonedName = (tpl.templateName || tpl.templateCode || '模板') + '（复制）';
            const created = await AdminApi.fetch('/api/v1/admin/pipeline/templates', {
              method: 'POST',
              body: JSON.stringify({
                templateCode: clonedCode,
                templateName: clonedName,
                stage: tpl.stage || 'PRE',
                enabled: false,
                versionNo: tpl.versionNo || 1,
                description: tpl.description || ''
              })
            });
            const newTemplateId = created && created.id;
            if (!newTemplateId) throw new Error('未获取到新模板ID');
            const srcSteps = (stepsByTemplateId[tpl.id] || []).slice().sort((x, y) => (x.orderNo || 0) - (y.orderNo || 0));
            for (const s of srcSteps) {
              await AdminApi.fetch('/api/v1/admin/pipeline/steps', {
                method: 'POST',
                body: JSON.stringify({
                  templateId: newTemplateId,
                  stepCode: (s.stepCode || 'step') + '_copy',
                  stepName: s.stepName || s.stepCode,
                  stepType: s.stepType,
                  stage: s.stage || tpl.stage || 'PRE',
                  orderNo: s.orderNo,
                  timeoutSeconds: s.timeoutSeconds,
                  configJson: s.configJson,
                  enabled: !!s.enabled
                })
              });
            }
            await reload();
            showMsg('模板已复制（默认停用）: ' + clonedCode, false);
          } catch (err) {
            showMsg('复制模板失败: ' + err.message, true);
          }
        };
      });
      board.querySelectorAll('a.pl-template-delete').forEach(a => {
        a.onclick = async (e) => {
          e.preventDefault();
          const tpl = (templates || []).find(t => String(t.id) === a.dataset.id);
          if (!tpl) return;
          const stepCount = (stepsByTemplateId[tpl.id] || []).length;
          const ok = window.confirm(
            '确认删除模板「' + (tpl.templateName || tpl.templateCode || tpl.id)
            + '」？\n将同时删除其 ' + stepCount + ' 个步骤，且不可恢复。'
          );
          if (!ok) return;
          try {
            await AdminApi.fetch('/api/v1/admin/pipeline/templates/' + encodeURIComponent(String(tpl.id)), {
              method: 'DELETE'
            });
            if (String(activeTemplateId || '') === String(tpl.id)) {
              activeTemplateId = null;
              document.getElementById('pl-step-editor').classList.add('hidden');
            }
            await reload();
            showMsg('模板已删除', false);
          } catch (err) {
            showMsg('删除模板失败: ' + err.message, true);
          }
        };
      });
      board.querySelectorAll('a.pl-use-template').forEach(a => {
        a.onclick = (e) => {
          e.preventDefault();
          document.getElementById('pl-stage').value = a.dataset.stage || 'PRE';
          document.getElementById('pl-template-code').value = a.dataset.code || '';
          showMsg('已填入执行参数，可直接输入 presetTypeCode 后执行', false);
        };
      });
      board.querySelectorAll('.pipeline-step-pill').forEach(el => {
        el.onclick = () => {
          const templateId = el.dataset.templateId;
          const stepId = el.dataset.stepId;
          const steps = (stepsByTemplateId[templateId] || []).slice();
          const step = steps.find(s => String(s.id) === String(stepId));
          if (!step) return;
          renderStepEditorPanel(templateId);
          openStepForm({ templateId, stage: step.stage || 'PRE', step });
        };
      });
    };

    const renderExecRows = () => {
      const body = document.getElementById('pl-exec-body');
      body.innerHTML = (executions || []).slice(0, 30).map(e => `
        <tr>
          <td>${e.id}</td><td>${esc(e.meetingId || '')}</td><td>${esc(e.stage || '')}</td>
          <td>${esc(e.status || '')}</td><td>${e.retryCount || 0}/${e.maxRetries || 0}</td>
          <td>${esc(e.lastError || '')}</td>
        </tr>
      `).join('');
    };

    const reload = async () => {
      templates = await AdminApi.fetch('/api/v1/admin/pipeline/templates');
      executions = await AdminApi.fetch('/api/v1/admin/pipeline/executions');
      Object.keys(stepsByTemplateId).forEach(k => delete stepsByTemplateId[k]);
      await Promise.all((templates || []).map(async t => {
        try {
          stepsByTemplateId[t.id] = await AdminApi.fetch('/api/v1/admin/pipeline/steps?templateId=' + t.id);
        } catch (e) {
          stepsByTemplateId[t.id] = [];
        }
      }));
      await renderTemplateBoard();
      renderExecRows();
      if (activeTemplateId && stepsByTemplateId[activeTemplateId]) {
        renderStepEditorPanel(activeTemplateId);
      }
    };

    document.getElementById('pl-new-template').onclick = () => openTemplateEditor(null);

    document.getElementById('pl-cancel').onclick = () => AdminUi.closeEditor(document.getElementById('pl-editor'));
    document.getElementById('pl-reload').onclick = reload;
    document.getElementById('pl-save').onclick = async () => {
      try {
        if (formMode === 'template' || formMode === 'template-edit') {
          const templateCode = document.getElementById('pl-f-template-code').value.trim();
          const templateName = document.getElementById('pl-f-template-name').value.trim() || templateCode;
          const stage = document.getElementById('pl-f-template-stage').value;
          const description = document.getElementById('pl-f-template-desc').value.trim();
          const enabled = document.getElementById('pl-f-template-enabled').checked;
          if (!templateCode) return showSaveMsg('templateCode 必填', true);
          await AdminApi.fetch('/api/v1/admin/pipeline/templates', {
            method: 'POST',
            body: JSON.stringify({
              id: formMode === 'template-edit' ? formTemplateId : null,
              templateCode, templateName, stage, description, enabled, versionNo: 1
            })
          });
          AdminUi.closeEditor(document.getElementById('pl-editor'));
          showMsg((formMode === 'template-edit' ? '模板已更新: ' : '模板已创建: ') + templateCode, false);
        } else {
          const stepId = document.getElementById('pl-form-step-id').value.trim();
          const stepCode = document.getElementById('pl-f-step-code').value.trim();
          const stepName = document.getElementById('pl-f-step-name').value.trim() || stepCode;
          const stepType = document.getElementById('pl-f-step-type').value.trim() || 'preset-sync';
          const stage = document.getElementById('pl-f-step-stage').value;
          const orderNo = Number(document.getElementById('pl-f-step-order').value || '1');
          const timeoutSeconds = Number(document.getElementById('pl-f-step-timeout').value || '120');
          const configJson = document.getElementById('pl-f-step-config').value.trim() || '{}';
          const enabled = document.getElementById('pl-f-step-enabled').checked;
          if (!formTemplateId) return showSaveMsg('templateId 不能为空', true);
          if (!stepCode) return showSaveMsg('stepCode 必填', true);
          await AdminApi.fetch('/api/v1/admin/pipeline/steps', {
            method: 'POST',
            body: JSON.stringify({
              id: stepId ? Number(stepId) : null,
              templateId: formTemplateId, stepCode, stepName, stepType, stage,
              orderNo, timeoutSeconds, configJson, enabled
            })
          });
          AdminUi.closeEditor(document.getElementById('pl-editor'));
          showMsg((stepId ? '步骤已更新: ' : '步骤已创建: ') + stepCode, false);
        }
        await reload();
      } catch (err) {
        showSaveMsg('保存失败: ' + err.message, true);
      }
    };

    document.getElementById('pl-exec').onclick = async () => {
      const presetTypeCodeText = document.getElementById('pl-preset-type-code').value.trim();
      const meetingId = document.getElementById('pl-meeting-id').value.trim();
      const stage = document.getElementById('pl-stage').value;
      const templateCode = document.getElementById('pl-template-code').value.trim();
      const skipExisting = !!document.getElementById('pl-skip-existing').checked;
      const presetTypeCode = presetTypeCodeText ? Number(presetTypeCodeText) : null;
      if (!meetingId && !presetTypeCode) {
        return showMsg('presetTypeCode 与 meetingId 至少填一个', true);
      }
      if (presetTypeCodeText && (!Number.isInteger(presetTypeCode) || presetTypeCode <= 0)) {
        return showMsg('presetTypeCode 必须为正整数', true);
      }
      try {
        const res = await AdminApi.fetch('/api/v1/admin/pipeline/execute', {
          method: 'POST',
          body: JSON.stringify({
            meetingId: meetingId || null,
            presetTypeCode: presetTypeCode,
            stage,
            templateCode,
            skipExisting
          })
        });
        if (res && (res.mode === 'preset' || res.mode === 'preset_direct')) {
          if (res.mode === 'preset_direct') {
            const success = Number(res.successSteps || 0);
            const failed = Number(res.failedSteps || 0);
            document.getElementById('pl-result').textContent = '按 preset 触发完成：成功步骤 ' + success + '，失败步骤 ' + failed;
            showMsg('按 preset 触发完成：成功 ' + success + '，失败 ' + failed, false);
          } else {
            const matched = Number(res.matchedMeetings || 0);
            const triggered = Number(res.triggeredMeetings || 0);
            document.getElementById('pl-result').textContent = '按 preset 触发完成：匹配 ' + matched + ' 场，触发 ' + triggered + ' 场';
            showMsg('按 preset 触发完成：匹配 ' + matched + '，触发 ' + triggered, false);
          }
        } else {
          document.getElementById('pl-result').textContent = '按 meetingId 触发已提交';
          showMsg('执行请求已提交: ' + (meetingId || ''), false);
        }
        executions = await AdminApi.fetch('/api/v1/admin/pipeline/executions');
        renderExecRows();
      } catch (e) {
        document.getElementById('pl-result').textContent = '执行失败: ' + e.message;
        showMsg('执行失败: ' + e.message, true);
      }
    };

    await reload();
  }
});
