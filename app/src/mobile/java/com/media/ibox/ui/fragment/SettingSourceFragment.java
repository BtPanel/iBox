package com.media.ibox.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.api.config.LiveConfig;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.bean.Config;
import com.media.ibox.bean.Live;
import com.media.ibox.bean.Site;
import com.media.ibox.databinding.FragmentSettingSourceBinding;
import com.media.ibox.event.ConfigEvent;
import com.media.ibox.impl.Callback;
import com.media.ibox.impl.ConfigListener;
import com.media.ibox.impl.LiveListener;
import com.media.ibox.impl.SiteListener;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.ui.dialog.ConfigDialog;
import com.media.ibox.ui.dialog.LiveDialog;
import com.media.ibox.ui.dialog.RepositoryDialog;
import com.media.ibox.ui.dialog.SiteDialog;
import com.media.ibox.utils.FileChooser;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.PermissionUtil;
import com.github.catvod.utils.Path;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * 二级页：内容源。点播订阅、直播源、壁纸从这里进入，
 * 点按 = 换源，长按 = 编辑地址，右侧两个小按钮 = 设为首页 / 订阅管理。
 */
public class SettingSourceFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener {

    private FragmentSettingSourceBinding mBinding;
    private WallpaperSettingsController wallpaperController;

    private final ActivityResultLauncher<Intent> wallpaperPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) return;
        setConfig(Config.find("file:/" + path.replace(Path.rootPath(), ""), 2));
    });

    public static SettingSourceFragment newInstance() {
        return new SettingSourceFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingSourceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        bindBackNavigation(mBinding.toolbar);
        EventBus.getDefault().register(this);
        mBinding.rowWall.icon.setImageResource(R.drawable.ic_msr_grid_view);
        mBinding.rowWall.title.setText(R.string.setting_wall);
        mBinding.rowWall.summary.setVisibility(View.VISIBLE);
        wallpaperController = new WallpaperSettingsController(this, mBinding.rowWall.summary, wallpaperPicker);
        wallpaperController.init();
        refresh();
    }

    private void refresh() {
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.rowWall.getRoot().setOnClickListener(wallpaperController::show);
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                wallpaperController.load(config);
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                refresh();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        RepositoryDialog.create().vod().show(this);
    }

    private void onLive(View view) {
        RepositoryDialog.create().live().show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create().live().edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create().search().change().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.show(this);
    }

    private void onVodHistory(View view) {
        RepositoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        RepositoryDialog.create().live().show(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) {
            wallpaperController.refresh();
        } else if (event.type() == ConfigEvent.Type.COMMON) {
            refresh();
            wallpaperController.refresh();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden && wallpaperController != null) {
            refresh();
            wallpaperController.refresh();
        }
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        if (wallpaperController != null) {
            wallpaperController.release();
            wallpaperController = null;
        }
        super.onDestroyView();
    }
}
