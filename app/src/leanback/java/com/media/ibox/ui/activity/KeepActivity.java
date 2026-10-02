package com.media.ibox.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.Product;
import com.media.ibox.R;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.bean.Config;
import com.media.ibox.bean.Keep;
import com.media.ibox.databinding.ActivityKeepBinding;
import com.media.ibox.db.AppDatabase;
import com.media.ibox.event.RefreshEvent;
import com.media.ibox.impl.Callback;
import com.media.ibox.library.KeepLibrary;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.adapter.KeepAdapter;
import com.media.ibox.ui.base.BaseActivity;
import com.media.ibox.ui.custom.CustomTextListener;
import com.media.ibox.ui.custom.SpaceItemDecoration;
import com.media.ibox.utils.Notify;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeepActivity extends BaseActivity implements KeepAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mAdapter;
    private List<Keep> allItems = Collections.emptyList();
    private boolean firstLoad = true;
    private int requestId;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityKeepBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        getKeep();
    }

    @Override
    protected void initEvent() {
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable editable) {
                applyLibraryFilter();
            }
        });
        mBinding.sort.setOnClickListener(view -> showSortDialog());
        updateSortButton();
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getKeep() {
        int currentId = ++requestId;
        AppDatabase.queryAsync(Keep::getVod, items -> {
            if (currentId != requestId || isFinishing() || isDestroyed()) return;
            allItems = items;
            applyLibraryFilter();
        });
    }

    private void applyLibraryFilter() {
        String keyword = mBinding.keyword.getText() == null ? "" : mBinding.keyword.getText().toString();
        List<Keep> visibleItems = KeepLibrary.filterAndSort(allItems, keyword, Setting.getKeepSortMode());
        mAdapter.setItems(visibleItems, () -> {
            mBinding.progressLayout.showContent(true, mAdapter.getItemCount());
            if (mAdapter.getItemCount() == 0 && mAdapter.isDelete()) mAdapter.setDelete(false);
            if (firstLoad) {
                firstLoad = false;
                mBinding.recycler.post(() -> {
                    if (mAdapter.getItemCount() > 0) mBinding.recycler.requestFocus();
                    else mBinding.keyword.requestFocus();
                });
            }
        });
    }

    private void showSortDialog() {
        String[] options = getResources().getStringArray(R.array.keep_sort_options);
        new IBoxAlertDialogBuilder(this)
                .setTitle(R.string.keep_sort)
                .setSingleChoiceItems(options, Setting.getKeepSortMode(), (dialog, which) -> {
                    Setting.putKeepSortMode(which);
                    updateSortButton();
                    applyLibraryFilter();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void updateSortButton() {
        String current = getResources().getStringArray(R.array.keep_sort_options)[Setting.getKeepSortMode()];
        mBinding.sort.setText(current);
        mBinding.sort.setContentDescription(getString(R.string.keep_sort_current, current));
    }

    private void loadConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.KEEP) getKeep();
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) CollectActivity.start(this, item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        item.delete();
        allItems = new ArrayList<>(allItems);
        allItems.remove(item);
        applyLibraryFilter();
        if (allItems.isEmpty()) mAdapter.setDelete(false);
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(true);
        return true;
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else if (mBinding.keyword.getText() != null && mBinding.keyword.length() > 0) mBinding.keyword.setText("");
        else super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        requestId++;
        super.onDestroy();
    }
}
