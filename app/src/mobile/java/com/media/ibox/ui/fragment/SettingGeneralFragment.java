package com.media.ibox.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.FragmentSettingGeneralBinding;
import com.media.ibox.databinding.ViewSettingRowBinding;
import com.media.ibox.databinding.ViewSettingSwitchRowBinding;
import com.media.ibox.server.RemoteAccess;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.ui.custom.AppNavigation;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.media.ibox.ui.dialog.RemoteAccessDialog;
import com.media.ibox.utils.ResUtil;

/**
 * 二级页：通用。无痕、配置提示、局域网控制、底部菜单、启动页。
 */
public class SettingGeneralFragment extends BaseFragment {

    private FragmentSettingGeneralBinding mBinding;
    private String[] bootStart;

    public static SettingGeneralFragment newInstance() {
        return new SettingGeneralFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingGeneralBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        bindBackNavigation(mBinding.toolbar);
        initRow(mBinding.rowIncognito, R.drawable.ic_msr_lock_open, R.string.setting_incognito);
        initRow(mBinding.rowConfigNotice, R.drawable.ic_msr_info, R.string.setting_config_notice);
        initRow(mBinding.rowRemoteAccess, R.drawable.ic_msr_smartphone, R.string.remote_title);
        initRow(mBinding.rowHideNavigation, R.drawable.ic_setting_navigation, R.string.setting_hide_navigation);
        initRow(mBinding.rowBootAutoStart, R.drawable.ic_msr_home, R.string.setting_boot_auto_start);
        refresh();
    }

    private void initRow(ViewSettingSwitchRowBinding row, int icon, int title) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
    }

    private void initRow(ViewSettingRowBinding row, int icon, int title) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
        row.value.setVisibility(View.VISIBLE);
    }

    private void refresh() {
        if (mBinding == null) return;
        mBinding.rowIncognito.toggle.setChecked(Setting.isIncognito());
        mBinding.rowConfigNotice.toggle.setChecked(Setting.isConfigNoticeEnabled());
        mBinding.rowRemoteAccess.value.setText(Setting.getSwitch(RemoteAccess.isEnabled()));
        mBinding.rowHideNavigation.value.setText(getHiddenNavigationText());
        bootStart = ResUtil.getStringArray(R.array.select_boot_start);
        mBinding.rowBootAutoStart.value.setText(bootStart[Setting.getBootStartMode()]);
    }

    @Override
    protected void initEvent() {
        mBinding.rowIncognito.getRoot().setOnClickListener(this::setIncognito);
        mBinding.rowConfigNotice.getRoot().setOnClickListener(this::setConfigNotice);
        mBinding.rowRemoteAccess.getRoot().setOnClickListener(view -> RemoteAccessDialog.create().show(requireActivity()));
        mBinding.rowHideNavigation.getRoot().setOnClickListener(this::setNavigationHidden);
        mBinding.rowBootAutoStart.getRoot().setOnClickListener(this::setBootAutoStart);
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.rowIncognito.toggle.setChecked(Setting.isIncognito());
    }

    private void setConfigNotice(View view) {
        Setting.putConfigNoticeEnabled(!Setting.isConfigNoticeEnabled());
        mBinding.rowConfigNotice.toggle.setChecked(Setting.isConfigNoticeEnabled());
    }

    private void setNavigationHidden(View view) {
        String[] items = {getString(R.string.nav_home), getString(R.string.nav_live), getString(R.string.nav_library), getString(R.string.download_short)};
        int[] flags = {Setting.NAVIGATION_HOME, Setting.NAVIGATION_LIVE, Setting.NAVIGATION_LIBRARY, Setting.NAVIGATION_DOWNLOADS};
        int current = Setting.getHiddenNavigationItems();
        boolean[] checked = new boolean[items.length];
        for (int i = 0; i < flags.length; i++) checked[i] = (current & flags[i]) != 0;
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_hide_navigation).setMultiChoiceItems(items, checked, (dialog, which, value) -> checked[which] = value).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
            int hidden = 0;
            for (int i = 0; i < flags.length; i++) if (checked[i]) hidden |= flags[i];
            Setting.putHiddenNavigationItems(hidden);
            mBinding.rowHideNavigation.value.setText(getHiddenNavigationText());
            AppNavigation.applyVisibility(requireActivity());
        }).show();
    }

    private String getHiddenNavigationText() {
        int hidden = Setting.getHiddenNavigationItems();
        if (hidden == 0) return getString(R.string.setting_none);
        int[] flags = {Setting.NAVIGATION_HOME, Setting.NAVIGATION_LIVE, Setting.NAVIGATION_LIBRARY, Setting.NAVIGATION_DOWNLOADS};
        int count = 0;
        for (int flag : flags) if ((hidden & flag) != 0) count++;
        return getString(R.string.setting_hidden_count, count);
    }

    private void setBootAutoStart(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_boot_auto_start).setSingleChoiceItems(bootStart, Setting.getBootStartMode(), (dialog, which) -> {
            Setting.putBootStartMode(which);
            mBinding.rowBootAutoStart.value.setText(bootStart[which]);
            dialog.dismiss();
        }).setNegativeButton(R.string.dialog_negative, null).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBinding != null) mBinding.rowRemoteAccess.value.setText(Setting.getSwitch(RemoteAccess.isEnabled()));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) refresh();
    }
}
