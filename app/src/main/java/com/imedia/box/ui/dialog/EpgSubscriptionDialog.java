package com.imedia.box.ui.dialog;

import android.text.TextUtils;
import android.os.Build;
import android.os.Environment;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.github.catvod.utils.Json;
import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;
import com.imedia.box.R;
import com.imedia.box.databinding.DialogEpgSubscriptionBinding;
import com.imedia.box.event.ServerEvent;
import com.imedia.box.live.EpgCacheScheduler;
import com.imedia.box.server.Server;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.ui.adapter.EpgSubscriptionAdapter;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.PermissionUtil;
import com.imedia.box.utils.QRCode;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EpgSubscriptionDialog extends BaseAlertDialog implements EpgSubscriptionAdapter.Listener {

    private DialogEpgSubscriptionBinding binding;
    private EpgSubscriptionAdapter adapter;
    private Runnable onChanged;
    private String editingId;
    private int type;

    public static EpgSubscriptionDialog create() {
        return new EpgSubscriptionDialog();
    }

    public EpgSubscriptionDialog onChanged(Runnable callback) {
        this.onChanged = callback;
        return this;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogEpgSubscriptionBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        if (Util.isLeanback()) {
            Server.get().start();
            binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(4), 166, 0));
            binding.info.setText(getString(R.string.live_epg_scan_info, Server.get().getAddress()));
        }
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter = new EpgSubscriptionAdapter(this));
        refresh();
        clearEditor();
    }

    @Override
    protected void initEvent() {
        binding.cache.setOnClickListener(v -> showCacheTimes());
        binding.other.setOnClickListener(v -> showOtherSettings());
        binding.help.setOnClickListener(v -> showHeadersHelp());
        binding.clear.setOnClickListener(v -> clearEditor());
        binding.type.setOnClickListener(v -> setType(type == 0 ? 1 : 0));
        binding.positive.setOnClickListener(v -> save());
    }

    private void refresh() {
        List<LiveSetting.EpgSubscription> items = LiveSetting.getEpgSubscriptions();
        adapter.setItems(items);
        if (!Util.isLeanback()) {
            ViewGroup.LayoutParams params = binding.list.getLayoutParams();
            params.height = ResUtil.dp2px(Math.max(1, Math.min(3, items.size())) * 56);
            binding.list.setLayoutParams(params);
        }
        binding.cache.setText(getString(R.string.live_epg_cache_value, LiveSetting.getEpgCacheHour()));
    }

    private void save() {
        String name = binding.name.getText().toString().trim();
        ParsedAddress address = parseAddress(binding.url.getText().toString().trim());
        if (address.url.isEmpty()) {
            Notify.show(R.string.live_epg_url_required);
            binding.url.requestFocus();
            return;
        }
        boolean active = LiveSetting.getActiveEpgSubscription() == null || LiveSetting.isActiveEpgSubscription(editingId);
        LiveSetting.EpgSubscription item = LiveSetting.saveEpgSubscription(editingId, name, address.url, type, address.headers);
        editingId = item.getId();
        if (active || LiveSetting.isActiveEpgSubscription(item.getId())) notifyChanged();
        refresh();
        Notify.show(R.string.live_epg_saved);
    }

    private ParsedAddress parseAddress(String source) {
        if (!Json.isObj(source)) return new ParsedAddress(source, new HashMap<>());
        try {
            JsonObject object = Json.parse(source).getAsJsonObject();
            String url = Json.safeString(object, "url");
            Map<String, String> headers = object.has("headers") && object.get("headers").isJsonObject()
                    ? Json.toMap(object.get("headers").toString()) : new HashMap<>();
            return new ParsedAddress(url, headers);
        } catch (Exception ignored) {
            return new ParsedAddress("", new HashMap<>());
        }
    }

    private void clearEditor() {
        editingId = null;
        binding.name.setText("");
        binding.url.setText("");
        setType(0);
    }

    private void setType(int value) {
        type = value == 1 ? 1 : 0;
        binding.type.setText(type == 1 ? R.string.live_epg_type_diyp : R.string.live_epg_type_xml);
    }

    private void showHeadersHelp() {
        if (TextUtils.isEmpty(binding.url.getText())) {
            binding.url.setText(R.string.live_epg_headers_example);
            binding.url.setSelection(binding.url.length());
        } else {
            Notify.show(R.string.live_epg_headers_help);
        }
    }

    private void showCacheTimes() {
        int[] hours = LiveSetting.EPG_CACHE_HOURS;
        CharSequence[] labels = new CharSequence[hours.length];
        int checked = 0;
        for (int i = 0; i < hours.length; i++) {
            labels[i] = getString(R.string.live_menu_epg_cache_hour, hours[i]);
            if (hours[i] == LiveSetting.getEpgCacheHour()) checked = i;
        }
        new IBoxAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_IBox_Dialog_Alert)
                .setTitle(R.string.live_menu_epg_cache_time)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    LiveSetting.putEpgCacheHour(hours[which]);
                    EpgCacheScheduler.schedule(requireContext());
                    refresh();
                    dialog.dismiss();
                })
                .show();
    }

    private void showOtherSettings() {
        new IBoxAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_IBox_Dialog_Alert)
                .setItems(new CharSequence[]{getString(R.string.live_epg_offset), getString(R.string.live_epg_cache_storage)}, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) showOffsetSettings();
                    else showStorageSettings();
                })
                .show();
    }

    private void showOffsetSettings() {
        CharSequence[] labels = new CharSequence[26];
        labels[0] = getString(R.string.live_epg_default_timezone);
        int checked = 0;
        for (int hour = -12; hour <= 12; hour++) {
            labels[hour + 13] = getString(R.string.live_epg_offset_hour, hour);
            if (LiveSetting.hasCustomEpgOffset() && LiveSetting.getEpgOffsetHours() == hour) checked = hour + 13;
        }
        new IBoxAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_IBox_Dialog_Alert)
                .setTitle(R.string.live_epg_offset)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    LiveSetting.putEpgOffsetHours(which == 0 ? null : which - 13);
                    notifyChanged();
                    dialog.dismiss();
                })
                .show();
    }

    private void showStorageSettings() {
        CharSequence[] labels = {getString(R.string.live_epg_storage_internal), getString(R.string.live_epg_storage_external)};
        new IBoxAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_IBox_Dialog_Alert)
                .setTitle(R.string.live_epg_cache_storage)
                .setSingleChoiceItems(labels, LiveSetting.isEpgExternalCache() ? 1 : 0, (dialog, which) -> {
                    if (which == 0) {
                        LiveSetting.putEpgExternalCache(false);
                        dialog.dismiss();
                        return;
                    }
                    PermissionUtil.requestFile(this, granted -> {
                        if (!isAdded()) return;
                        boolean scoped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
                        boolean allFiles = scoped && Environment.isExternalStorageManager();
                        if (EpgStorageSelection.canUseExternal(granted, scoped, allFiles)) {
                            LiveSetting.putEpgExternalCache(true);
                            dialog.dismiss();
                        } else {
                            Notify.show(R.string.live_epg_storage_permission_required);
                        }
                    });
                })
                .show();
    }

    @Override
    public void onEdit(LiveSetting.EpgSubscription item) {
        editingId = item.getId();
        binding.name.setText(item.getName());
        binding.url.setText(formatAddress(item));
        binding.url.setSelection(binding.url.length());
        setType(item.getType());
    }

    @Override
    public void onCopy(LiveSetting.EpgSubscription item) {
        LiveSetting.EpgSubscription copy = LiveSetting.copyEpgSubscription(item.getId());
        if (copy != null) onEdit(copy);
        refresh();
    }

    @Override
    public void onDelete(LiveSetting.EpgSubscription item) {
        boolean active = LiveSetting.isActiveEpgSubscription(item.getId());
        LiveSetting.deleteEpgSubscription(item.getId());
        if (item.getId().equals(editingId)) clearEditor();
        refresh();
        if (active) notifyChanged();
    }

    @Override
    public void onActivate(LiveSetting.EpgSubscription item) {
        LiveSetting.setActiveEpgSubscription(item.getId());
        refresh();
        notifyChanged();
        Notify.show(R.string.live_epg_activated);
    }

    private String formatAddress(LiveSetting.EpgSubscription item) {
        if (item.getHeaders().isEmpty()) return item.getUrl();
        JsonObject object = new JsonObject();
        object.addProperty("url", item.getUrl());
        object.add("headers", Json.parse(com.imedia.box.App.gson().toJson(item.getHeaders())));
        return object.toString();
    }

    private void notifyChanged() {
        if (onChanged != null) onChanged.run();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        if (event.name() != null) binding.name.setText(event.name());
        binding.url.setText(event.text());
        binding.url.setSelection(binding.url.length());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.isLeanback()) setWidth(0.74f);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onStop();
    }

    private record ParsedAddress(String url, Map<String, String> headers) {
    }
}
