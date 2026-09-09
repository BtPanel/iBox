const icDir = `data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23F5A623'><path d='M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'/></svg>`;
const icFile = `data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23717970'><path d='M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z'/></svg>`;
let currentRoot = '';
let currentFile = '';
let currentParent = '';
let longPressTimer = null;
let longPressTriggered = false;
let pendingDelFolder = null;
let warnToastTimer = null;
let danmakuMode = 1;
let danmakuSize = 25;
let dialogClosing = false;
let manageState = null;
let manageLoaded = false;
let editingSubscription = null;
let pendingSubscriptionDelete = null;

const subscriptionGroups = [
    { key: 'vod', title: '点播订阅', type: 0 },
    { key: 'live', title: '直播订阅', type: 1 },
    { key: 'wall', title: '壁纸订阅', type: 2 },
    { key: 'epg', title: 'EPG 节目单', epg: true }
];

const settingGroups = [
    { title: '通用', items: [
        { key: 'wallBlur', label: '壁纸模糊', type: 'switch' },
        { key: 'incognito', label: '无痕模式', type: 'switch' },
        { key: 'bootAutoStart', label: '开机自动进入应用', type: 'switch' },
        { key: 'bootStartMode', label: '启动页面', type: 'select', options: [[1, '点播'], [2, '直播'], [3, '观看记录']] },
        { key: 'autoFallbackLine', label: '线路失败自动切换', type: 'switch' },
        { key: 'autoFallbackSource', label: '站点失败自动切换', type: 'switch' },
        { key: 'fontScale', label: '界面字号', type: 'select', options: [[0, '标准'], [1, '大'], [2, '特大']] },
        { key: 'highContrast', label: '高对比度', type: 'switch' },
        { key: 'reduceMotion', label: '减少动画', type: 'switch' },
        { key: 'themeColor', label: '主题色', type: 'color', options: [[-1, '琥珀', '#F2B84B'], [-14122271, '蓝色', '#2882E1'], [-16732991, '青色', '#00ACC1'], [-12345273, '绿色', '#43A047'], [-1754827, '红色', '#E53935']] },
        { key: 'focusColor', label: '焦点色', type: 'color', options: [[-1, '白色', '#FFFFFF'], [-14122271, '蓝色', '#2882E1'], [-16732991, '青色', '#00ACC1'], [-12345273, '绿色', '#43A047'], [-1754827, '红色', '#E53935'], [-870325, '琥珀', '#F2B84B']] },
        { key: 'searchThreads', label: '搜索线程', type: 'number', min: 1, max: 20, step: 1 },
        { key: 'adblock', label: '广告过滤', type: 'switch' },
        { key: 'configNotice', label: '订阅公告', type: 'switch' }
    ]},
    { title: '播放', items: [
        { key: 'playerEngine', label: '播放内核', type: 'select', options: [[0, 'ExoPlayer'], [1, 'MPV']] },
        { key: 'playerMpvGpuNext', label: 'MPV GPU Next', type: 'switch' },
        { key: 'playerMpvVulkan', label: 'MPV Vulkan', type: 'switch' },
        { key: 'playerRender', label: '渲染方式', type: 'select', options: [[0, 'Surface'], [1, 'Texture']] },
        { key: 'playerSize', label: '播放器字号', type: 'select', options: [[0, '小'], [1, '较小'], [2, '标准'], [3, '大']] },
        { key: 'playerScale', label: '画面比例', type: 'select', options: [[0, '适应'], [1, '拉伸'], [2, '裁剪'], [3, '原始'], [4, '16:9']] },
        { key: 'playerBackground', label: '后台播放', type: 'select', options: [[0, '关闭'], [1, '开启'], [2, '画中画']] },
        { key: 'playerBuffer', label: '缓冲倍数', type: 'number', min: 1, max: 10, step: 1 },
        { key: 'userAgent', label: '默认 UA', type: 'text', placeholder: '留空使用应用默认值' }
    ]},
    { title: '解码', items: [
        { key: 'decodeTunnel', label: '隧道播放', type: 'switch' },
        { key: 'decodeAudioPassThrough', label: '音频直通', type: 'switch' },
        { key: 'decodeAudioPrefer', label: '优先音频解码器', type: 'switch' },
        { key: 'decodeVideoPrefer', label: '优先视频解码器', type: 'switch' },
        { key: 'decodeDolbyVision', label: '杜比视界输出', type: 'select', options: [[0, '自动'], [1, '强制支持'], [2, '视为不支持']] },
        { key: 'decodePreferAac', label: '优先 AAC 音频', type: 'switch' }
    ]},
    { title: '预加载', items: [
        { key: 'preloadEnabled', label: '启用预加载', type: 'switch' },
        { key: 'preloadNextEpisode', label: '预加载下一集', type: 'switch' },
        { key: 'preloadThreads', label: '下载线程', type: 'number', min: 1, max: 10, step: 1 },
        { key: 'preloadSizeMb', label: '缓存上限', type: 'number', min: 128, max: 4096, step: 128, suffix: 'MB' },
        { key: 'preloadTimeSeconds', label: '预加载时长', type: 'number', min: 20, max: 120, step: 10, suffix: '秒' }
    ]},
    { title: '直播', items: [
        { key: 'liveChange', label: '上下键换台', type: 'switch' },
        { key: 'liveInvert', label: '反转换台方向', type: 'switch' },
        { key: 'liveReconnectDelay', label: '断线重连', type: 'select', options: [[0, '关闭'], [1, '1 秒'], [3, '3 秒'], [5, '5 秒'], [10, '10 秒'], [20, '20 秒']] },
        { key: 'liveTimeout', label: '播放超时', type: 'select', options: [[5, '5 秒'], [10, '10 秒'], [15, '15 秒'], [20, '20 秒'], [25, '25 秒'], [30, '30 秒']] },
        { key: 'liveRtspTcp', label: 'RTSP 使用 TCP', type: 'switch' },
        { key: 'liveTimeshiftEnd', label: '时移结束后', type: 'select', options: [[0, '继续直播'], [1, '停在结尾']] },
        { key: 'liveRememberDecode', label: '记住解码方式', type: 'switch' },
        { key: 'liveShowClock', label: '显示时间', type: 'switch' },
        { key: 'liveShowNetwork', label: '显示网速', type: 'switch' },
        { key: 'liveShowBottomInfo', label: '显示底部信息', type: 'switch' },
        { key: 'liveShowBottomLogo', label: '底部显示台标', type: 'switch' },
        { key: 'liveShowChannelLogo', label: '频道列表显示台标', type: 'switch' },
        { key: 'liveShowChannelNumber', label: '频道列表显示序号', type: 'switch' },
        { key: 'liveChannelEpg', label: '频道节目预告', type: 'select', options: [[0, '关闭'], [1, '仅节目名'], [2, '节目名和进度']] },
        { key: 'liveCompactList', label: '紧凑频道列表', type: 'switch' },
        { key: 'livePanelOpacity', label: '菜单透明度', type: 'select', options: [[70, '70%'], [80, '80%'], [90, '90%'], [100, '100%']] },
        { key: 'liveEpgCacheHour', label: 'EPG 缓存时长', type: 'select', options: [[8, '8 小时'], [10, '10 小时'], [12, '12 小时'], [14, '14 小时'], [16, '16 小时'], [18, '18 小时'], [20, '20 小时'], [22, '22 小时']] },
        { key: 'liveEpgOffset', label: 'EPG 时间偏移', type: 'number', min: -12, max: 12, step: 1, suffix: '小时' },
        { key: 'liveEpgExternalCache', label: '缓存外部 EPG', type: 'switch' },
        { key: 'liveScale', label: '直播画面比例', type: 'select', options: [[0, '适应'], [1, '拉伸'], [2, '裁剪'], [3, '原始'], [4, '16:9']] }
    ]},
    { title: '弹幕', items: [
        { key: 'danmakuLoad', label: '加载弹幕', type: 'switch' },
        { key: 'danmakuAuto', label: '自动匹配弹幕', type: 'switch' },
        { key: 'danmakuSpiderFirst', label: '接口优先', type: 'switch' },
        { key: 'danmakuShow', label: '显示弹幕', type: 'switch' },
        { key: 'danmakuTextScale', label: '文字缩放', type: 'number', min: 0.5, max: 2, step: 0.05 },
        { key: 'danmakuTransparency', label: '透明度', type: 'number', min: 0, max: 1, step: 0.05 },
        { key: 'danmakuBold', label: '粗体文字', type: 'switch' },
        { key: 'danmakuMaxOnScreen', label: '同屏数量', type: 'number', min: 0, max: 100, step: 1 },
        { key: 'danmakuScrollArea', label: '滚动区域', type: 'number', min: 0.1, max: 1, step: 0.1 },
        { key: 'danmakuShowScroll', label: '滚动弹幕', type: 'switch' },
        { key: 'danmakuShowTop', label: '顶部弹幕', type: 'switch' },
        { key: 'danmakuShowBottom', label: '底部弹幕', type: 'switch' },
        { key: 'danmakuShowReverse', label: '反向弹幕', type: 'switch' },
        { key: 'danmakuMerge', label: '合并重复弹幕', type: 'switch' },
        { key: 'danmakuMaxLength', label: '最大文字长度', type: 'number', min: 0, max: 500, step: 1 },
        { key: 'danmakuBlocked', label: '屏蔽关键词', type: 'text', placeholder: '多个关键词用逗号分隔' }
    ]}
];

