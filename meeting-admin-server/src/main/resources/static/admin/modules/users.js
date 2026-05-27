AdminModules.register({
  route: '/users',
  mount: async function (root) {
    let pageNum = 1;
    const pageSize = 20;
    let keyword = '';
    let expiryFilter = 'ALL';

    root.innerHTML = `
      <div class="panel">
        <p class="module-intro">${AdminHints.users.moduleIntro}</p>
        <span id="usr-msg" class="msg hidden"></span>
      </div>
      <div id="usr-main"></div>`;

    const showMsg = (text, isErr) => {
      const el = document.getElementById('usr-msg');
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };

    const esc = s => (s == null ? '' : String(s)).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
    const fmtDt = s => s ? String(s).replace('T', ' ').slice(0, 19) : '-';
    const cellLong = (v, max) => {
      const t = v == null ? '' : String(v);
      if (!t) return '-';
      const short = t.length > (max || 14) ? t.slice(0, max || 14) + '…' : t;
      return '<span title="' + esc(t) + '">' + esc(short) + '</span>';
    };

    const parseUserId = raw => {
      const s = String(raw == null ? '' : raw).trim();
      if (!s || !/^-?\d+$/.test(s)) return null;
      const n = Number(s);
      return Number.isSafeInteger(n) && n > 0 ? n : null;
    };

    const expiryBadge = st => {
      if (st === 'EXPIRED') return '<span class="msg msg-err">已过期</span>';
      if (st === 'EXPIRING') return '<span class="msg">即将过期</span>';
      if (st === 'VALID') return '<span class="msg msg-ok">有效</span>';
      if (!st) return '<span class="muted">无声纹</span>';
      return esc(st);
    };

    function toLocalInput(iso) {
      if (!iso) return '';
      const d = new Date(iso);
      if (Number.isNaN(d.getTime())) return '';
      const pad = n => String(n).padStart(2, '0');
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
        + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }

    function fromLocalInput(val) {
      if (!val) return null;
      const s = val.trim();
      if (!s) return null;
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s)) return s + ':00';
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(s)) return s;
      return null;
    }

    function renderPager(containerId, page, onPage) {
      const el = document.getElementById(containerId);
      const total = page.total || 0;
      const current = page.current || 1;
      const pages = Math.max(1, Math.ceil(total / (page.size || pageSize)));
      el.innerHTML = '';
      if (pages <= 1) return;
      const prev = document.createElement('button');
      prev.type = 'button';
      prev.textContent = '上一页';
      prev.disabled = current <= 1;
      prev.onclick = () => onPage(current - 1);
      const next = document.createElement('button');
      next.type = 'button';
      next.textContent = '下一页';
      next.disabled = current >= pages;
      next.onclick = () => onPage(current + 1);
      const info = document.createElement('span');
      info.className = 'muted';
      info.textContent = ' 第 ' + current + ' / ' + pages + ' 页 ';
      el.appendChild(prev);
      el.appendChild(info);
      el.appendChild(next);
    }

    async function loadList() {
      const panel = document.getElementById('usr-main');
      panel.innerHTML = '<div class="panel"><p class="muted">加载中…</p></div>';
      try {
        const qs = new URLSearchParams({ page: pageNum, size: pageSize, expiryFilter: expiryFilter });
        if (keyword) qs.set('keyword', keyword);
        const page = await AdminApi.fetch('/api/v1/admin/users?' + qs);
        const rows = page.records || [];

        panel.innerHTML = `
          <div class="panel toolbar">
            <button type="button" class="primary" id="usr-new">新建用户</button>
            <input id="usr-kw" type="search" placeholder="姓名 / 飞书 ID / featureId / OA userId" value="${esc(keyword)}"/>
            <select id="usr-expiry" title="${esc(AdminHints.users.expiryFilter)}">
              <option value="ALL">声纹：全部</option>
              <option value="VALID">声纹：有效</option>
              <option value="EXPIRING">声纹：48h 内过期</option>
              <option value="EXPIRED">声纹：已过期</option>
            </select>
            <button type="button" id="usr-search">搜索</button>
            <span class="muted">共 ${page.total || 0} 人</span>
          </div>
          <div class="panel table-wrap"><table class="table-wide"><thead><tr>
            <th>OA userId</th><th>姓名</th><th>feishu_user_id</th><th>union_id</th><th>open_id</th>
            <th>声纹</th><th>featureId</th><th>groupId</th><th>注册</th><th>过期</th><th>操作</th>
          </tr></thead><tbody id="usr-tbody"></tbody></table></div>
          <div class="panel toolbar" id="usr-pager"></div>
          <div class="panel hidden form-editor" id="usr-editor"></div>`;

        document.getElementById('usr-expiry').value = expiryFilter;
        const tbody = document.getElementById('usr-tbody');
        rows.forEach(r => {
          const tr = document.createElement('tr');
          const feat = r.voiceprintCount > 1
            ? esc(r.featureId) + ' <span class="muted">(+' + (r.voiceprintCount - 1) + ')</span>'
            : esc(r.featureId || '-');
          tr.innerHTML = '<td>' + esc(r.userId) + '</td><td>' + esc(r.userName) + '</td>'
            + '<td>' + cellLong(r.feishuUserId, 12) + '</td>'
            + '<td>' + cellLong(r.feishuUnionId, 16) + '</td>'
            + '<td>' + cellLong(r.feishuOpenId, 16) + '</td>'
            + '<td>' + expiryBadge(r.hasVoiceprint ? r.expiryStatus : null) + '</td>'
            + '<td>' + feat + '</td>'
            + '<td>' + esc(r.groupId || '-') + '</td>'
            + '<td>' + fmtDt(r.registeredAt) + '</td>'
            + '<td>' + fmtDt(r.expiresAt) + '</td>'
            + '<td><a href="#" class="usr-edit" data-id="' + r.userId + '">编辑</a> '
            + '<a href="#" class="usr-del" data-id="' + r.userId + '">删除</a></td>';
          tbody.appendChild(tr);
        });

        renderPager('usr-pager', page, p => { pageNum = p; loadList(); });
        document.getElementById('usr-kw').onkeydown = e => {
          if (e.key === 'Enter') document.getElementById('usr-search').click();
        };
        document.getElementById('usr-search').onclick = () => {
          keyword = document.getElementById('usr-kw').value.trim();
          expiryFilter = document.getElementById('usr-expiry').value;
          pageNum = 1;
          loadList();
        };
        document.getElementById('usr-new').onclick = () => showEditor(null);
        tbody.querySelectorAll('a.usr-edit').forEach(a => {
          a.onclick = async e => {
            e.preventDefault();
            try {
              const detail = await AdminApi.fetch('/api/v1/admin/users/' + a.dataset.id);
              showEditor(detail);
            } catch (err) {
              showMsg(err.message, true);
            }
          };
        });
        tbody.querySelectorAll('a.usr-del').forEach(a => {
          a.onclick = async e => {
            e.preventDefault();
            if (!confirm('删除用户 #' + a.dataset.id + '？将同时删除其飞书映射与全部声纹记录。')) return;
            try {
              await AdminApi.fetch('/api/v1/admin/users/' + a.dataset.id, { method: 'DELETE' });
              showMsg('已删除用户 #' + a.dataset.id, false);
              loadList();
            } catch (err) {
              showMsg(err.message, true);
            }
          };
        });
      } catch (err) {
        panel.innerHTML = '<div class="panel"><p class="msg msg-err">加载失败: ' + esc(err.message) + '</p></div>';
        showMsg(err.message, true);
      }
    }

    function showEditor(detail) {
      const isNew = !detail;
      const mapping = isNew ? {} : (detail.mapping || {});
      const vp = isNew ? {} : (detail.primaryVoiceprint || {});
      const ed = document.getElementById('usr-editor');
      ed.classList.remove('hidden');
      ed.innerHTML = `
        <h3>${isNew ? '新建用户' : '编辑用户 #' + esc(mapping.userId)}</h3>
        <p class="form-hint">${AdminHints.users.editorIntro}</p>
        <h4>飞书映射 · int_user_mapping_feishu</h4>
        ${AdminForm.field('OA userId', '<input id="ed-user-id" type="number"/>', AdminHints.users.userId)}
        ${AdminForm.field('姓名', '<input id="ed-user-name" type="text"/>', AdminHints.users.userName)}
        ${AdminForm.field('feishu_user_id', '<input id="ed-feishu-user" type="text"/>', AdminHints.users.feishuUserId)}
        ${AdminForm.field('feishu_union_id', '<input id="ed-feishu-union" type="text"/>', AdminHints.users.feishuUnionId)}
        ${AdminForm.field('feishu_open_id', '<input id="ed-feishu-open" type="text"/>', AdminHints.users.feishuOpenId)}
        <h4>声纹 · int_voiceprint</h4>
        <input type="hidden" id="ed-vp-id" value="${esc(vp.id || '')}"/>
        ${AdminForm.field('featureId', '<input id="ed-feature-id" type="text"/>', AdminHints.users.featureIdOptional)}
        ${AdminForm.field('groupId', '<input id="ed-group-id" type="text"/>', AdminHints.users.groupId)}
        ${AdminForm.field('注册时间', '<input id="ed-registered" type="datetime-local"/>', AdminHints.users.registeredAt)}
        ${AdminForm.field('过期时间', '<input id="ed-expires" type="datetime-local"/>', AdminHints.users.expiresAt)}
        <div class="form-field">
          <div class="form-check-row">
            <label class="check-label"><input type="checkbox" id="ed-clear-vp"/> 清除该用户全部声纹</label>
          </div>
          ${AdminForm.hint(AdminHints.users.clearVoiceprint)}
        </div>
        <p id="ed-save-msg" class="msg hidden"></p>
        <button type="button" class="primary" id="ed-save">保存</button>
        <button type="button" id="ed-cancel">取消</button>`;

      const uidInput = document.getElementById('ed-user-id');
      uidInput.value = mapping.userId != null ? mapping.userId : '';
      uidInput.readOnly = !isNew;
      document.getElementById('ed-user-name').value = mapping.userName || '';
      document.getElementById('ed-feishu-user').value = mapping.feishuUserId || '';
      document.getElementById('ed-feishu-union').value = mapping.feishuUnionId || '';
      document.getElementById('ed-feishu-open').value = mapping.feishuOpenId || '';
      document.getElementById('ed-feature-id').value = vp.featureId || '';
      document.getElementById('ed-group-id').value = vp.groupId || '';
      document.getElementById('ed-registered').value = toLocalInput(vp.registeredAt);
      document.getElementById('ed-expires').value = toLocalInput(vp.expiresAt);
      document.getElementById('ed-clear-vp').checked = false;

      document.getElementById('ed-cancel').onclick = () => ed.classList.add('hidden');
      document.getElementById('ed-save').onclick = () => saveEditor(isNew);
      ed.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    async function saveEditor(isNew) {
      const msgEl = document.getElementById('ed-save-msg');
      const userId = parseUserId(document.getElementById('ed-user-id').value);
      const mapping = {
        userId: userId,
        userName: document.getElementById('ed-user-name').value.trim(),
        feishuUserId: document.getElementById('ed-feishu-user').value.trim() || null,
        feishuUnionId: document.getElementById('ed-feishu-union').value.trim() || null,
        feishuOpenId: document.getElementById('ed-feishu-open').value.trim() || null
      };
      const registeredRaw = document.getElementById('ed-registered').value.trim();
      const expiresRaw = document.getElementById('ed-expires').value.trim();
      const registeredAt = fromLocalInput(registeredRaw);
      const expiresAt = fromLocalInput(expiresRaw);
      const featureId = document.getElementById('ed-feature-id').value.trim();
      const clearVoiceprint = document.getElementById('ed-clear-vp').checked;

      if (userId == null) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = 'OA userId 须为正整数';
        return;
      }
      if (!mapping.userName) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = '请填写姓名';
        return;
      }
      if (registeredRaw && !registeredAt) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = '注册时间格式无效';
        return;
      }
      if (expiresRaw && !expiresAt) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = '过期时间格式无效';
        return;
      }
      if (registeredAt && expiresAt && expiresAt <= registeredAt) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = '过期时间须晚于注册时间';
        return;
      }

      const body = {
        mapping: mapping,
        clearVoiceprint: clearVoiceprint
      };
      if (!clearVoiceprint && featureId) {
        body.voiceprint = {
          id: document.getElementById('ed-vp-id').value.trim() || null,
          userId: userId,
          userName: mapping.userName,
          feishuUserId: mapping.feishuUserId,
          featureId: featureId,
          groupId: document.getElementById('ed-group-id').value.trim() || null,
          registeredAt: registeredAt,
          expiresAt: expiresAt
        };
      }

      try {
        if (isNew) {
          await AdminApi.fetch('/api/v1/admin/users', { method: 'POST', body: JSON.stringify(body) });
        } else {
          await AdminApi.fetch('/api/v1/admin/users/' + userId, { method: 'PUT', body: JSON.stringify(body) });
        }
        document.getElementById('usr-editor').classList.add('hidden');
        showMsg('用户档案已保存', false);
        loadList();
      } catch (err) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = err.message;
      }
    }

    loadList();
  }
});
