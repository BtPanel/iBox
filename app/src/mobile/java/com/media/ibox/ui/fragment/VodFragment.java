package com.media.ibox.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.media.ibox.R;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.bean.Class;
import com.media.ibox.bean.Config;
import com.media.ibox.bean.History;
import com.media.ibox.bean.Keep;
import com.media.ibox.bean.Result;
import com.media.ibox.bean.Site;
import com.media.ibox.bean.Value;
import com.media.ibox.databinding.FragmentVodBinding;
import com.media.ibox.db.AppDatabase;
import com.media.ibox.event.CastEvent;
import com.media.ibox.event.ConfigEvent;
import com.media.ibox.event.RefreshEvent;
import com.media.ibox.event.StateEvent;
import com.media.ibox.impl.Callback;
import com.media.ibox.impl.ConfigListener;
import com.media.ibox.impl.FilterListener;
import com.media.ibox.impl.SiteListener;
import com.media.ibox.model.SiteViewModel;
import com.media.ibox.ui.activity.HistoryActivity;
import com.media.ibox.ui.activity.HomeActivity;
import com.media.ibox.ui.activity.KeepActivity;
import com.media.ibox.ui.activity.SearchActivity;
import com.media.ibox.ui.activity.VideoActivity;
import com.media.ibox.ui.adapter.TypeAdapter;
import com.media.ibox.ui.adapter.HistoryAdapter;
import com.media.ibox.ui.adapter.KeepAdapter;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.ui.dialog.FilterDialog;
import com.media.ibox.ui.dialog.LinkDialog;
import com.media.ibox.ui.dialog.ReceiveDialog;
import com.media.ibox.ui.dialog.RepositoryDialog;
import com.media.ibox.ui.dialog.SiteDialog;
import com.media.ibox.utils.ImgUtil;
import com.media.ibox.utils.DisplayText;
import com.media.ibox.utils.Notify;
import com.media.ibox.Product;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class VodFragment extends BaseFragment implements ConfigListener, SiteListener, FilterListener, TypeAdapter.OnClickListener, HistoryAdapter.OnClickListener, KeepAdapter.OnClickListener {

    private FragmentVodBinding mBinding;
    private SiteViewModel mViewModel;
    private TypeAdapter mAdapter;
    private Result mResult;
    private HistoryAdapter mHistoryAdapter;
    private KeepAdapter mKeepAdapter;
    private boolean initialized;
    private int mLibraryRequestId;

    public static VodFragment newInstance() {
        return new VodFragment();
    }

    private FolderFragment getFragment() {
        return (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        initialized = true;
        EventBus.getDefault().register(this);
        mBinding.title.setSelected(true);
        setRecyclerView();
        setLibraryRecyclerView();
        setViewModel();
        showSourceProgress();
        setTitle();
        setLogo();
        if (Setting.getHomeContent() != Setting.HOME_RECOMMEND) {
            setHomeContent();
        } else if (!VodConfig.get().isInitialized()) {
            hideContent();
        } else if (!hasVodSource()) {
            showEmpty("");
        } else if (!VodConfig.get().getSites().isEmpty()) {
            setHomeContent();
        } else {
            hideContent();
        }
    }

    @Override
    protected void initEvent() {
        mBinding.top.setOnClickListener(this::onTop);
        mBinding.repository.setOnClickListener(view -> RepositoryDialog.create().vod().show(this));
        mBinding.search.setOnClickListener(view -> SearchActivity.start(requireActivity()));
        mBinding.keep.setOnClickListener(view -> KeepActivity.start(requireActivity()));
        mBinding.history.setOnClickListener(view -> HistoryActivity.start(requireActivity()));
        mBinding.source.setOnClickListener(this::onSite);
        mBinding.linkQuick.setOnClickListener(this::onLink);
        mBinding.filter.setOnClickListener(this::onFilter);
        mBinding.filter.setOnLongClickListener(this::onLink);
        mBinding.emptyRetry.setOnClickListener(view -> onEmptyAction());
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.type.smoothScrollToPosition(position);
                mAdapter.setSelected(position);
                setFabVisible(position);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.type.setHasFixedSize(true);
        mBinding.type.setItemAnimator(null);
        mBinding.type.setAdapter(mAdapter = new TypeAdapter(this));
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    private void setLibraryRecyclerView() {
        mHistoryAdapter = new HistoryAdapter(this);
        mKeepAdapter = new KeepAdapter(this);
        updateLibraryLayout();
        mBinding.libraryRecycler.setHasFixedSize(true);
    }

    private void updateLibraryLayout() {
        int[] size = Product.getSpec(requireActivity());
        mHistoryAdapter.setSize(size);
        mKeepAdapter.setSize(size);
        mBinding.libraryRecycler.setLayoutManager(new GridLayoutManager(requireActivity(), Product.getColumn(requireActivity())));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
    }

    private void setAdapter(Result result) {
        mAdapter.addAll(mResult = result);
        mBinding.pager.getAdapter().notifyDataSetChanged();
        if (Setting.getHomeContent() != Setting.HOME_RECOMMEND) return;
        hideProgress();
        if (hasHomeContent(result)) {
            hideEmpty();
            setFabVisible(0);
            showContent();
        } else {
            showEmpty(result.getMsg());
        }
    }

    private void setFabVisible(int position) {
        if (mAdapter.getItemCount() == 0) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.linkArea.setVisibility(View.VISIBLE);
            mBinding.filter.setVisibility(View.GONE);
        } else if (!mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.linkArea.setVisibility(View.GONE);
            mBinding.filter.setVisibility(View.VISIBLE);
        } else if (position == 0 || mAdapter.get(position).getFilters().isEmpty()) {
            mBinding.top.setVisibility(View.INVISIBLE);
            mBinding.filter.setVisibility(View.GONE);
            mBinding.linkArea.setVisibility(View.VISIBLE);
        }
    }

    private void setTitle() {
        String title = DisplayText.withoutEyeDecoration(getHome().getName());
        mBinding.title.setText(TextUtils.isEmpty(title) ? getString(R.string.app_name) : title);
    }

    private void onTop(View view) {
        getFragment().scrollToTop();
        mBinding.top.setVisibility(View.INVISIBLE);
        if (mBinding.filter.getVisibility() == View.INVISIBLE) mBinding.filter.show();
        else if (mBinding.linkArea.getVisibility() == View.INVISIBLE) mBinding.linkArea.setVisibility(View.VISIBLE);
    }

    private boolean onLink(View view) {
        LinkDialog.show(this);
        return true;
    }

    private void onSite(View view) {
        SiteDialog.create().change().show(this);
    }

    private void onFilter(View view) {
        if (mAdapter.getItemCount() > 0) FilterDialog.create().filter(mAdapter.get(mBinding.pager.getCurrentItem()).getFilters()).show(this);
    }

    private void showProgress() {
        showProgress(R.string.home_content_loading);
    }

    private void showSourceProgress() {
        showProgress(R.string.home_source_loading);
    }

    private void showProgress(@StringRes int message) {
        mBinding.progress.status.setText(message);
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        mBinding.filter.setVisibility(View.GONE);
        mBinding.linkArea.setVisibility(View.VISIBLE);
        mBinding.top.setVisibility(View.INVISIBLE);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void hideContent() {
        mBinding.type.setVisibility(View.INVISIBLE);
        mBinding.pager.setVisibility(View.INVISIBLE);
    }

    private void showContent() {
        mBinding.type.setVisibility(View.VISIBLE);
        mBinding.pager.setVisibility(View.VISIBLE);
    }

    private boolean hasHomeContent(Result result) {
        return !result.getTypes().isEmpty() || !result.getList().isEmpty();
    }

    private void hideEmpty() {
        mBinding.emptyPanel.setVisibility(View.GONE);
    }

    private void showEmpty(String message) {
        hideProgress();
        hideContent();
        mBinding.filter.setVisibility(View.GONE);
        mBinding.linkArea.setVisibility(View.GONE);
        mBinding.top.setVisibility(View.INVISIBLE);
        boolean hasSource = hasVodSource();
        mBinding.emptyText.setText(hasSource
                ? TextUtils.isEmpty(message) ? getString(R.string.home_load_empty) : message
                : getString(R.string.home_source_empty));
        mBinding.emptyRetry.setText(hasSource ? R.string.home_load_retry : R.string.home_source_add);
        mBinding.emptyPanel.setVisibility(View.VISIBLE);
    }

    private boolean hasVodSource() {
        return VodConfig.get().isInitialized() && !VodConfig.get().getConfig().isEmpty();
    }

    private void onEmptyAction() {
        if (!hasVodSource()) {
            RepositoryDialog.create().vod().show(this);
        } else if (VodConfig.get().getSites().isEmpty()) {
            ((HomeActivity) requireActivity()).reloadVodConfig();
        } else {
            homeContent();
        }
    }

    private void homeContent() {
        if (!hasVodSource()) {
            showEmpty("");
            return;
        }
        hideEmpty();
        showProgress();
        mAdapter.clear();
        hideContent();
        mViewModel.homeContent();
        mBinding.pager.setAdapter(new PageAdapter(getChildFragmentManager()));
    }

    private void setHomeContent() {
        int content = Setting.getHomeContent();
        boolean recommend = content == Setting.HOME_RECOMMEND;
        hideEmpty();
        mBinding.type.setVisibility(recommend ? View.VISIBLE : View.GONE);
        mBinding.pager.setVisibility(recommend ? View.VISIBLE : View.GONE);
        mBinding.libraryPanel.setVisibility(recommend ? View.GONE : View.VISIBLE);
        mBinding.filter.setVisibility(View.GONE);
        mBinding.linkArea.setVisibility(recommend ? View.VISIBLE : View.GONE);
        mBinding.top.setVisibility(View.INVISIBLE);
        if (recommend) {
            homeContent();
        } else if (content == Setting.HOME_HISTORY) {
            hideProgress();
            showHistory();
        } else {
            hideProgress();
            showKeep();
        }
    }

    private void showHistory() {
        mBinding.libraryRecycler.setAdapter(mHistoryAdapter);
        int requestId = ++mLibraryRequestId;
        AppDatabase.queryAsync(History::get, items -> {
            if (!initialized || requestId != mLibraryRequestId || Setting.getHomeContent() != Setting.HOME_HISTORY) return;
            mHistoryAdapter.setItems(items, hasChange -> {
                mBinding.libraryEmpty.setText(R.string.home_empty_history);
                mBinding.libraryEmpty.setVisibility(mHistoryAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                if (hasChange) mBinding.libraryRecycler.scrollToPosition(0);
            });
        });
    }

    private void showKeep() {
        mBinding.libraryRecycler.setAdapter(mKeepAdapter);
        int requestId = ++mLibraryRequestId;
        AppDatabase.queryAsync(Keep::getVod, items -> {
            if (!initialized || requestId != mLibraryRequestId || Setting.getHomeContent() != Setting.HOME_KEEP) return;
            mKeepAdapter.setItems(items, () -> {
                mBinding.libraryEmpty.setText(R.string.home_empty_keep);
                mBinding.libraryEmpty.setVisibility(mKeepAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            });
        });
    }

    public Result getResult() {
        return mResult == null ? new Result() : mResult;
    }

    private void setLogo() {
        ImgUtil.logo(mBinding.logo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.VOD) return;
        setTitle();
        setLogo();
        setHomeContent();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                setTitle();
                setHomeContent();
                break;
            case HOME_MODE:
                setHomeContent();
                break;
            case HISTORY:
                if (Setting.getHomeContent() == Setting.HOME_HISTORY) showHistory();
                break;
            case KEEP:
                if (Setting.getHomeContent() == Setting.HOME_KEEP) showKeep();
                break;
            case SIZE:
                updateLibraryLayout();
                setHomeContent();
                break;
            case CATEGORY:
                if (Setting.getHomeContent() == Setting.HOME_RECOMMEND) getFragment().onRefresh();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStateEvent(StateEvent event) {
        switch (event.type()) {
            case EMPTY:
                hideProgress();
                if (Setting.getHomeContent() == Setting.HOME_RECOMMEND && (mResult == null || !hasHomeContent(mResult))) {
                    showEmpty("");
                }
                break;
            case PROGRESS:
                hideEmpty();
                hideContent();
                showSourceProgress();
                break;
            case ERROR:
                showEmpty(TextUtils.isEmpty(event.message()) ? getString(R.string.home_load_failed) : event.message());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        ReceiveDialog.create().event(event).show(this);
    }

    @Override
    public void setConfig(Config config) {
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
                hideEmpty();
                showSourceProgress();
                hideContent();
                setTitle();
                setLogo();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                if (!TextUtils.isEmpty(msg)) Notify.show(msg);
                showEmpty(TextUtils.isEmpty(msg) ? getString(R.string.home_load_failed) : msg);
            }
        });
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void onItemClick(int position, Class item) {
        mBinding.pager.setCurrentItem(position);
        mAdapter.setSelected(position);
    }

    @Override
    public void setFilter(String key, Value value) {
        getFragment().setFilter(key, value);
    }

    @Override
    public boolean canBack() {
        if (mBinding.pager.getAdapter() == null || mBinding.pager.getAdapter().getCount() == 0) return true;
        if (!getFragment().canBack()) return true;
        getFragment().goBack();
        return false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLibraryRequestId++;
        initialized = false;
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialized && Setting.getHomeContent() != Setting.HOME_RECOMMEND) setHomeContent();
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete(), this::showHistory);
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) SearchActivity.start(requireActivity(), item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
        else VideoActivity.start(requireActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        mKeepAdapter.remove(item.delete(), this::showKeep);
    }

    @Override
    public boolean onLongClick(History item) {
        int watched = item.isWatched() ? R.string.history_mark_unwatched : R.string.history_mark_watched;
        CharSequence[] actions = {getString(watched), getString(R.string.history_remove)};
        new IBoxAlertDialogBuilder(requireContext()).setTitle(item.getVodName()).setItems(actions, (dialog, which) -> {
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
    public boolean onLongClick() {
        return false;
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mAdapter.get(position);
            return FolderFragment.newInstance(getHome().getKey(), type, 4);
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

    }
}
