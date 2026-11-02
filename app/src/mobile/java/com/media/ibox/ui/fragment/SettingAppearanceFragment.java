package com.media.ibox.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.FragmentSettingAppearanceBinding;
import com.media.ibox.databinding.ViewSettingRowBinding;
import com.media.ibox.event.RefreshEvent;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.media.ibox.ui.dialog.AccessibilityDialog;
import com.media.ibox.ui.dialog.CustomColorDialog;
import com.media.ibox.utils.ResUtil;

/**
 * 二级页：外观与布局。主题、布局、色彩、首页内容、图片尺寸、无障碍。
 */
public class SettingAppearanceFragment extends BaseFragment implements CustomColorDialog.Listener {

    private FragmentSettingAppearanceBinding mBinding;
    private String[] size;
    private String[] themeMode;
    private String[] layoutMode;
    private String[] homeContent;

    public static SettingAppearanceFragment newInstance() {
        return new SettingAppearanceFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingAppearanceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        bindBackNavigation(mBinding.toolbar);
        initRow(mBinding.rowThemeMode, R.drawable.ic_setting_theme_mode, R.string.setting_theme_mode);
        initRow(mBinding.rowLayoutMode, R.drawable.ic_msr_grid_view, R.string.setting_layout_mode);
        initRow(mBinding.rowThemeColor, R.drawable.ic_setting_palette, R.string.setting_theme_color);
        initRow(mBinding.rowHomeContent, R.drawable.ic_msr_home, R.string.home_content);
        initRow(mBinding.rowSize, R.drawable.ic_nav_aspect, R.string.setting_size);
        initRow(mBinding.rowAccessibility, R.drawable.ic_msr_accessibility_new, R.string.accessibility_title);
        mBinding.rowAccessibility.summary.setText(R.string.accessibility_setting_summary);
        mBinding.rowAccessibility.summary.setVisibility(View.VISIBLE);
        mBinding.rowAccessibility.chevron.setVisibility(View.VISIBLE);
        mBinding.rowHomeContent.summary.setText(R.string.home_content_summary);
        mBinding.rowHomeContent.summary.setVisibility(View.VISIBLE);
        refresh();
    }

    private void initRow(ViewSettingRowBinding row, int icon, int title) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
        row.value.setVisibility(View.VISIBLE);
    }

    private void refresh() {
        if (mBinding == null) return;
        themeMode = ResUtil.getStringArray(R.array.select_theme_mode);
        layoutMode = ResUtil.getStringArray(R.array.select_layout_mode);
        homeContent = ResUtil.getStringArray(R.array.select_home_content);
        size = ResUtil.getStringArray(R.array.select_size);
        mBinding.rowThemeMode.value.setText(themeMode[Setting.getThemeMode()]);
        mBinding.rowLayoutMode.value.setText(layoutMode[Setting.getLayoutMode()]);
        mBinding.rowThemeColor.value.setText(getString(Setting.getThemeColor() == -1 ? R.string.setting_off : R.string.setting_custom));
        mBinding.rowHomeContent.value.setText(homeContent[Setting.getHomeContent()]);
        mBinding.rowSize.value.setText(size[PlayerSetting.getSize()]);
    }

    @Override
    protected void initEvent() {
        mBinding.rowThemeMode.getRoot().setOnClickListener(this::setThemeMode);
        mBinding.rowLayoutMode.getRoot().setOnClickListener(this::setLayoutMode);
        mBinding.rowThemeColor.getRoot().setOnClickListener(view -> CustomColorDialog.show(this, Setting.getLastThemeColor()));
        mBinding.rowHomeContent.getRoot().setOnClickListener(this::setHomeContent);
        mBinding.rowSize.getRoot().setOnClickListener(this::setSize);
        mBinding.rowAccessibility.getRoot().setOnClickListener(view -> AccessibilityDialog.show(requireActivity(), () -> {
            if (!Setting.applyThemeMode()) requireActivity().recreate();
        }));
    }

    private void setThemeMode(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_theme_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(themeMode, Setting.getThemeMode(), (dialog, which) -> {
            Setting.putThemeMode(which);
            mBinding.rowThemeMode.value.setText(themeMode[which]);
            dialog.dismiss();
            Setting.applyThemeMode();
        }).show();
    }

    private void setLayoutMode(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_layout_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(layoutMode, Setting.getLayoutMode(), (dialog, which) -> {
            Setting.putLayoutMode(which);
            mBinding.rowLayoutMode.value.setText(layoutMode[which]);
            dialog.dismiss();
            requireActivity().recreate();
        }).show();
    }

    private void setHomeContent(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.home_content).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(homeContent, Setting.getHomeContent(), (dialog, which) -> {
            Setting.putHomeContent(which);
            mBinding.rowHomeContent.value.setText(homeContent[which]);
            RefreshEvent.homeMode();
            dialog.dismiss();
        }).show();
    }

    private void setSize(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_size).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(size, PlayerSetting.getSize(), (dialog, which) -> {
            mBinding.rowSize.value.setText(size[which]);
            PlayerSetting.putSize(which);
            RefreshEvent.size();
            dialog.dismiss();
        }).show();
    }

    @Override
    public void setTheme(int color) {
        Setting.putThemeColor(color);
        if (mBinding != null) mBinding.rowThemeColor.value.setText(getString(Setting.getThemeColor() == -1 ? R.string.setting_off : R.string.setting_custom));
        RefreshEvent.theme();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) refresh();
    }
}