function search() {
    doAction('search', { word: $('#keyword').val() });
}

function push() {
    doAction('push', { url: $('#push_url').val() });
}

function sendDanmaku() {
    const text = $('#danmaku_text').val().trim();
    if (!text) return;
    doAction('danmaku', { text: `[0.0,${danmakuMode},${danmakuSize},16777215]${text}` });
    $('#danmaku_text').val('');
}

function showDanmakuModeDialog() {
    $('#danmakuModeDialog .md-dialog-list-item').removeClass('active');
    $(`#danmakuModeDialog .md-dialog-list-item[data-val="${danmakuMode}"]`).addClass('active');
    openDialog('danmakuModeDialog');
}

function setDanmakuMode(val, label) {
    danmakuMode = val;
    $('#danmaku_mode_label').text(label);
    closeDialog('danmakuModeDialog');
}

function showDanmakuSizeDialog() {
    $('#danmakuSizeDialog .md-dialog-list-item').removeClass('active');
    $(`#danmakuSizeDialog .md-dialog-list-item[data-val="${danmakuSize}"]`).addClass('active');
    openDialog('danmakuSizeDialog');
}

function setDanmakuSize(val, label) {
    danmakuSize = val;
    $('#danmaku_size_label').text(label);
    closeDialog('danmakuSizeDialog');
}

