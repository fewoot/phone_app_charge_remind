/* ============================================================
   充电提醒 · 交互原型（纯前端，数据存 localStorage）
   ============================================================ */
(function () {
  'use strict';

  /* ---------------- 常量 ---------------- */
  var KEY = 'charge-reminder-proto-v2';
  var DAY = 86400000;
  var WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

  var PALETTES = {
    mint:   { name: '薄荷', accent: '#0B8F6A', accent2: '#37C99A' },
    ocean:  { name: '海盐', accent: '#2563EB', accent2: '#60A5FA' },
    sunset: { name: '暖阳', accent: '#C2620F', accent2: '#F59E0B' },
    violet: { name: '紫霞', accent: '#6D4AE0', accent2: '#A78BFA' }
  };

  var EMOJIS = ['🔋', '⌚', '🎧', '🪥', '🪒', '📷', '🤖', '💡', '🎮', '📱', '🔦', '🎒'];

  var TEMPLATES = [
    { key: 'powerbank',  name: '充电宝',  emoji: '🔋', interval: 14, unit: 'day' },
    { key: 'watch',      name: '手表',    emoji: '⌚', interval: 7,  unit: 'day' },
    { key: 'earbuds',    name: '耳机',    emoji: '🎧', interval: 10, unit: 'day' },
    { key: 'toothbrush', name: '电动牙刷', emoji: '🪥', interval: 14, unit: 'day' },
    { key: 'shaver',     name: '剃须刀',  emoji: '🪒', interval: 21, unit: 'day' },
    { key: 'camera',     name: '相机电池', emoji: '📷', interval: 30, unit: 'day' },
    { key: 'vacuum',     name: '扫地机',  emoji: '🤖', interval: 30, unit: 'day' },
    { key: 'other',      name: '其他',    emoji: '💡', interval: 14, unit: 'day' }
  ];

  var COLORS = ['#0B8F6A', '#2563EB', '#C2620F', '#6D4AE0', '#DB2777', '#64748B'];

  var DEFAULT_SETTINGS = {
    theme: 'auto',
    palette: 'mint',
    bgType: 'gradient',
    bgImage: null,
    blur: 8,
    dim: 45,
    dynamicColor: true,
    accentFromImage: null,
    defaultIntervalValue: 14,
    defaultIntervalUnit: 'day',
    defaultRemindTime: '09:00',
    defaultAdvance: 0,
    dndStart: '23:00',
    dndEnd: '08:00',
    strength: 'notification',
    dailyOverdue: true,
    perms: { notification: true, exactAlarm: true, battery: false }
  };

  /* ---------------- 工具 ---------------- */
  function $(s, r) { return (r || document).querySelector(s); }
  function $$(s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); }
  function clamp(v, a, b) { return Math.min(b, Math.max(a, v)); }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function uid() { return Math.random().toString(36).slice(2, 9); }
  function startOfDay(d) { var x = new Date(d); x.setHours(0, 0, 0, 0); return x; }
  function dayDiff(a, b) { return Math.round((startOfDay(a) - startOfDay(b)) / DAY); }
  function fmtMD(d) { return (d.getMonth() + 1) + '/' + d.getDate(); }
  function fmtDate(d) { return (d.getMonth() + 1) + '月' + d.getDate() + '日'; }
  function fmtWD(d) { return WEEK[d.getDay()]; }
  function toDateInput(ts) {
    var d = new Date(ts);
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  /* ---------------- 数据 ---------------- */
  function makeDevice(p) {
    return Object.assign({
      id: uid(),
      name: '',
      emoji: '🔋',
      photo: null,
      color: null,
      type: 'recurring',
      intervalValue: 14,
      unit: 'day',
      dueDate: null,
      remindTime: '09:00',
      advance: 0,
      adaptive: true,
      enabled: true,
      completed: false,
      snoozeDays: 0,
      note: '',
      lastChargedAt: Date.now(),
      createdAt: Date.now(),
      history: []
    }, p || {});
  }

  function seed() {
    function ago(n) {
      var x = new Date();
      x.setHours(9, 0, 0, 0);
      x.setDate(x.getDate() - n);
      return x.getTime();
    }
    var devices = [
      makeDevice({
        name: '充电宝', emoji: '🔋', intervalValue: 14, unit: 'day', remindTime: '09:00',
        lastChargedAt: ago(12), adaptive: true,
        history: [
          { at: ago(12), pct: 21, gap: 14 },
          { at: ago(26), pct: 26, gap: 15 },
          { at: ago(41), pct: 18, gap: 14 },
          { at: ago(55), pct: 31, gap: 13 }
        ]
      }),
      makeDevice({
        name: '手表', emoji: '⌚', intervalValue: 7, unit: 'day', remindTime: '20:00', advance: 1,
        lastChargedAt: ago(7), adaptive: true,
        history: [
          { at: ago(7), pct: 12, gap: 7 },
          { at: ago(14), pct: 15, gap: 7 },
          { at: ago(22), pct: 9, gap: 8 }
        ]
      }),
      makeDevice({
        name: '耳机', emoji: '🎧', intervalValue: 10, unit: 'day', remindTime: '09:00',
        lastChargedAt: ago(14), adaptive: false,
        history: [
          { at: ago(14), pct: 5, gap: 10 },
          { at: ago(24), pct: 8, gap: 9 }
        ]
      })
    ];
    return { devices: devices, settings: JSON.parse(JSON.stringify(DEFAULT_SETTINGS)) };
  }

  function load() {
    try {
      var raw = localStorage.getItem(KEY);
      if (!raw) return null;
      var s = JSON.parse(raw);
      if (!s || !Array.isArray(s.devices)) return null;
      return s;
    } catch (e) { return null; }
  }
  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {}
  }

  var state = load() || seed();
  state.settings = Object.assign({}, DEFAULT_SETTINGS, state.settings || {});
  state.settings.perms = Object.assign({}, DEFAULT_SETTINGS.perms, (state.settings && state.settings.perms) || {});
  state.devices = (state.devices || []).map(function (d) { return makeDevice(d); });

  var ui = { detailId: null, editingId: null, draft: null };
  var navStack = [];
  var dialogResolve = null;
  var chargeCtx = { id: null, pct: 25 };
  var toastTimer = null;

  /* ---------------- 设备计算 ---------------- */
  function getDevice(id) {
    for (var i = 0; i < state.devices.length; i++) if (state.devices[i].id === id) return state.devices[i];
    return null;
  }
  function unitDays(u) { return u === 'week' ? 7 : u === 'month' ? 30 : 1; }
  function intervalDays(d) {
    if (d.type === 'onetime') return 0;
    return Math.max(1, Math.round(d.intervalValue * unitDays(d.unit)));
  }
  function intervalLabel(d) {
    var u = d.unit === 'week' ? '周' : d.unit === 'month' ? '个月' : '天';
    return d.intervalValue + ' ' + u;
  }
  function dueOf(d) {
    if (d.type === 'onetime') return startOfDay(new Date(d.dueDate || Date.now()));
    var base = startOfDay(new Date(d.lastChargedAt));
    var due = new Date(base);
    due.setDate(due.getDate() + intervalDays(d) + (d.snoozeDays || 0));
    return due;
  }
  function daysLeft(d) { return dayDiff(dueOf(d), Date.now()); }
  function progressOf(d) {
    if (d.type === 'onetime') {
      var total = Math.max(1, dayDiff(dueOf(d), d.createdAt || Date.now()));
      return clamp(1 - daysLeft(d) / total, 0, 1);
    }
    return clamp(dayDiff(Date.now(), d.lastChargedAt) / intervalDays(d), 0, 1);
  }
  function statusOf(d) {
    if (d.type === 'onetime' && d.completed) return { tone: 'done', text: '已完成' };
    if (!d.enabled) return { tone: 'off', text: '已停用' };
    var n = daysLeft(d);
    if (n > 3) return { tone: 'ok', text: '还有 ' + n + ' 天' };
    if (n > 0) return { tone: 'soon', text: '还有 ' + n + ' 天' };
    if (n === 0) return { tone: 'today', text: '今天该充了' };
    return { tone: 'over', text: '逾期 ' + (-n) + ' 天' };
  }
  function toneColor(t) {
    if (t === 'over') return 'var(--red)';
    if (t === 'today' || t === 'soon') return 'var(--amber)';
    if (t === 'off') return 'var(--text-3)';
    return 'var(--accent)';
  }
  function sortedDevices() {
    return state.devices.slice().sort(function (a, b) {
      var aw = (a.enabled && !(a.type === 'onetime' && a.completed)) ? 0 : 1;
      var bw = (b.enabled && !(b.type === 'onetime' && b.completed)) ? 0 : 1;
      if (aw !== bw) return aw - bw;
      return daysLeft(a) - daysLeft(b);
    });
  }
  function avgGap(d) {
    var gaps = d.history.filter(function (h) { return h.gap != null; }).map(function (h) { return h.gap; });
    if (!gaps.length) return null;
    var sum = gaps.reduce(function (a, b) { return a + b; }, 0);
    return Math.round(sum / gaps.length);
  }

  /* ---------------- 外观 ---------------- */
  function resolveTheme() {
    if (state.settings.theme === 'auto') {
      return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return state.settings.theme;
  }

  function applyAppearance() {
    var s = state.settings;
    var root = document.documentElement;
    var theme = resolveTheme();
    root.dataset.theme = theme;
    root.dataset.palette = s.palette;
    root.dataset.bg = s.bgType;

    ['--accent', '--accent-2', '--accent-soft', '--glow'].forEach(function (k) {
      root.style.removeProperty(k);
    });
    if (s.bgType === 'image' && s.dynamicColor && s.accentFromImage) {
      root.style.setProperty('--accent', s.accentFromImage.accent);
      root.style.setProperty('--accent-2', s.accentFromImage.accent2);
      root.style.setProperty('--accent-soft', s.accentFromImage.soft);
      root.style.setProperty('--glow', s.accentFromImage.glow);
    }

    var photo = $('#bgPhoto');
    photo.style.backgroundImage = s.bgImage ? 'url("' + s.bgImage + '")' : 'none';
    photo.style.filter = 'blur(' + s.blur + 'px)';

    var dim = clamp(s.dim / 100 + (theme === 'dark' ? 0.12 : 0), 0, 0.86);
    root.style.setProperty('--dim', dim.toFixed(3));
  }

  /* ---------------- 首页 ---------------- */
  function renderHome() {
    var now = new Date();
    $('#homeDate').textContent = (now.getMonth() + 1) + '月' + now.getDate() + '日 ' + WEEK[now.getDay()];

    var list = sortedDevices();
    var overview = $('#overview');
    if (!list.length) {
      overview.innerHTML = '';
      $('#deviceList').innerHTML = emptyHTML();
      return;
    }

    var overdue = list.filter(function (d) { return d.enabled && daysLeft(d) < 0; }).length;
    var today = list.filter(function (d) { return d.enabled && daysLeft(d) === 0; }).length;
    var chips = '<span class="overview-chip">' + list.length + ' 台设备</span>';
    if (overdue) chips += '<span class="overview-chip warn">' + overdue + ' 台逾期</span>';
    if (today) chips += '<span class="overview-chip warn">' + today + ' 台今天</span>';
    if (!overdue && !today) chips += '<span class="overview-chip ok">全部正常</span>';
    overview.innerHTML = chips;

    $('#deviceList').innerHTML = list.map(cardHTML).join('');
  }

  function cardHTML(d, i) {
    var st = statusOf(d);
    var p = progressOf(d);
    var circ = 150.8;
    var due = dueOf(d);
    var devColor = d.color || 'var(--accent)';
    var icon = d.photo
      ? '<img src="' + d.photo + '" alt="">'
      : '<span>' + d.emoji + '</span>';

    var meta, dueLine;
    if (d.type === 'onetime') {
      meta = '一次性任务';
      dueLine = '到期 ' + fmtDate(due) + ' ' + fmtWD(due) + ' · ' + d.remindTime + ' 提醒';
    } else {
      meta = '上次 ' + fmtMD(new Date(d.lastChargedAt)) + ' · 每 ' + intervalLabel(d) +
        (d.snoozeDays ? ' · 已推迟 ' + d.snoozeDays + ' 天' : '');
      dueLine = fmtDate(due) + ' ' + fmtWD(due) + ' · ' + d.remindTime + ' 提醒' + (d.advance ? ' · 提前 ' + d.advance + ' 天' : '');
    }

    return '' +
      '<article class="device-card ' + (d.enabled ? '' : 'is-off') + '" data-action="open" data-id="' + d.id + '" style="--i:' + i + ';--dev-color:' + devColor + '">' +
        '<div class="card-main">' +
          '<div class="ring" style="--ring-color:' + toneColor(st.tone) + '">' +
            '<svg viewBox="0 0 56 56" aria-hidden="true">' +
              '<circle class="ring-track" cx="28" cy="28" r="24"></circle>' +
              '<circle class="ring-fill" cx="28" cy="28" r="24" style="stroke-dasharray:' + circ + ';stroke-dashoffset:' + (circ * (1 - p)).toFixed(1) + '"></circle>' +
            '</svg>' +
            '<div class="ring-icon">' + icon + '</div>' +
          '</div>' +
          '<div class="card-text">' +
            '<div class="card-title-row">' +
              '<h3>' + esc(d.name) + '</h3>' +
              '<span class="status status-' + st.tone + '">' + st.text + '</span>' +
            '</div>' +
            '<p class="card-meta">' + meta + '</p>' +
            '<p class="card-due">' + dueLine + '</p>' +
          '</div>' +
          '<svg class="chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5l7 7-7 7"></path></svg>' +
        '</div>' +
        '<div class="card-foot">' +
          '<label class="switch-label" data-noopen>' +
            '<span>' + (d.enabled ? '已启用' : '已停用') + '</span>' +
            '<span class="switch"><input type="checkbox" data-toggle="' + d.id + '"' + (d.enabled ? ' checked' : '') + '><i></i></span>' +
          '</label>' +
          '<button class="btn-charge" data-action="charge" data-id="' + d.id + '"' + (d.enabled ? '' : ' disabled') + '>' +
            (d.type === 'onetime' ? '完成' : '已充电') +
          '</button>' +
        '</div>' +
      '</article>';
  }

  function emptyHTML() {
    return '' +
      '<div class="empty">' +
        '<svg viewBox="0 0 200 150" fill="none" aria-hidden="true">' +
          '<rect x="62" y="34" width="76" height="92" rx="24" fill="var(--accent-soft)"></rect>' +
          '<rect x="88" y="20" width="24" height="12" rx="5" fill="var(--accent)" opacity=".3"></rect>' +
          '<path d="M104 54 L86 84 h11 l-2 18 18-28 h-11 z" fill="var(--accent)"></path>' +
          '<circle cx="48" cy="118" r="4" fill="var(--accent)" opacity=".22"></circle>' +
          '<circle cx="156" cy="44" r="6" fill="var(--accent)" opacity=".18"></circle>' +
          '<circle cx="152" cy="120" r="3" fill="var(--accent)" opacity=".25"></circle>' +
        '</svg>' +
        '<h3>还没有设备</h3>' +
        '<p>添加第一台设备，到日子我会提醒你</p>' +
        '<button class="btn-primary" data-action="add">添加设备</button>' +
      '</div>';
  }

  /* ---------------- 详情 ---------------- */
  function renderDetail() {
    var d = getDevice(ui.detailId);
    if (!d) { back(); return; }
    var st = statusOf(d);
    var p = progressOf(d);
    var circ = 150.8;
    var due = dueOf(d);
    var icon = d.photo ? '<img src="' + d.photo + '" alt="">' : '<span>' + d.emoji + '</span>';
    var avg = avgGap(d);

    var html = '' +
      '<div class="detail-hero">' +
        '<div class="ring ring-xl" style="--ring-color:' + toneColor(st.tone) + ';--dev-color:' + (d.color || 'var(--accent)') + '">' +
          '<svg viewBox="0 0 56 56" aria-hidden="true">' +
            '<circle class="ring-track" cx="28" cy="28" r="24"></circle>' +
            '<circle class="ring-fill" cx="28" cy="28" r="24" style="stroke-dasharray:' + circ + ';stroke-dashoffset:' + (circ * (1 - p)).toFixed(1) + '"></circle>' +
          '</svg>' +
          '<div class="ring-icon">' + icon + '</div>' +
        '</div>' +
        '<h2>' + esc(d.name) + '</h2>' +
        '<p class="status-lg status-' + st.tone + '">' + st.text + '</p>' +
        '<p class="detail-sub">下次提醒 ' + fmtDate(due) + ' ' + fmtWD(due) + ' · ' + d.remindTime +
          (d.snoozeDays ? '（已推迟 ' + d.snoozeDays + ' 天）' : '') + '</p>' +
        (d.type === 'recurring'
          ? '<p class="detail-sub">上次充电 ' + fmtDate(new Date(d.lastChargedAt)) + ' · 每 ' + intervalLabel(d) + '</p>'
          : '<p class="detail-sub">一次性任务 · ' + (d.completed ? '已完成' : '等待完成') + '</p>') +
      '</div>' +
      '<div class="stat-grid">' +
        '<div class="stat"><b>' + (avg == null ? '—' : avg + ' 天') + '</b><span>平均间隔</span></div>' +
        '<div class="stat"><b>' + d.history.length + '</b><span>已充电次数</span></div>' +
        '<div class="stat"><b>' + (d.adaptive ? '开' : '关') + '</b><span>充电自适应</span></div>' +
      '</div>' +
      '<section class="panel">' +
        '<div class="panel-head"><h3>间隔趋势</h3><span>单位：天' +
          (d.type === 'recurring' ? ' · 目标 ' + intervalDays(d) + ' 天' : '') +
        '</span></div>' +
        chartSVG(d) +
      '</section>' +
      '<section class="panel">' +
        '<div class="panel-head"><h3>充电记录</h3><span>' + d.history.length + ' 条</span></div>' +
        historyHTML(d) +
      '</section>' +
      '<div class="detail-actions">' +
        '<button class="btn-primary" data-action="charge">记录充电</button>' +
        '<button class="btn-danger-ghost" data-action="delete">删除设备</button>' +
      '</div>';

    $('#detailBody').innerHTML = html;
  }

  function chartSVG(d) {
    var items = d.history.slice(-6).map(function (h) {
      return { date: new Date(h.at), gap: h.gap || 0 };
    });
    if (items.length < 2) {
      return '<p class="chart-empty">再记录几次充电，就能看到间隔趋势了</p>';
    }
    var W = 320, H = 150, L = 10, R = 10, T = 22, B = 26;
    var target = d.type === 'recurring' ? intervalDays(d) : 0;
    var max = Math.max.apply(null, items.map(function (i) { return i.gap; }).concat([target, 1])) * 1.2;
    var n = items.length;
    var step = (W - L - R) / n;
    var bw = Math.min(26, step * 0.5);
    var scale = (H - T - B) / max;
    var bars = '';

    items.forEach(function (it, i) {
      var x = L + step * i + (step - bw) / 2;
      var h = Math.max(4, it.gap * scale);
      var y = H - B - h;
      bars += '<rect x="' + x.toFixed(1) + '" y="' + y.toFixed(1) + '" width="' + bw.toFixed(1) + '" height="' + h.toFixed(1) + '" rx="6" fill="var(--accent)" opacity=".85"></rect>';
      bars += '<text x="' + (x + bw / 2).toFixed(1) + '" y="' + (y - 6).toFixed(1) + '" text-anchor="middle" font-size="9" fill="var(--text-3)">' + it.gap + '</text>';
      bars += '<text x="' + (x + bw / 2).toFixed(1) + '" y="' + (H - 8) + '" text-anchor="middle" font-size="9" fill="var(--text-3)">' + fmtMD(it.date) + '</text>';
    });

    var line = '';
    if (target) {
      var ty = (H - B - target * scale).toFixed(1);
      line = '<line x1="' + L + '" x2="' + (W - R) + '" y1="' + ty + '" y2="' + ty + '" stroke="var(--text-3)" stroke-width="1" stroke-dasharray="4 4" opacity=".45"></line>';
    }
    return '<svg class="chart" viewBox="0 0 ' + W + ' ' + H + '">' + bars + line + '</svg>';
  }

  function historyHTML(d) {
    if (!d.history.length) {
      return '<p class="history-empty">还没有记录，充完电点一下「已充电」就会出现在这里。</p>';
    }
    return d.history.slice().sort(function (a, b) { return b.at - a.at; }).map(function (h) {
      var date = new Date(h.at);
      var gap = h.gap == null ? '首次记录' : '间隔 ' + h.gap + ' 天';
      var pct = h.pct == null ? '' : '<span class="h-pct">剩余 ' + h.pct + '%</span>';
      return '<div class="history-row">' +
        '<span class="h-date">' + fmtMD(date) + '</span>' +
        '<span class="h-gap">' + fmtWD(date) + ' · ' + gap + '</span>' +
        pct +
      '</div>';
    }).join('');
  }

  /* ---------------- 编辑 ---------------- */
  function renderEditor() {
    var d = ui.draft;
    $('#editorTitle').textContent = ui.editingId ? '编辑设备' : '添加设备';

    var html = '';
    html += '<div class="field">' +
      '<label class="field-label" for="fName">设备名称</label>' +
      '<input id="fName" class="input" maxlength="20" placeholder="例如：充电宝" value="' + esc(d.name) + '">' +
      '</div>';

    html += '<div class="field"><div class="field-label">快捷模板</div><div class="chips" id="tplChips">' +
      TEMPLATES.map(function (t) {
        return '<button data-tpl="' + t.key + '"' + (d.templateKey === t.key ? ' class="is-on"' : '') + '>' + t.emoji + ' ' + t.name + '</button>';
      }).join('') +
      '</div></div>';

    html += '<div class="field"><div class="field-label">类型</div>' +
      segHTML('type', [['recurring', '定期充电'], ['onetime', '一次性']], d.type) +
      '</div>';

    if (d.type === 'recurring') {
      html += '<div class="field"><div class="field-label">间隔</div>' +
        '<div class="row-gap">' +
          '<input id="fInterval" class="input num" type="number" min="1" max="365" value="' + d.intervalValue + '">' +
          segHTML('unit', [['day', '天'], ['week', '周'], ['month', '月']], d.unit, 'sm') +
        '</div>' +
        '<p class="field-hint">例如：每 14 天提醒一次</p>' +
        '</div>';
    } else {
      html += '<div class="field"><div class="field-label">到期日</div>' +
        '<input id="fDue" class="input" type="date" value="' + (d.dueDate ? toDateInput(d.dueDate) : '') + '">' +
        '<p class="field-hint">一次性任务完成后会自动标记为已完成</p>' +
        '</div>';
    }

    html += '<div class="row-gap" style="margin-bottom:18px">' +
      '<label class="field half"><span class="field-label">提醒时间</span>' +
        '<input id="fTime" class="input" type="time" value="' + d.remindTime + '"></label>' +
      '<label class="field half"><span class="field-label">提前提醒</span>' +
        '<select id="fAdvance" class="input">' +
          [0, 1, 2, 3].map(function (n) {
            return '<option value="' + n + '"' + (d.advance === n ? ' selected' : '') + '>' + (n === 0 ? '当天' : n + ' 天前') + '</option>';
          }).join('') +
        '</select></label>' +
      '</div>';

    if (d.type === 'recurring') {
      html += '<div class="field"><div class="field-label">上次充电日期（用于校准）</div>' +
        '<input id="fLast" class="input" type="date" value="' + toDateInput(d.lastChargedAt) + '">' +
        '</div>';
    }

    html += '<div class="field"><div class="field-label">图标</div>' +
      '<div class="icon-row">' +
        '<div class="icon-preview" style="--preview-color:' + (d.color || 'var(--accent)') + '">' +
          (d.photo ? '<img src="' + d.photo + '" alt="">' : '<span>' + d.emoji + '</span>') +
        '</div>' +
        '<div class="icon-tools">' +
          '<button class="btn-tonal" data-act="pick-icon">📷 拍照 / 相册</button>' +
          (d.photo ? '<button class="btn-link" data-act="clear-photo">改用 emoji</button>' : '') +
        '</div>' +
      '</div>' +
      '<div class="emoji-grid">' +
        EMOJIS.map(function (e) {
          return '<button data-emoji="' + e + '"' + (!d.photo && d.emoji === e ? ' class="is-on"' : '') + '>' + e + '</button>';
        }).join('') +
      '</div>' +
      '</div>';

    html += '<div class="field"><div class="field-label">颜色</div><div class="swatches">' +
      COLORS.map(function (c) {
        var on = (d.color || COLORS[0]) === c;
        return '<button class="swatch' + (on ? ' is-on' : '') + '" data-color="' + c + '" style="--sw:' + c + '" aria-label="颜色 ' + c + '"></button>';
      }).join('') +
      '</div></div>';

    html += '<div class="panel"><div class="setting">' +
      '<div><div class="setting-label">充电自适应</div>' +
      '<div class="setting-desc">记录剩余电量后，自动微调下次间隔</div></div>' +
      '<label class="switch"><input type="checkbox" id="fAdaptive"' + (d.adaptive ? ' checked' : '') + '><i></i></label>' +
      '</div></div>';

    html += '<div class="field"><label class="field-label" for="fNote">备注（可选）</label>' +
      '<textarea id="fNote" class="input textarea" rows="2" placeholder="例如：放在书桌抽屉里">' + esc(d.note) + '</textarea></div>';

    $('#editorBody').innerHTML = html;
  }

  function segHTML(key, opts, current, cls) {
    return '<div class="segmented' + (cls ? ' ' + cls : '') + '" data-seg="' + key + '">' +
      opts.map(function (o) {
        return '<button data-value="' + o[0] + '"' + (o[0] === current ? ' class="is-on"' : '') + '>' + o[1] + '</button>';
      }).join('') +
      '</div>';
  }

  function applyTemplate(key) {
    var tpl = null;
    TEMPLATES.forEach(function (t) { if (t.key === key) tpl = t; });
    if (!tpl) return;
    ui.draft.templateKey = key;
    if (tpl.key !== 'other') ui.draft.name = tpl.name;
    ui.draft.emoji = tpl.emoji;
    ui.draft.photo = null;
    ui.draft.intervalValue = tpl.interval;
    ui.draft.unit = tpl.unit;
    renderEditor();
  }

  function saveEditor() {
    var d = ui.draft;
    d.name = (d.name || '').trim();
    if (!d.name) { toast('先给设备起个名字'); return; }
    if (d.type === 'onetime' && !d.dueDate) { toast('选一个到期日'); return; }

    if (ui.editingId) {
      for (var i = 0; i < state.devices.length; i++) {
        if (state.devices[i].id === ui.editingId) state.devices[i] = d;
      }
    } else {
      state.devices.push(d);
    }
    save();
    renderHome();
    back();
    if (navStack.indexOf($('#screenDetail')) >= 0) renderDetail();
    toast('已保存');
  }

  /* ---------------- 充电 / 推迟 ---------------- */
  function suggestInterval(d, pct) {
    if (d.type !== 'recurring' || !d.adaptive || pct == null) return null;
    var base = intervalDays(d);
    var next = base;
    if (pct >= 40) next = Math.max(base + 1, Math.round(base * 1.15));
    else if (pct <= 15) next = Math.min(base - 1, Math.round(base * 0.85));
    next = Math.max(1, next);
    if (next === base) return null;
    return { from: base, to: next };
  }

  function setIntervalDays(d, days) {
    if (d.unit === 'day') { d.intervalValue = days; return; }
    var m = unitDays(d.unit);
    if (days % m === 0) d.intervalValue = days / m;
    else { d.unit = 'day'; d.intervalValue = days; }
  }

  function logCharge(id, pct) {
    var d = getDevice(id);
    if (!d) return;
    var now = Date.now();
    var gap = dayDiff(now, d.lastChargedAt);
    d.history.push({ at: now, pct: pct, gap: gap });
    d.lastChargedAt = now;
    d.snoozeDays = 0;

    var change = suggestInterval(d, pct);
    if (change) setIntervalDays(d, change.to);
    if (d.type === 'onetime') { d.completed = true; d.enabled = false; }

    save();
    renderHome();
    if (ui.detailId === id) renderDetail();

    if (change) toast('已记录 · 间隔从 ' + change.from + ' 天调整为 ' + change.to + ' 天');
    else if (d.type === 'onetime') toast('已完成');
    else toast('已记录，下次提醒已重新安排');
  }

  function snooze(id, days) {
    var d = getDevice(id);
    if (!d) return;
    d.snoozeDays = (d.snoozeDays || 0) + days;
    save();
    renderHome();
    if (ui.detailId === id) renderDetail();
    toast('已推迟 ' + days + ' 天');
  }

  function deleteDevice(id) {
    var d = getDevice(id);
    if (!d) return;
    openDialog({
      title: '删除「' + d.name + '」',
      text: '设备信息和充电记录都会被删除，无法恢复。',
      ok: '删除',
      danger: true
    }).then(function (ok) {
      if (!ok) return;
      state.devices = state.devices.filter(function (x) { return x.id !== id; });
      save();
      renderHome();
      if (navStack.indexOf($('#screenDetail')) >= 0) back();
      toast('已删除');
    });
  }

  /* ---------------- 记录充电面板 ---------------- */
  function openCharge(id) {
    var d = getDevice(id);
    if (!d) return;
    chargeCtx.id = id;
    chargeCtx.pct = 25;
    $('#chargeSub').textContent = d.name + ' · 上次 ' + fmtMD(new Date(d.lastChargedAt));
    $('#chargeRange').value = 25;
    updatePctUI();
    openSheet($('#sheetCharge'));
  }

  function updatePctUI() {
    var d = getDevice(chargeCtx.id);
    var pct = chargeCtx.pct;
    var display = $('#pctDisplay');
    var hint = $('#adaptiveHint');
    var unit = display.querySelector('span');
    if (unit) unit.style.display = pct == null ? 'none' : '';

    if (pct == null) {
      display.classList.add('is-none');
      $('#chargePct').textContent = '不记录';
      hint.textContent = '不记录剩余电量，下次间隔保持 ' + (d ? intervalLabel(d) : '') + '。';
    } else {
      display.classList.remove('is-none');
      $('#chargePct').textContent = pct;
      var change = d ? suggestInterval(d, pct) : null;
      if (!d || d.type !== 'recurring' || !d.adaptive) {
        hint.textContent = '充电自适应已关闭，下次间隔保持 ' + (d ? intervalLabel(d) : '') + '。';
      } else if (change) {
        hint.textContent = '剩余 ' + pct + '%：' + (pct >= 40 ? '电量还很足，可以拉长间隔' : '差点放空，建议缩短间隔') +
          '，下次间隔 ' + change.from + ' → ' + change.to + ' 天。';
      } else {
        hint.textContent = '剩余 ' + pct + '%：处于理想区间（20–30%），下次间隔保持 ' + intervalLabel(d) + '。';
      }
    }

    $$('#chargeChips button').forEach(function (b) {
      var v = b.dataset.pct;
      var on = (v === 'none' && pct == null) || (v !== 'none' && pct != null && Number(v) === pct);
      b.classList.toggle('is-on', on);
    });
  }

  /* ---------------- 设置 ---------------- */
  function renderSettings() {
    var s = state.settings;
    var html = '';

    html += '<div class="group"><div class="group-title">外观</div><div class="panel">';
    html += '<div class="setting col"><div class="setting-label">主题</div>' +
      segHTML('theme', [['auto', '跟随系统'], ['light', '浅色'], ['dark', '深色']], s.theme) + '</div>';
    html += '<div class="setting col"><div class="setting-label">配色</div>' +
      '<div class="swatches" data-swatch="palette">' +
        Object.keys(PALETTES).map(function (k) {
          return '<button class="swatch' + (s.palette === k ? ' is-on' : '') + '" data-value="' + k + '" style="--sw:' + PALETTES[k].accent + '" aria-label="' + PALETTES[k].name + '"></button>';
        }).join('') +
      '</div></div>';
    html += '<div class="setting col"><div class="setting-label">背景</div>' +
      segHTML('bgType', [['solid', '纯色'], ['gradient', '渐变'], ['image', '图片']], s.bgType) + '</div>';

    if (s.bgType === 'image') {
      html += '<div class="setting col">' +
        '<button class="btn-tonal wide" data-act="pick-bg">' + (s.bgImage ? '更换背景图片' : '从相册选择图片') + '</button>' +
        (s.bgImage ? '<div class="bg-preview" style="background-image:url(\'' + s.bgImage + '\')"></div>' : '<p class="field-hint">选一张照片，模糊和暗化会自动作用在背景上。</p>') +
        '<div class="slider-row"><span>模糊</span><input type="range" min="0" max="25" step="1" value="' + s.blur + '" data-range="blur"><b>' + s.blur + '</b></div>' +
        '<div class="slider-row"><span>暗化</span><input type="range" min="0" max="70" step="5" value="' + s.dim + '" data-range="dim"><b>' + s.dim + '%</b></div>' +
        '</div>';
    }

    html += '<div class="setting"><div><div class="setting-label">动态取色</div>' +
      '<div class="setting-desc">从背景图片提取主色（Android 12+）</div></div>' +
      '<label class="switch"><input type="checkbox" data-check="dynamicColor"' + (s.dynamicColor ? ' checked' : '') + '><i></i></label></div>';
    html += '</div></div>';

    html += '<div class="group"><div class="group-title">提醒</div><div class="panel">';
    html += '<div class="setting"><div class="setting-label">默认间隔</div><div class="row-gap tight">' +
      '<input class="input num" type="number" min="1" max="365" value="' + s.defaultIntervalValue + '" data-number="defaultIntervalValue">' +
      segHTML('defaultIntervalUnit', [['day', '天'], ['week', '周'], ['month', '月']], s.defaultIntervalUnit, 'sm') +
      '</div></div>';
    html += '<div class="setting"><div class="setting-label">默认提醒时间</div>' +
      '<input class="input time" type="time" value="' + s.defaultRemindTime + '" data-time="defaultRemindTime"></div>';
    html += '<div class="setting"><div class="setting-label">默认提前提醒</div>' +
      '<select class="input select" data-select="defaultAdvance">' +
        [0, 1, 2, 3].map(function (n) {
          return '<option value="' + n + '"' + (s.defaultAdvance === n ? ' selected' : '') + '>' + (n === 0 ? '当天' : n + ' 天前') + '</option>';
        }).join('') +
      '</select></div>';
    html += '<div class="setting"><div class="setting-label">免打扰</div><div class="row-gap tight">' +
      '<input class="input time" type="time" value="' + s.dndStart + '" data-time="dndStart">' +
      '<span class="dash">–</span>' +
      '<input class="input time" type="time" value="' + s.dndEnd + '" data-time="dndEnd">' +
      '</div></div>';
    html += '<div class="setting col"><div class="setting-label">提醒强度</div>' +
      segHTML('strength', [['notification', '普通通知'], ['alarm', '闹钟式']], s.strength) + '</div>';
    html += '<div class="setting"><div><div class="setting-label">逾期每天提醒</div>' +
      '<div class="setting-desc">逾期后每天同一时间再提醒一次</div></div>' +
      '<label class="switch"><input type="checkbox" data-check="dailyOverdue"' + (s.dailyOverdue ? ' checked' : '') + '><i></i></label></div>';
    html += '</div></div>';

    html += '<div class="group"><div class="group-title">权限自检</div><div class="panel">';
    html += permRow('notification', '通知权限', '接收到期提醒', s.perms.notification);
    html += permRow('exactAlarm', '精确闹钟', '保证到点准时提醒', s.perms.exactAlarm);
    html += permRow('battery', '电池优化', '允许后台被唤醒', s.perms.battery);
    html += '</div>' +
      '<button class="btn-tonal wide" data-act="test-notif" style="margin-top:10px">发送一条测试通知</button></div>';

    html += '<div class="group"><div class="group-title">数据</div><div class="panel">' +
      '<button class="row-btn" data-act="export">导出备份（JSON）</button>' +
      '<button class="row-btn" data-act="import">导入备份</button>' +
      '<button class="row-btn danger" data-act="clear">清空所有数据</button>' +
      '</div><p class="foot-note">数据只存在本机，不联网。卸载前记得导出备份。</p></div>';

    html += '<div class="group"><div class="group-title">关于</div><div class="panel">' +
      '<div class="setting"><div class="setting-label">版本</div><div class="value">1.0.0 · 原型</div></div>' +
      '<button class="row-btn" data-act="license">开源许可</button>' +
      '<button class="row-btn" data-act="privacy">隐私说明</button>' +
      '</div><p class="foot-note">充电提醒 · 本地提醒，不收集任何数据。</p></div>';

    $('#settingsBody').innerHTML = html;
  }

  function permRow(key, title, desc, ok) {
    return '<div class="setting"><div><div class="setting-label">' + title + '</div>' +
      '<div class="setting-desc">' + desc + '</div></div>' +
      '<button class="perm ' + (ok ? 'ok' : 'no') + '" data-perm="' + key + '">' + (ok ? '已开启' : '去开启') + '</button></div>';
  }

  function setSetting(key, val) {
    var strKeys = ['theme', 'palette', 'bgType', 'defaultIntervalUnit', 'strength'];
    if (strKeys.indexOf(key) >= 0) state.settings[key] = val;
    else state.settings[key] = Number(val);
    save();
  }

  /* ---------------- 导航 ---------------- */
  function go(el) {
    navStack.push(el);
    el.classList.add('is-active');
    el.scrollTop = 0;
    updateChrome();
  }
  function back() {
    var cur = navStack.pop();
    if (cur) cur.classList.remove('is-active');
    updateChrome();
  }
  function updateChrome() {
    $('#btnAdd').classList.toggle('is-hidden', navStack.length > 0);
    var onEditor = navStack[navStack.length - 1] === $('#screenEditor');
    $('#editorActions').classList.toggle('is-hidden', !onEditor);
  }
  function openDetail(id) {
    ui.detailId = id;
    renderDetail();
    go($('#screenDetail'));
  }
  function openEditor(id) {
    if (id) {
      var d = getDevice(id);
      if (!d) return;
      ui.editingId = id;
      ui.draft = JSON.parse(JSON.stringify(d));
    } else {
      var s = state.settings;
      ui.editingId = null;
      ui.draft = makeDevice({
        name: '',
        intervalValue: s.defaultIntervalValue,
        unit: s.defaultIntervalUnit,
        remindTime: s.defaultRemindTime,
        advance: s.defaultAdvance,
        adaptive: true
      });
    }
    renderEditor();
    go($('#screenEditor'));
  }
  function openSettings() {
    renderSettings();
    go($('#screenSettings'));
  }

  /* ---------------- 弹层 ---------------- */
  function openSheet(el) {
    el.classList.add('is-open');
    $('#scrim').classList.add('is-on');
  }
  function closeSheet() {
    $$('.sheet').forEach(function (s) { s.classList.remove('is-open'); });
    if (!$('#dialog').classList.contains('is-open')) $('#scrim').classList.remove('is-on');
  }
  function openDialog(opts) {
    return new Promise(function (resolve) {
      dialogResolve = resolve;
      $('#dialogTitle').textContent = opts.title || '';
      $('#dialogText').textContent = opts.text || '';
      var cancel = $('#dialogCancel');
      cancel.style.display = opts.single ? 'none' : '';
      cancel.textContent = opts.cancel || '取消';
      var ok = $('#dialogOk');
      ok.textContent = opts.ok || '确定';
      ok.className = opts.danger ? 'danger' : 'primary';
      $('#dialog').classList.add('is-open');
      $('#scrim').classList.add('is-on');
    });
  }
  function closeDialog(val) {
    $('#dialog').classList.remove('is-open');
    if (!$('#sheetCharge').classList.contains('is-open')) $('#scrim').classList.remove('is-on');
    if (dialogResolve) {
      var fn = dialogResolve;
      dialogResolve = null;
      fn(val);
    }
  }
  function toast(msg) {
    var el = $('#toast');
    el.textContent = msg;
    el.classList.add('is-on');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('is-on'); }, 2600);
  }

  /* ---------------- 通知 ---------------- */
  function showNotification(d) {
    var layer = $('#notificationLayer');
    var el = document.createElement('div');
    el.className = 'notif';
    var st = statusOf(d);
    el.innerHTML =
      '<div class="notif-head"><span class="notif-app"><i>⚡</i>充电提醒</span><span class="notif-time">现在</span></div>' +
      '<div class="notif-title">该给「' + esc(d.name) + '」充电了</div>' +
      '<div class="notif-body">' + st.text + ' · 每 ' + intervalLabel(d) + '提醒一次</div>' +
      '<div class="notif-actions"><button data-nact="done">已充电</button><button data-nact="snooze">推迟 3 天</button></div>';
    layer.appendChild(el);
    requestAnimationFrame(function () { el.classList.add('is-in'); });

    var closed = false;
    function close() {
      if (closed) return;
      closed = true;
      el.classList.remove('is-in');
      setTimeout(function () { el.remove(); }, 420);
    }
    el.addEventListener('click', function (e) {
      var b = e.target.closest('[data-nact]');
      if (!b) return;
      if (b.dataset.nact === 'done') logCharge(d.id, null);
      else snooze(d.id, 3);
      close();
    });
    setTimeout(close, 7000);
  }

  function testNotification() {
    var list = sortedDevices();
    var target = list[0];
    if (!target) { toast('先添加一台设备'); return; }
    showNotification(target);
  }

  /* ---------------- 图片处理 ---------------- */
  function readImage(file, opts, cb) {
    var reader = new FileReader();
    reader.onload = function () {
      var img = new Image();
      img.onload = function () {
        var maxSide = opts.maxSide || 1400;
        var w = img.width, h = img.height;
        var sx = 0, sy = 0, sw = w, sh = h;
        if (opts.square) {
          var side = Math.min(w, h);
          sx = (w - side) / 2; sy = (h - side) / 2; sw = sh = side;
          w = h = Math.min(maxSide, side);
        } else if (Math.max(w, h) > maxSide) {
          var k = maxSide / Math.max(w, h);
          w = Math.round(w * k); h = Math.round(h * k);
        }
        var c = document.createElement('canvas');
        c.width = Math.max(1, w); c.height = Math.max(1, h);
        var ctx = c.getContext('2d');
        ctx.drawImage(img, sx, sy, sw, sh, 0, 0, c.width, c.height);
        cb(c.toDataURL('image/jpeg', 0.85), c);
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  }

  function rgbToHsl(r, g, b) {
    r /= 255; g /= 255; b /= 255;
    var max = Math.max(r, g, b), min = Math.min(r, g, b);
    var h = 0, s = 0, l = (max + min) / 2;
    var d = max - min;
    if (d) {
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
      else if (max === g) h = ((b - r) / d + 2) / 6;
      else h = ((r - g) / d + 4) / 6;
    }
    return [h, s, l];
  }
  function hslToHex(h, s, l) {
    function f(p, q, t) {
      if (t < 0) t += 1;
      if (t > 1) t -= 1;
      if (t < 1 / 6) return p + (q - p) * 6 * t;
      if (t < 1 / 2) return q;
      if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
      return p;
    }
    var r, g, b;
    if (s === 0) { r = g = b = l; }
    else {
      var q = l < 0.5 ? l * (1 + s) : l + s - l * s;
      var p = 2 * l - q;
      r = f(p, q, h + 1 / 3); g = f(p, q, h); b = f(p, q, h - 1 / 3);
    }
    function hex(v) { var x = Math.round(v * 255).toString(16); return x.length === 1 ? '0' + x : x; }
    return '#' + hex(r) + hex(g) + hex(b);
  }
  /* 调试用：生成一张示例背景，验证换背景与动态取色 */
  function demoBackground() {
    var c = document.createElement('canvas');
    c.width = 900; c.height = 1600;
    var ctx = c.getContext('2d');
    var g = ctx.createLinearGradient(0, 0, 900, 1600);
    g.addColorStop(0, '#0E3B3A');
    g.addColorStop(0.42, '#2A7F6F');
    g.addColorStop(0.74, '#C9A227');
    g.addColorStop(1, '#5B2A1E');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 900, 1600);
    ctx.globalAlpha = 0.16;
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath(); ctx.arc(720, 220, 260, 0, Math.PI * 2); ctx.fill();
    ctx.beginPath(); ctx.arc(120, 1280, 330, 0, Math.PI * 2); ctx.fill();
    ctx.globalAlpha = 1;
    state.settings.bgImage = c.toDataURL('image/jpeg', 0.86);
    state.settings.bgType = 'image';
    state.settings.accentFromImage = accentFromCanvas(c);
    applyAppearance();
  }

  function accentFromCanvas(canvas) {
    var c = document.createElement('canvas');
    c.width = 32; c.height = 32;
    var ctx = c.getContext('2d');
    ctx.drawImage(canvas, 0, 0, 32, 32);
    var data = ctx.getImageData(0, 0, 32, 32).data;

    /* 取"最显眼的那一族颜色"，而不是整张图的平均值（平均值容易变成脏灰） */
    var buckets = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    var rSum = 0, gSum = 0, bSum = 0, n = 0;

    for (var i = 0; i < data.length; i += 4) {
      if (data[i + 3] < 128) continue;
      var r = data[i], g = data[i + 1], b = data[i + 2];
      rSum += r; gSum += g; bSum += b; n++;
      var hsl = rgbToHsl(r, g, b);
      if (hsl[1] < 0.3 || hsl[2] < 0.12 || hsl[2] > 0.9) continue;
      var idx = Math.floor(hsl[0] * 12) % 12;
      buckets[idx] += hsl[1] * (1 - Math.abs(hsl[2] - 0.5));
    }

    var bestIdx = -1, bestVal = 0;
    for (var k = 0; k < 12; k++) {
      if (buckets[k] > bestVal) { bestVal = buckets[k]; bestIdx = k; }
    }

    var hue, sat, lit;
    if (bestIdx >= 0) {
      hue = ((bestIdx + 0.5) / 12) * 360;
      sat = 0.52;
      lit = 0.43;
    } else if (n) {
      var avg = rgbToHsl(rSum / n, gSum / n, bSum / n);
      hue = avg[0] * 360;
      sat = clamp(avg[1], 0.42, 0.7);
      lit = clamp(avg[2], 0.36, 0.52);
    } else {
      return null;
    }

    var deg = Math.round(hue);
    return {
      accent: hslToHex(hue / 360, sat, lit),
      accent2: hslToHex(hue / 360, clamp(sat + 0.16, 0, 0.9), clamp(lit + 0.18, 0, 0.72)),
      soft: 'hsla(' + deg + ', ' + Math.round(sat * 100) + '%, ' + Math.round(lit * 100) + '%, 0.12)',
      glow: 'hsla(' + deg + ', ' + Math.round(sat * 100) + '%, ' + Math.round(lit * 100) + '%, 0.26)'
    };
  }

  /* ---------------- 导入导出 ---------------- */
  function exportData() {
    var blob = new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = 'charge-reminder-backup-' + new Date().toISOString().slice(0, 10) + '.json';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
    toast('备份已导出');
  }

  function importData(file) {
    var reader = new FileReader();
    reader.onload = function () {
      var data;
      try { data = JSON.parse(reader.result); } catch (e) { toast('文件格式不对'); return; }
      if (!data || !Array.isArray(data.devices)) { toast('文件格式不对'); return; }
      openDialog({
        title: '导入备份',
        text: '将用备份覆盖当前的 ' + state.devices.length + ' 台设备，确定吗？',
        ok: '导入'
      }).then(function (ok) {
        if (!ok) return;
        state.devices = data.devices.map(function (d) { return makeDevice(d); });
        state.settings = Object.assign({}, DEFAULT_SETTINGS, data.settings || {});
        state.settings.perms = Object.assign({}, DEFAULT_SETTINGS.perms, (data.settings && data.settings.perms) || {});
        save();
        applyAppearance();
        renderHome();
        renderSettings();
        toast('已导入 ' + state.devices.length + ' 台设备');
      });
    };
    reader.readAsText(file);
  }

  /* ---------------- 事件绑定 ---------------- */
  function bind() {
    $('#btnSettings').addEventListener('click', openSettings);
    $('#btnAdd').addEventListener('click', function () { openEditor(null); });
    $('#btnEdit').addEventListener('click', function () { if (ui.detailId) openEditor(ui.detailId); });
    $('#btnSave').addEventListener('click', saveEditor);
    $$('[data-back]').forEach(function (b) { b.addEventListener('click', back); });

    /* 首页列表 */
    var list = $('#deviceList');
    list.addEventListener('click', function (e) {
      if (e.target.closest('[data-noopen]')) return;
      var act = e.target.closest('[data-action]');
      if (!act) return;
      if (act.dataset.action === 'open') openDetail(act.dataset.id);
      else if (act.dataset.action === 'charge') openCharge(act.dataset.id);
      else if (act.dataset.action === 'add') openEditor(null);
    });
    list.addEventListener('change', function (e) {
      var cb = e.target.closest('[data-toggle]');
      if (!cb) return;
      var d = getDevice(cb.dataset.toggle);
      if (!d) return;
      d.enabled = cb.checked;
      save();
      renderHome();
    });

    /* 详情 */
    $('#detailBody').addEventListener('click', function (e) {
      var b = e.target.closest('[data-action]');
      if (!b) return;
      if (b.dataset.action === 'charge') openCharge(ui.detailId);
      else if (b.dataset.action === 'delete') deleteDevice(ui.detailId);
    });

    /* 编辑 */
    var editor = $('#editorBody');
    editor.addEventListener('input', function (e) {
      var t = e.target;
      if (t.id === 'fName') ui.draft.name = t.value;
      else if (t.id === 'fInterval') ui.draft.intervalValue = Math.max(1, Number(t.value) || 1);
      else if (t.id === 'fNote') ui.draft.note = t.value;
    });
    editor.addEventListener('change', function (e) {
      var t = e.target;
      if (t.id === 'fTime') ui.draft.remindTime = t.value;
      else if (t.id === 'fAdvance') ui.draft.advance = Number(t.value);
      else if (t.id === 'fDue') ui.draft.dueDate = new Date(t.value + 'T09:00:00').getTime();
      else if (t.id === 'fLast') ui.draft.lastChargedAt = new Date(t.value + 'T09:00:00').getTime();
      else if (t.id === 'fAdaptive') ui.draft.adaptive = t.checked;
    });
    editor.addEventListener('click', function (e) {
      var tpl = e.target.closest('[data-tpl]');
      if (tpl) { applyTemplate(tpl.dataset.tpl); return; }

      var em = e.target.closest('[data-emoji]');
      if (em) { ui.draft.emoji = em.dataset.emoji; ui.draft.photo = null; renderEditor(); return; }

      var sw = e.target.closest('[data-color]');
      if (sw) { ui.draft.color = sw.dataset.color; renderEditor(); return; }

      var seg = e.target.closest('[data-seg] button');
      if (seg) {
        var key = seg.parentElement.dataset.seg;
        if (key === 'type') {
          ui.draft.type = seg.dataset.value;
          if (ui.draft.type === 'onetime' && !ui.draft.dueDate) {
            var dd = new Date();
            dd.setDate(dd.getDate() + 7);
            ui.draft.dueDate = dd.getTime();
          }
          renderEditor();
        } else if (key === 'unit') {
          ui.draft.unit = seg.dataset.value;
          renderEditor();
        }
        return;
      }

      var act = e.target.closest('[data-act]');
      if (!act) return;
      if (act.dataset.act === 'pick-icon') $('#iconFileInput').click();
      else if (act.dataset.act === 'clear-photo') { ui.draft.photo = null; renderEditor(); }
    });

    /* 记录充电面板 */
    $('#chargeRange').addEventListener('input', function (e) {
      chargeCtx.pct = Number(e.target.value);
      updatePctUI();
    });
    $('#chargeChips').addEventListener('click', function (e) {
      var b = e.target.closest('[data-pct]');
      if (!b) return;
      chargeCtx.pct = b.dataset.pct === 'none' ? null : Number(b.dataset.pct);
      if (chargeCtx.pct != null) $('#chargeRange').value = chargeCtx.pct;
      updatePctUI();
    });
    $('#btnConfirmCharge').addEventListener('click', function () {
      var id = chargeCtx.id;
      var pct = chargeCtx.pct;
      closeSheet();
      logCharge(id, pct);
    });
    $$('[data-close-sheet]').forEach(function (b) { b.addEventListener('click', closeSheet); });

    /* 设置 */
    var settings = $('#settingsBody');
    settings.addEventListener('click', function (e) {
      var seg = e.target.closest('[data-seg] button');
      if (seg) {
        var key = seg.parentElement.dataset.seg;
        setSetting(key, seg.dataset.value);
        applyAppearance();
        renderSettings();
        return;
      }
      var sw = e.target.closest('[data-swatch] button');
      if (sw) {
        setSetting(sw.closest('[data-swatch]').dataset.swatch, sw.dataset.value);
        applyAppearance();
        renderSettings();
        return;
      }
      var perm = e.target.closest('[data-perm]');
      if (perm) {
        var p = perm.dataset.perm;
        state.settings.perms[p] = !state.settings.perms[p];
        save();
        renderSettings();
        toast('原型模拟：真机上会跳到系统设置页');
        return;
      }
      var act = e.target.closest('[data-act]');
      if (!act) return;
      var a = act.dataset.act;
      if (a === 'pick-bg') $('#bgFileInput').click();
      else if (a === 'test-notif') testNotification();
      else if (a === 'export') exportData();
      else if (a === 'import') $('#importInput').click();
      else if (a === 'clear') {
        openDialog({
          title: '清空所有数据',
          text: '会删除全部设备与充电记录，且无法恢复。',
          ok: '清空',
          danger: true
        }).then(function (ok) {
          if (!ok) return;
          state.devices = [];
          save();
          renderHome();
          toast('已清空');
        });
      } else if (a === 'license') {
        openDialog({
          title: '开源许可',
          text: '原型基于 MIT / Apache-2.0 / GPL-3.0 等开源项目的能力设计，正式版会在此列出全部依赖与许可证。',
          ok: '知道了',
          single: true
        });
      } else if (a === 'privacy') {
        openDialog({
          title: '隐私说明',
          text: '所有数据只保存在你的手机本地。不联网、不收集、不上传、没有账号。',
          ok: '知道了',
          single: true
        });
      }
    });
    settings.addEventListener('input', function (e) {
      var r = e.target.closest('[data-range]');
      if (!r) return;
      var key = r.dataset.range;
      state.settings[key] = Number(r.value);
      var b = r.parentElement.querySelector('b');
      if (b) b.textContent = key === 'dim' ? r.value + '%' : r.value;
      applyAppearance();
      save();
    });
    settings.addEventListener('change', function (e) {
      var t = e.target;
      if (t.dataset.check) {
        state.settings[t.dataset.check] = t.checked;
        save();
        applyAppearance();
      } else if (t.dataset.number) {
        state.settings[t.dataset.number] = Math.max(1, Number(t.value) || 1);
        save();
      } else if (t.dataset.time) {
        state.settings[t.dataset.time] = t.value;
        save();
      } else if (t.dataset.select) {
        state.settings[t.dataset.select] = Number(t.value);
        save();
      }
    });

    /* 弹层 */
    $('#scrim').addEventListener('click', function () {
      if ($('#dialog').classList.contains('is-open')) closeDialog(false);
      else closeSheet();
    });
    $('#dialogOk').addEventListener('click', function () { closeDialog(true); });
    $('#dialogCancel').addEventListener('click', function () { closeDialog(false); });

    /* 文件选择 */
    $('#bgFileInput').addEventListener('change', function (e) {
      var f = e.target.files && e.target.files[0];
      if (!f) return;
      readImage(f, { maxSide: 1600 }, function (dataUrl, canvas) {
        state.settings.bgImage = dataUrl;
        state.settings.bgType = 'image';
        state.settings.accentFromImage = accentFromCanvas(canvas);
        save();
        applyAppearance();
        renderSettings();
        toast('背景已更新');
      });
      e.target.value = '';
    });
    $('#iconFileInput').addEventListener('change', function (e) {
      var f = e.target.files && e.target.files[0];
      if (!f) return;
      readImage(f, { maxSide: 320, square: true }, function (dataUrl) {
        ui.draft.photo = dataUrl;
        renderEditor();
        toast('图标已更新');
      });
      e.target.value = '';
    });
    $('#importInput').addEventListener('change', function (e) {
      var f = e.target.files && e.target.files[0];
      if (f) importData(f);
      e.target.value = '';
    });

    /* 系统深色模式变化 */
    var mq = matchMedia('(prefers-color-scheme: dark)');
    var onChange = function () { if (state.settings.theme === 'auto') applyAppearance(); };
    if (mq.addEventListener) mq.addEventListener('change', onChange);
    else if (mq.addListener) mq.addListener(onChange);
  }

  /* ---------------- 启动 ---------------- */
  function tick() {
    var d = new Date();
    $('#sbTime').textContent = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  function fitPhone() {
    var phone = $('#phone');
    if (window.innerWidth <= 560) { phone.style.zoom = ''; return; }
    var scale = Math.min(1, (window.innerHeight - 108) / 844, (window.innerWidth - 60) / 390);
    phone.style.zoom = String(Math.max(0.55, scale));
  }

  function handleHash() {
    var h = location.hash.replace('#', '');
    if (h === 'settings') openSettings();
    else if (h === 'detail') {
      var first = sortedDevices()[0];
      if (first) openDetail(first.id);
    } else if (h === 'editor') {
      openEditor(null);
    } else if (h === 'dark') {
      state.settings.theme = 'dark';
      applyAppearance();
    } else if (h === 'charge') {
      var cand = sortedDevices().filter(function (d) { return d.type === 'recurring' && d.adaptive; })[0] || sortedDevices()[0];
      if (cand) openCharge(cand.id);
    } else if (h === 'photo') {
      demoBackground();
    }
  }

  function init() {
    bind();
    applyAppearance();
    renderHome();
    tick();
    setInterval(tick, 30000);
    fitPhone();
    window.addEventListener('resize', fitPhone);
    handleHash();
    if (location.search.indexOf('debug') >= 0) {
      var screen = $('#screenHome');
      var card = $('#deviceList .device-card');
      var badge = document.createElement('div');
      badge.style.cssText = 'position:fixed;left:0;top:0;z-index:99999;background:#e11d48;color:#fff;' +
        'font:11px/1.4 monospace;padding:4px 6px;white-space:pre;max-width:100%';
      badge.textContent =
        'vw ' + window.innerWidth + 'x' + window.innerHeight + '  dpr ' + window.devicePixelRatio + '\n' +
        'phone ' + $('#phone').getBoundingClientRect().width.toFixed(1) +
        '  app ' + $('#app').getBoundingClientRect().width.toFixed(1) +
        '  screen ' + screen.getBoundingClientRect().width.toFixed(1) +
        '  padR ' + getComputedStyle(screen).paddingRight +
        '  card ' + (card ? card.getBoundingClientRect().width.toFixed(1) : '-');
      document.body.appendChild(badge);
    }
  }

  init();
})();
