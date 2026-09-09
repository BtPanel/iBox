package com.imedia.box.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.Product;
import com.imedia.box.R;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.bean.Config;
import com.imedia.box.bean.Keep;
import com.imedia.box.databinding.ActivityKeepBinding;
import com.imedia.box.db.AppDatabase;
import com.imedia.box.event.RefreshEvent;
import com.imedia.box.impl.Callback;
import com.imedia.box.library.KeepLibrary;
import com.imedia.box.setting.Setting;
import com.imedia.box.ui.adapter.KeepAdapter;
import com.imedia.box.ui.base.BaseActivity;
import com.imedia.box.ui.custom.AppNavigation;
import com.imedia.box.ui.custom.CustomTextListener;
import com.imedia.box.ui.dialog.SyncDialog;
import com.imedia.box.utils.Notify;
import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeepActivity extends BaseActivity implements KeepAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mAdapter;
    private List<Keep> allItems = Collections.emptyList();
    private int mRequestId;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityKeepBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setSupportActionBar(mBinding.toolbar);
        AppNavigation.bind(this, AppNavigation.LIBRARY);
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
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn(this)));
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this));
        mAdapter.setSize(Product.getSpec(this));
    }

    private void getKeep() {
        int requestId = ++mRequestId;
        AppDatabase.queryAsync(Keep::getVod, items -> {
            if (requestId != mRequestId || isFinishing() || isDestroyed()) return;
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
        mBinding.sort.setContentDescription(getString(R.string.keep_sort_current, current));
    }

    private void onSync() {
        SyncDialog.create().keep().show(this);
    }

    private void onDelete() {
        if (mAdapter.isDelete()) {
            new IBoxAlertDialogBuilder(this).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_keep).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                allItems = Collections.emptyList();
                mAdapter.clear();
                mBinding.progressLayout.showEmpty();
            }).show();
        } else if (mAdapter.getItemCount() > 0) {
            mAdapter.setDelete(true);
        }
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
        if (event.getType().equals(RefreshEvent.Type.KEEP)) getKeep();
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) SearchActivity.start(this, item.getVodName());
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
        mAdapter.setDelete(!mAdapter.isDelete());
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_keep, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        else if (item.getItemId() == R.id.delete) onDelete();
        else if (item.getItemId() == R.id.sync) onSync();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackInvoked() {
        if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else if (mBinding.keyword.getText() != null && mBinding.keyword.length() > 0) mBinding.keyword.setText("");
        else super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        mRequestId++;
        super.onDestroy();
    }
}