function doAction(action, kv) {
    $.post('/action', { ...kv, do: action });
}

function showManageSection(section) {
    const subscriptions = section === 'subscriptions';
    $('#manageSubscriptions').toggleClass('active', subscriptions);
    $('#manageSettings').toggleClass('active', !subscriptions);
    $('#manageTabSubscriptions').toggleClass('active', subscriptions);
    $('#manageTabSettings').toggleClass('active', !subscriptions);
}

function loadManage(showMessage = false) {
    $('#manageSubscriptions').addClass('manage-loading').html('<div class="manage-empty">正在读取电视数据...</div>');
    $.getJSON('/manage', function (state) {
        manageState = state;
        manageLoaded = true;
        renderSubscriptions();
        renderSettings();
        if (showMessage) warnToast('已刷新');
    }).fail(function (xhr) {
        manageLoaded = false;
        const message = xhr.status === 401 ? '配对已失效，请重新扫描电视二维码' : '无法连接电视';
        $('#manageSubscriptions').removeClass('manage-loading').html(`<div class="manage-empty">${message}</div>`);
        $('#manageSettings').html(`<div class="manage-empty">${message}</div>`);
    });
}

function renderSubscriptions() {
    if (!manageState) return;
    const html = subscriptionGroups.map(group => {
        const items = manageState.subscriptions[group.key] || [];
        const rows = items.length ? items.map(item => subscriptionRow(group, item)).join('') : '<div class="manage-empty compact">暂无订阅</div>';
        return `<section class="manage-group"><div class="manage-group-title"><h2>${group.title}</h2><button class="text-btn" type="button" onclick="openSubscriptionDialog('${group.key}')">添加</button></div><div class="subscription-list">${rows}</div></section>`;
    }).join('');
    $('#manageSubscriptions').removeClass('manage-loading').html(html);
}

function subscriptionRow(group, item) {
    const id = item.id;
    const active = String(manageState.active[group.key]) === String(id);
    const name = item.name || defaultSubscriptionName(item.url);
    const safeKey = escAttr(group.key);
    const safeId = escAttr(id);
    return `<div class="subscription-item${active ? ' active' : ''}">
        <div class="subscription-main">
            <div class="subscription-name">${escHtml(name)}${active ? '<span class="active-badge">当前</span>' : ''}</div>
            <div class="subscription-url">${escHtml(item.url || '')}</div>
        </div>
        <div class="subscription-actions">
            ${active ? '' : `<button type="button" onclick="activateSubscription('${safeKey}','${safeId}')">使用</button>`}
            <button type="button" onclick="openSubscriptionDialog('${safeKey}','${safeId}')">编辑</button>
            <button class="danger" type="button" onclick="confirmSubscriptionDelete('${safeKey}','${safeId}')">删除</button>
        </div>
    </div>`;
}

