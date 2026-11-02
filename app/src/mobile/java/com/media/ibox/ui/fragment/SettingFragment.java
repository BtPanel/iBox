package com.media.ibox.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.BuildConfig;
import com.media.ibox.Product;
import com.media.ibox.R;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.databinding.FragmentSettingBinding;
import com.media.ibox.databinding.ViewSettingRowBinding;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.activity.HomeActivity;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.utils.ResUtil;

/**
 * 设置首页：只列入口，具体开关下沉到二级页面
 * （内容源 / 通用 / 外观与布局 / 播放与弹幕 / 网络与数据）。
 */
public class SettingFragment extends BaseFragment {

    private FragmentSettingBinding mBinding;
    private String[] themeMode;
    private String[] size;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private HomeActivity getRoot() {
        return (HomeActivity) requireActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        Product.applySettingsWidth(mBinding.settingsContent, requireActivity());
        bindEntry(mBinding.rowSource, R.drawable.ic_msr_movie, R.string.setting_entry_source, HomeActivity.PAGE_SOURCE);
        bindEntry(mBinding.rowGeneral, R.drawable.ic_msr_tune, R.string.setting_entry_general, HomeActivity.PAGE_GENERAL);
        bindEntry(mBinding.rowAppearance, R.drawable.ic_msr_grid_view, R.string.setting_entry_appearance, HomeActivity.PAGE_APPEARANCE);
        bindEntry(mBinding.rowPlayback, R.drawable.ic_msr_play_arrow, R.string.setting_entry_playback, HomeActivity.PAGE_PLAYBACK);
        bindEntry(mBinding.rowData, R.drawable.ic_msr_database, R.string.setting_entry_data, HomeActivity.PAGE_DATA);
        mBinding.rowGeneral.summary.setText(R.string.setting_entry_general_summary);
        mBinding.rowGeneral.summary.setVisibility(View.VISIBLE);
        mBinding.rowPlayback.summary.setText(R.string.setting_entry_playback_summary);
        mBinding.rowPlayback.summary.setVisibility(View.VISIBLE);
        bindVersion();
        refresh();
    }

    private void bindEntry(ViewSettingRowBinding row, int icon, int title, int page) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
        row.getRoot().setOnClickListener(view -> getRoot().change(page));
    }

    private void bindVersion() {
        mBinding.rowVersion.icon.setImageResource(R.drawable.ic_msr_info);
        mBinding.rowVersion.title.setText(R.string.app_name);
        mBinding.rowVersion.summary.setText(R.string.settings_version_summary);
        mBinding.rowVersion.summary.setVisibility(View.VISIBLE);
        mBinding.rowVersion.value.setText(BuildConfig.VERSION_NAME);
        mBinding.rowVersion.value.setVisibility(View.VISIBLE);
        mBinding.rowVersion.chevron.setVisibility(View.GONE);
        mBinding.rowVersion.getRoot().setClickable(false);
        mBinding.rowVersion.getRoot().setFocusable(false);
    }

    /** 入口副标题用当前真实状态，不写死文案 */
    private void refresh() {
        if (mBinding == null) return;
        String vod = VodConfig.getDesc();
        mBinding.rowSource.summary.setText(TextUtils.isEmpty(vod) ? getString(R.string.setting_entry_source_empty) : vod);
        mBinding.rowSource.summary.setVisibility(View.VISIBLE);
        themeMode = ResUtil.getStringArray(R.array.select_theme_mode);
        size = ResUtil.getStringArray(R.array.select_size);
        mBinding.rowAppearance.summary.setText(getString(R.string.setting_entry_appearance_summary,
                themeMode[Setting.getThemeMode()], size[PlayerSetting.getSize()]));
        mBinding.rowAppearance.summary.setVisibility(View.VISIBLE);
        mBinding.rowData.summary.setText(getString(R.string.setting_entry_data_summary, Setting.getSearchThreads()));
        mBinding.rowData.summary.setVisibility(View.VISIBLE);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) refresh();
    }
}
