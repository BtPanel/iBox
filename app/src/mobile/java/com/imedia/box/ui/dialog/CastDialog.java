package com.imedia.box.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.App;
import com.imedia.box.Constant;
import com.imedia.box.R;
import com.imedia.box.bean.CastVideo;
import com.imedia.box.bean.Config;
import com.imedia.box.bean.Device;
import com.imedia.box.bean.History;
import com.imedia.box.bean.Episode;
import com.imedia.box.databinding.DialogDeviceBinding;
import com.imedia.box.dlna.DLNACast;
import com.imedia.box.dlna.DLNACastManager;
import com.imedia.box.dlna.DLNACastSession;
import com.imedia.box.dlna.CastEpisodeQueue;
import com.imedia.box.player.PlayerManager;
import com.imedia.box.server.Server;
import com.imedia.box.ui.activity.CastControlActivity;
import com.imedia.box.ui.activity.ScanActivity;
import com.imedia.box.ui.adapter.DeviceAdapter;
import com.imedia.box.ui.custom.SpaceItemDecoration;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.ScanTask;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class CastDialog extends BaseBottomSheetDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener, DLNACastManager.DeviceListener, Callback {

    private final FormBody.Builder body;
    private final OkHttpClient client;

    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private ScanTask scanTask;
    private CastVideo video;
    private CastEpisodeQueue episodes;
    private History history;
    private boolean connecting;
    private boolean searching;
    private boolean fm;
    private boolean switchingDevice;

    private final Runnable finishSearch = () -> setSearching(false);

    public CastDialog() {
        scanTask = new ScanTask(this);
        body = new FormBody.Builder();
        body.add("device", Device.get().toString());
        body.add("config", Config.vod().toString());
        client = OkHttp.client(Constant.TIMEOUT_SYNC);
    }

    public static CastDialog create(PlayerManager player) {
        CastDialog dialog = new CastDialog();
        dialog.fm = player.isVod();
        dialog.video = CastVideo.create(player, dialog.fm ? player.getPosition() : C.TIME_UNSET);
        return dialog;
    }

    public static void switchDevice(FragmentActivity activity) {
        if (!DLNACastSession.get().isActive()) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof CastDialog) return;
        CastDialog dialog = new CastDialog();
        dialog.switchingDevice = true;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    public CastDialog history(History history) {
        String id = history.getVodId();
        String fd = history.getVodId();
        if (fd.startsWith("/") || fd.startsWith("file")) fd = Server.get().getFileStreamAddress(fd);
        else fd = Server.get().exposeLocalStream(fd);
        body.add("history", history.toString().replace(id, fd));
        return this;
    }

    public CastDialog episodes(History history, List<Episode> items, int selected) {
        if (history != null && items != null && !items.isEmpty()) {
            List<CastEpisodeQueue.Item> queue = items.stream()
                    .map(item -> new CastEpisodeQueue.Item(item.getName(), item.getUrl())).toList();
            episodes = new CastEpisodeQueue(history.getSiteKey(), history.getVodFlag(), queue, selected, history.isRevPlay());
            this.history = history.copy();
        }
        return this;
    }

    public CastDialog image(String image) {
        video = video.image(image);
        return this;
    }

    public void show(FragmentActivity activity) {
        if (DLNACastSession.get().isActive()) {
            CastControlActivity.start(activity);
            return;
        }
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof CastDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.scan.setVisibility(fm && !switchingDevice ? View.VISIBLE : View.GONE);
        if (switchingDevice) binding.title.setText(R.string.cast_switch_device);
        DLNACastManager.get().init(requireActivity());
        DLNACastManager.get().setDeviceListener(this);
        setRecyclerView();
        getDevice();
    }

    @Override
    protected void initEvent() {
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
        binding.emptyRefresh.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(false);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
    }

    private void setRecyclerVisible() {
        if (binding == null) return;
        boolean hasDevice = adapter.getItemCount() > 0;
        binding.recycler.setVisibility(hasDevice ? View.VISIBLE : View.GONE);
        binding.empty.setVisibility(!searching && !hasDevice ? View.VISIBLE : View.GONE);
    }

    private void getDevice() {
        if (switchingDevice) {
            adapter.setItems(otherRenderers(DLNACastManager.get().getRegistered()), () -> {
                setRecyclerVisible();
                search();
            });
            return;
        }
        adapter.setItems(Device.getAll(), () -> {
            adapter.sort(DLNACastManager.get().getRegistered(), this::setRecyclerVisible);
            if (adapter.getItemCount() == 0) onRefresh();
            else search();
        });
    }

    private void onScan() {
        launcher.launch(new Intent(requireActivity(), ScanActivity.class));
    }

    private void onRefresh() {
        if (switchingDevice) {
            adapter.clear(() -> {
                search();
                adapter.sort(otherRenderers(DLNACastManager.get().getRegistered()), this::setRecyclerVisible);
            });
            return;
        }
        adapter.clear(() -> {
            Device.delete();
            if (fm) scanTask.start();
            search();
            adapter.sort(DLNACastManager.get().getRegistered(), this::setRecyclerVisible);
        });
    }

    private void search() {
        if (binding == null) return;
        connecting = false;
        binding.searchText.setText(R.string.cast_searching);
        binding.recycler.setAlpha(1f);
        setSearching(true);
        DLNACastManager.get().search();
        App.post(finishSearch, 4000);
    }

    private void setSearching(boolean searching) {
        this.searching = searching;
        if (binding == null) return;
        binding.searching.setVisibility(searching ? View.VISIBLE : View.GONE);
        setRecyclerVisible();
    }

    private void onCasted() {
        ((CastDialog.Listener) requireActivity()).onCasted();
        dismiss();
    }

    private void onDlnaCasted(Device device) {
        DLNACastSession.get().start(video, device);
        DLNACastSession.get().setEpisodes(episodes, history);
        ((CastDialog.Listener) requireActivity()).onCasted();
        CastControlActivity.start(requireActivity());
        dismiss();
    }

    private void onCastFailed() {
        connecting = false;
        if (binding == null) return;
        binding.recycler.setAlpha(1f);
        setSearching(false);
    }

    private java.util.List<Device> otherRenderers(java.util.List<Device> devices) {
        Device current = DLNACastSession.get().getDevice();
        return devices.stream().filter(item -> item.isDLNA() && !item.equals(current)).toList();
    }

    @Override
    public void onDeviceAdded(Device device) {
        if (binding == null) return;
        if (!switchingDevice || otherRenderers(java.util.List.of(device)).size() == 1) adapter.sort(device, this::setRecyclerVisible);
    }

    @Override
    public void onDeviceRemoved(Device device) {
        if (binding == null) return;
        adapter.remove(device, this::setRecyclerVisible);
    }

    @Override
    public void onFind(Device device) {
        onDeviceAdded(device);
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        App.post(() -> {
            onCastFailed();
            Notify.show(e.getMessage());
        });
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        try (Response res = response) {
            if (res.body().string().equals("OK")) App.post(this::onCasted);
            else App.post(() -> {
                onCastFailed();
                Notify.show(R.string.device_offline);
            });
        }
    }

    @Override
    public void onItemClick(Device item) {
        if (connecting) return;
        connecting = true;
        App.removeCallbacks(finishSearch);
        binding.searchText.setText(getString(R.string.cast_connecting_device, item.getName()));
        binding.recycler.setAlpha(0.5f);
        setSearching(true);
        if (switchingDevice) DLNACastSession.get().switchTo(item, this::dismiss, this::onCastFailed);
        else if (item.isDLNA()) new DLNACast(video, () -> onDlnaCasted(item), this::onCastFailed).cast(item);
        else OkHttp.newCall(client, item.getIp().concat("/action?do=cast"), body.build()).enqueue(this);
    }

    @Override
    public boolean onLongClick(Device item) {
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        DLNACastManager.get().setDeviceListener(null);
        DLNACastManager.get().release(requireActivity());
        App.removeCallbacks(finishSearch);
        scanTask.stop();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) scanTask.start(result.getData().getStringExtra("address"));
    });

    public interface Listener {

        void onCasted();
    }
}