function defaultSubscriptionName(url) {
    if (!url) return '未命名订阅';
    try {
        const parsed = new URL(url);
        return parsed.hostname || '未命名订阅';
    } catch (e) {
        return '未命名订阅';
    }
}

function openSubscriptionDialog(key, id = '') {
    const group = subscriptionGroups.find(value => value.key === key);
    if (!group || !manageState) return;
    const item = id ? (manageState.subscriptions[key] || []).find(value => String(value.id) === String(id)) : null;
    editingSubscription = { key, id, epg: !!group.epg, type: group.type };
    $('#subscriptionDialogTitle').text((item ? '编辑' : '新增') + group.title.replace('订阅', ''));
    $('#subscriptionName').val(item ? item.name || '' : '');
    $('#subscriptionUrl').val(item ? item.url || '' : '');
    $('#subscriptionEpgType').toggle(!!group.epg).val(item && group.epg ? item.type || 0 : 0);
    openDialog('subscriptionDialog');
    setTimeout(() => $('#subscriptionName').trigger('focus'), 100);
}

function saveSubscription() {
    if (!editingSubscription) return;
    const name = $('#subscriptionName').val().trim();
    const url = $('#subscriptionUrl').val().trim();
    if (!url) { warnToast('请输入订阅地址'); return; }
    const data = { name, url };
    if (editingSubscription.id) data.id = editingSubscription.id;
    if (editingSubscription.epg) {
        data.do = 'epg-save';
        data.type = $('#subscriptionEpgType').val();
    } else {
        data.do = 'config-save';
        data.type = editingSubscription.type;
    }
    manageRequest(data, '已保存', function () { closeDialog('subscriptionDialog'); editingSubscription = null; });
}

function activateSubscription(key, id) {
    const group = subscriptionGroups.find(value => value.key === key);
    if (!group) return;
    manageRequest({ do: group.epg ? 'epg-active' : 'config-active', id }, '已切换');
}

function confirmSubscriptionDelete(key, id) {
    const group = subscriptionGroups.find(value => value.key === key);
    const item = group && (manageState.subscriptions[key] || []).find(value => String(value.id) === String(id));
    if (!group || !item) return;
    pendingSubscriptionDelete = { key, id, epg: !!group.epg };
    $('#subscriptionDeleteText').text(`确定删除“${item.name || defaultSubscriptionName(item.url)}”？`);
    openDialog('subscriptionDeleteDialog');
}

function deleteSubscription() {
    if (!pendingSubscriptionDelete) return;
    const item = pendingSubscriptionDelete;
    manageRequest({ do: item.epg ? 'epg-delete' : 'config-delete', id: item.id }, '已删除', function () {
        closeDialog('subscriptionDeleteDialog');
        pendingSubscriptionDelete = null;
    });
}

function renderSettings() {
    if (!manageState) return;
    const html = settingGroups.map(group => `<section class="manage-group"><div class="manage-group-title"><h2>${group.title}</h2></div><div class="setting-list">${group.items.map(settingRow).join('')}</div></section>`).join('');
    $('#manageSettings').html(html);
}

function settingRow(item) {
    const value = manageState.settings[item.key];
    let control;
    if (item.type === 'switch') {
        control = `<label class="md-switch"><input type="checkbox" ${value ? 'checked' : ''} onchange="saveManageSetting('${item.key}',this.checked)"><span></span></label>`;
    } else if (item.type === 'color') {
        control = `<div class="color-swatches">${item.options.map(option => `<button type="button" title="${option[1]}" aria-label="${option[1]}" class="color-swatch${String(option[0]) === String(value) ? ' active' : ''}" style="--swatch:${option[2]}" onclick="saveManageSetting('${item.key}','${option[0]}')"></button>`).join('')}</div>`;
    } else if (item.type === 'select') {
        const options = item.options.map(option => `<option value="${option[0]}"${String(option[0]) === String(value) ? ' selected' : ''}>${option[1]}</option>`).join('');
        control = `<select class="setting-control" onchange="saveManageSetting('${item.key}',this.value)">${options}</select>`;
    } else {
        const attrs = item.type === 'number' ? `type="number" min="${item.min}" max="${item.max}" step="${item.step}"` : `type="text" placeholder="${item.placeholder || ''}"`;
        control = `<div class="setting-input-wrap"><input class="setting-control setting-input" ${attrs} value="${escAttr(value == null ? '' : value)}" onchange="saveManageSetting('${item.key}',this.value)">${item.suffix ? `<span>${item.suffix}</span>` : ''}</div>`;
    }
    return `<div class="setting-item"><label>${item.label}</label>${control}</div>`;
}

