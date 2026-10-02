package com.media.ibox.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;

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
import com.media.ibox.library.KeepLibrary;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.adapter.HistoryAdapter;
import com.media.ibox.ui.adapter.KeepAdapter;
import com.media.ibox.ui.base.BaseActivity;
import com.media.ibox.ui.custom.CustomTextListener;
import com.media.ibox.ui.custom.SpaceItemDecoration;
import com.media.ibox.ui.dialog.WebDavSyncDialog;
import com.media.ibox.utils.Notify;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LibraryActivity extends BaseActivity implements HistoryAdapter.OnClickListener, KeepAdapter.OnClickListener {

    private ActivityLibraryBinding mBinding;
    private HistoryAdapter mHistoryAdapter;
    private KeepAdapter mKeepAdapter;
    private List<History> histories = Collections.emptyList();
    private List<Keep> keeps = Collections.emptyList();
    private boolean keep;
    private boolean firstLoad = true;
    private int libraryRequestId;

    public static void start(Activity activity, boolean keep) {
        activity.startActivity(new Intent(activity, LibraryActivity.class).putExtra("keep", keep));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLibraryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        keep = getIntent().getBooleanExtra("keep", false);
        mHistoryAdapter = new HistoryAdapter(this);
        mKeepAdapter = new KeepAdapter(this);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
        mBinding.tabs.check(keep ? R.id.keepTab : R.id.historyTab);
        showCurrent();
    }

    @Override
    protected void initEvent() {
        mBinding.tabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            keep = checkedId == R.id.keepTab;
            exitDeleteMode();
            showCurrent();
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable editable) {
                applyFilter();
            }
        });
        mBinding.sort.setOnClickListener(view -> showSortDialog());
        mBinding.sync.setOnClickListener(view -> WebDavSyncDialog.show(this, this::reload));
        mBinding.manage.setOnClickListener(view -> toggleDeleteMode());
    }

    private void showCurrent() {
        mBinding.keyword.setHint(keep ? R.string.keep_search_hint : R.string.library_history_search_hint);
        mBinding.sort.setVisibility(keep ? View.VISIBLE : View.GONE);
        mBinding.recycler.setAdapter(keep ? mKeepAdapter : mHistoryAdapter);
        updateManageButton();
        reload();
    }

    private void reload() {
        int requestId = ++libraryRequestId;
        if (keep) {
            AppDatabase.queryAsync(Keep::getVod, items -> {
                if (!keep || requestId != libraryRequestId || isFinishing() || isDestroyed()) return;
                keeps = items;
                applyFilter();
            });
        } else {
            AppDatabase.queryAsync(History::get, items -> {
                if (keep || requestId != libraryRequestId || isFinishing() || isDestroyed()) return;
                histories = items;
                applyFilter();
            });
        }
    }

    private String keyword() {
        return mBinding.keyword.getText() == null ? "" : mBinding.keyword.getText().toString().trim().toLowerCase(Locale.ROOT);
    }

    private void applyFilter() {
        if (keep) {
            List<Keep> items = KeepLibrary.filterAndSort(keeps, keyword(), Setting.getKeepSortMode());
            mKeepAdapter.setItems(items, this::showContent);
        } else {
            String keyword = keyword();
            List<History> items = keyword.isEmpty() ? histories : histories.stream().filter(item ->
                    item.getVodName().toLowerCase(Locale.ROOT).contains(keyword)
                            || item.getVodRemarks().toLowerCase(Locale.ROOT).contains(keyword)
                            || item.getSiteName().toLowerCase(Locale.ROOT).contains(keyword)).toList();
            mHistoryAdapter.setItems(items, changed -> showContent());
        }
    }

    private void showContent() {
        int count = keep ? mKeepAdapter.getItemCount() : mHistoryAdapter.getItemCount();
        mBinding.progressLayout.showContent(true, count);
        if (firstLoad) {
            firstLoad = false;
            mBinding.recycler.post(() -> {
                if (count > 0) mBinding.recycler.requestFocus();
                else mBinding.historyTab.requestFocus();
            });
        }
    }

    private void showSortDialog() {
        String[] options = getResources().getStringArray(R.array.keep_sort_options);
        new IBoxAlertDialogBuilder(this)
                .setTitle(R.string.keep_sort)
                .setSingleChoiceItems(options, Setting.getKeepSortMode(), (dialog, which) -> {
                    Setting.putKeepSortMode(which);
                    applyFilter();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void toggleDeleteMode() {
        if (keep) mKeepAdapter.setDelete(!mKeepAdapter.isDelete());
        else mHistoryAdapter.setDelete(!mHistoryAdapter.isDelete());
        updateManageButton();
        if (mBinding.recycler.getAdapter().getItemCount() > 0) mBinding.recycler.requestFocus();
    }

    private void exitDeleteMode() {
        mKeepAdapter.setDelete(false);
        mHistoryAdapter.setDelete(false);
        updateManageButton();
    }

    private void updateManageButton() {
        boolean deleting = keep ? mKeepAdapter.isDelete() : mHistoryAdapter.isDelete();
        mBinding.manage.setText(deleting ? R.string.library_done : R.string.library_manage);
        mBinding.manage.setIconResource(deleting ? R.drawable.ic_msr_check_circle : R.drawable.ic_msr_delete);
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
        item.delete();
        reload();
    }

    @Override
    public boolean onLongClick(History item) {
        int watched = item.isWatched() ? R.string.history_mark_unwatched : R.string.history_mark_watched;
        CharSequence[] actions = {getString(watched), getString(R.string.history_remove)};
        new IBoxAlertDialogBuilder(this).setTitle(item.getVodName()).setItems(actions, (dialog, which) -> {
            if (which == 0) item.markWatched(!item.isWatched());
            else item.delete();
            reload();
            RefreshEvent.history();
        }).show();
        return true;
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
        reload();
    }

    @Override
    public boolean onLongClick() {
        toggleDeleteMode();
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.HISTORY && !keep) reload();
        if (event.getType() == RefreshEvent.Type.KEEP && keep) reload();
    }

    @Override
    protected void onBackInvoked() {
        if (mHistoryAdapter.isDelete() || mKeepAdapter.isDelete()) exitDeleteMode();
        else if (mBinding.keyword.length() > 0) mBinding.keyword.setText("");
        else super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        libraryRequestId++;
        super.onDestroy();
    }
}
