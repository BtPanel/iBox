package com.media.ibox.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.Product;
import com.media.ibox.R;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.bean.Config;
import com.media.ibox.bean.History;
import com.media.ibox.bean.Keep;
import com.media.ibox.databinding.ActivityLibraryBinding;
import com.media.ibox.db.AppDatabase;
import com.media.ibox.event.RefreshEvent;
import com.media.ibox.impl.Callback;
import com.media.ibox.ui.adapter.HistoryAdapter;
import com.media.ibox.ui.adapter.KeepAdapter;
import com.media.ibox.ui.base.BaseActivity;
import com.media.ibox.ui.custom.AppNavigation;
import com.media.ibox.ui.dialog.SyncDialog;
import com.media.ibox.utils.Notify;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class LibraryActivity extends BaseActivity implements HistoryAdapter.OnClickListener, KeepAdapter.OnClickListener {

    private ActivityLibraryBinding mBinding;
    private HistoryAdapter mHistoryAdapter;
    private KeepAdapter mKeepAdapter;
    private boolean keep;
    private int mLibraryRequestId;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, LibraryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLibraryBinding.inflate(getLayoutInflater());
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
        mHistoryAdapter = new HistoryAdapter(this);
        mKeepAdapter = new KeepAdapter(this);
        int[] size = Product.getSpec(this);
        mHistoryAdapter.setSize(size);
        mKeepAdapter.setSize(size);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn(this)));
        mBinding.tabs.addTab(mBinding.tabs.newTab().setText(R.string.app_history));
        mBinding.tabs.addTab(mBinding.tabs.newTab().setText(R.string.app_keep));
        showHistory();
    }

    @Override
    protected void initEvent() {
        mBinding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) showHistory();
                else showKeep();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                mBinding.recycler.scrollToPosition(0);
            }
        });
    }

    private void showHistory() {
        keep = false;
        mKeepAdapter.setDelete(false);
        mBinding.recycler.setAdapter(mHistoryAdapter);
        getHistory();
    }

    private void showKeep() {
        keep = true;
        mHistoryAdapter.setDelete(false);
        mBinding.recycler.setAdapter(mKeepAdapter);
        getKeep();
    }

    private void getHistory() {
        int requestId = ++mLibraryRequestId;
        AppDatabase.queryAsync(History::get, items -> {
            if (keep || requestId != mLibraryRequestId || isFinishing() || isDestroyed()) return;
            mHistoryAdapter.setItems(items, hasChange -> {
                mBinding.progressLayout.showContent(true, mHistoryAdapter.getItemCount());
                if (hasChange) mBinding.recycler.scrollToPosition(0);
            });
        });
    }

    private void getKeep() {
        int requestId = ++mLibraryRequestId;
        AppDatabase.queryAsync(Keep::getVod, items -> {
            if (!keep || requestId != mLibraryRequestId || isFinishing() || isDestroyed()) return;
            mKeepAdapter.setItems(items, () -> mBinding.progressLayout.showContent(true, mKeepAdapter.getItemCount()));
        });
    }

    private void onSync() {
        if (keep) SyncDialog.create().keep().show(this);
        else SyncDialog.create().history().show(this);
    }

    private void onDelete() {
        if (keep && mKeepAdapter.isDelete()) {
            confirmDelete(R.string.dialog_delete_keep, mKeepAdapter::clear);
        } else if (!keep && mHistoryAdapter.isDelete()) {
            confirmDelete(R.string.dialog_delete_history, mHistoryAdapter::clear);
        } else if (keep && mKeepAdapter.getItemCount() > 0) {
            mKeepAdapter.setDelete(true);
        } else if (!keep && mHistoryAdapter.getItemCount() > 0) {
            mHistoryAdapter.setDelete(true);
        }
    }

    private void confirmDelete(int message, Runnable action) {
        new IBoxAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_record)
                .setMessage(message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    action.run();
                    mBinding.progressLayout.showContent(true, 0);
                })
                .show();
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

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete(), () -> {
            if (mHistoryAdapter.getItemCount() == 0) mHistoryAdapter.setDelete(false);
        });
    }

    @Override
    public boolean onLongClick(History item) {
        int watched = item.isWatched() ? R.string.history_mark_unwatched : R.string.history_mark_watched;
        CharSequence[] actions = {getString(watched), getString(R.string.history_remove)};
        new IBoxAlertDialogBuilder(this).setTitle(item.getVodName()).setItems(actions, (dialog, which) -> {
            if (which == 0) {
                item.markWatched(!item.isWatched());
                RefreshEvent.history();
            } else {
                onItemDelete(item);
            }
        }).show();
        return true;
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
        mKeepAdapter.remove(item.delete(), () -> {
            if (mKeepAdapter.getItemCount() == 0) mKeepAdapter.setDelete(false);
        });
    }

    @Override
    public boolean onLongClick() {
        if (keep) mKeepAdapter.setDelete(!mKeepAdapter.isDelete());
        else mHistoryAdapter.setDelete(!mHistoryAdapter.isDelete());
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.HISTORY && !keep) getHistory();
        if (event.getType() == RefreshEvent.Type.KEEP && keep) getKeep();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_library, menu);
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
        if (keep && mKeepAdapter.isDelete()) mKeepAdapter.setDelete(false);
        else if (!keep && mHistoryAdapter.isDelete()) mHistoryAdapter.setDelete(false);
        else super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        mLibraryRequestId++;
        super.onDestroy();
    }
}