function saveManageSetting(key, value) {
    manageRequest({ do: 'setting', key, value }, '已保存');
}

function manageRequest(data, message, beforeRender) {
    $('#loadingToast').show();
    $.ajax({
        url: '/manage', type: 'post', data, dataType: 'json',
        success: function (state) {
            manageState = state;
            if (beforeRender) beforeRender();
            renderSubscriptions();
            renderSettings();
            warnToast(message);
        },
        error: function (xhr) {
            const text = xhr.responseText || '操作失败';
            warnToast(text.length > 40 ? '操作失败' : text);
        },
        complete: function () { $('#loadingToast').hide(); }
    });
}

function openDialog(id) {
    $('#' + id).show();
    history.pushState({ dialog: id }, '');
}

function closeDialog(id) {
    dialogClosing = true;
    $('#' + id).hide();
    history.back();
}

function startLongPress(callback) {
    longPressTriggered = false;
    longPressTimer = setTimeout(() => {
        longPressTriggered = true;
        callback();
    }, 500);
}

function cancelLongPress() {
    if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
}

function escPath(s) {
    return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

function escHtml(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function escAttr(s) {
    return escHtml(String(s)).replace(/'/g, '&#39;');
}

function buildParentItem() {
    return `<a class="file-item" href="javascript:void(0)" onclick="history.back()">
    <img class="file-icon" src="${icDir}" alt="">
    <div class="file-info"><div class="file-name">..</div></div>
    </a>`;
}

function buildDirItem(name, time, path) {
    const ep = escPath(path);
    return `<a class="file-item" href="javascript:void(0)" ontouchstart="startLongPress(()=>showDelFolderDialog('${ep}',currentRoot))" ontouchmove="cancelLongPress()" ontouchend="cancelLongPress()" oncontextmenu="return false" onclick="if(!longPressTriggered)listFile('${ep}',true)">
    <img class="file-icon" src="${icDir}" alt="">
    <div class="file-info"><div class="file-name">${escHtml(name)}</div><div class="file-time">${escHtml(time)}</div></div>
    </a>`;
}

function buildFileItem(name, time, path) {
    const ep = escPath(path);
    return `<a class="file-item" href="javascript:void(0)" ontouchstart="startLongPress(()=>showDelFileDialog('${ep}'))" ontouchmove="cancelLongPress()" ontouchend="cancelLongPress()" oncontextmenu="return false" onclick="if(!longPressTriggered)selectFile('${ep}')">
    <img class="file-icon" src="${icFile}" alt="">
    <div class="file-info"><div class="file-name">${escHtml(name)}</div><div class="file-time">${escHtml(time)}</div></div>
    </a>`;
}

function addFile(node) {
    $('#file_list').append(node);
}

function selectFile(path) {
    currentFile = path;
    $("#fileUrl").text("file:/" + path);
    openDialog('fileInfoDialog');
}

function pushFile(yes) {
    closeDialog('fileInfoDialog');
    if (yes === 1) doAction('file', { path: "file:/" + currentFile });
}

function listFile(path, addHistory = false) {
    const loadingTimer = setTimeout(() => $('#loadingToast').show(), 200);
    $.get('/file' + path, function (res) {
        clearTimeout(loadingTimer);
        let info;
        try {
            info = JSON.parse(res);
        } catch (e) {
            $('#loadingToast').hide();
            warnToast('响应格式错误');
            return;
        }
        const parent = info.parent;
        currentRoot = path;
        currentParent = parent;
        const array = info.files;
        if (path === '' && array.length === 0) warnToast('可能没有存储权限');
        $('#file_list').html('');
        if (parent !== '.') addFile(buildParentItem());
        array.forEach(node => {
            if (node.dir === 1) addFile(buildDirItem(node.name, node.time, node.path));
            else addFile(buildFileItem(node.name, node.time, node.path));
        });
        if (addHistory) history.pushState(path, '');
        $('#loadingToast').hide();
    }).fail(function () {
        clearTimeout(loadingTimer);
        $('#loadingToast').hide();
        warnToast('加载失败');
    });
}

function uploadFile() {
    $('#file_uploader').click();
}

function onFileSelected() {
    const files = $('#file_uploader')[0].files;
    if (files.length === 0) return;
    const tip = Array.from(files).map(f => f.name).join(', ');
    $('#uploadTipContent').text(tip);
    openDialog('uploadTip');
}

function confirmUpload(yes) {
    closeDialog('uploadTip');
    if (yes !== 1) return;
    const files = $('#file_uploader')[0].files;
    if (files.length === 0) return;
    const formData = new FormData();
    formData.append('path', currentRoot);
    Array.from(files).forEach((f, i) => formData.append('files-' + i, f));
    $('#loadingToast').show();
    $.ajax({
        url: '/upload',
        type: 'post',
        data: formData,
        processData: false,
        contentType: false,
        complete: function () {
            $('#loadingToast').hide();
            $('#file_uploader').val('');
            listFile(currentRoot);
        }
    });
}

function showNewFolderDialog() {
    openDialog('newFolder');
}

function confirmNewFolder(yes) {
    closeDialog('newFolder');
    const name = $('#newFolderContent').val().trim();
    $('#newFolderContent').val('');
    if (yes !== 1 || name.length === 0) return;
    $('#loadingToast').show();
    $.post('/newFolder', { path: currentRoot, name }, function () {
        $('#loadingToast').hide();
        listFile(currentRoot);
    }).fail(function () {
        $('#loadingToast').hide();
        warnToast('新建失败');
    });
}

function showDelFolderDialog(path, refreshPath) {
    pendingDelFolder = { path, refreshPath };
    $('#delFolderContent').text('是否删除 ' + path);
    openDialog('delFolder');
}

function confirmDelFolder(yes) {
    closeDialog('delFolder');
    if (yes !== 1 || !pendingDelFolder) { pendingDelFolder = null; return; }
    const { path, refreshPath } = pendingDelFolder;
    pendingDelFolder = null;
    $('#loadingToast').show();
    $.post('/delFolder', { path }, function () {
        $('#loadingToast').hide();
        listFile(refreshPath);
    }).fail(function () {
        $('#loadingToast').hide();
        warnToast('删除失败');
    });
}

function showDelFileDialog(path) {
    currentFile = path;
    $('#delFileContent').text('是否删除 ' + path);
    openDialog('delFile');
}

function confirmDelFile(yes) {
    closeDialog('delFile');
    if (yes !== 1) return;
    $('#loadingToast').show();
    $.post('/delFile', { path: currentFile }, function () {
        $('#loadingToast').hide();
        listFile(currentRoot);
    }).fail(function () {
        $('#loadingToast').hide();
        warnToast('删除失败');
    });
}

function warnToast(msg) {
    $('#warnToastContent').text(msg);
    $('#warnToast').show();
    if (warnToastTimer) clearTimeout(warnToastTimer);
    warnToastTimer = setTimeout(() => { $('#warnToast').hide(); warnToastTimer = null; }, 1000);
}

function showPanel(id) {
    for (let i = 1; i <= 5; i++) {
        document.getElementById('panel' + i).classList.toggle('active', i === id);
        document.getElementById('tab' + i).classList.toggle('active', i === id);
    }
    if (id === 5 && document.getElementById('file_list').innerHTML === '') listFile('');
    if (id === 4 && !manageLoaded) loadManage();
}

const tab = parseInt(new URLSearchParams(window.location.search).get('tab')) || 1;
history.replaceState(null, '');
showPanel(tab);

window.addEventListener('popstate', function () {
    if (dialogClosing) { dialogClosing = false; return; }
    const visible = $('.md-dialog-overlay:visible');
    if (visible.length) { visible.first().hide(); return; }
    listFile(currentParent);
});

$(function () {
    $('#keyword').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); search(); } });
    $('#push_url').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); push(); } });
    $('#danmaku_text').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); sendDanmaku(); } });
    $('#subscriptionName, #subscriptionUrl').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); saveSubscription(); } });
    $('#newFolderContent').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); confirmNewFolder(1); } });
});
