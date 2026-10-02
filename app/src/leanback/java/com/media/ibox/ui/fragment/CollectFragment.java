package com.media.ibox.ui.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.Product;
import com.media.ibox.R;
import com.media.ibox.bean.Collect;
import com.media.ibox.bean.Result;
import com.media.ibox.bean.Vod;
import com.media.ibox.databinding.FragmentTypeBinding;
import com.media.ibox.model.SiteViewModel;
import com.media.ibox.search.SearchFilter;
import com.media.ibox.search.SearchResultGroup;
import com.media.ibox.ui.activity.VideoActivity;
import com.media.ibox.ui.activity.VodActivity;
import com.media.ibox.ui.adapter.BaseDiffCallback;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.ui.custom.CustomRowPresenter;
import com.media.ibox.ui.custom.CustomScroller;
import com.media.ibox.ui.custom.CustomSelector;
import com.media.ibox.ui.presenter.SearchResultPresenter;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class CollectFragment extends BaseFragment implements CustomScroller.Callback, SearchResultPresenter.OnClickListener {

    private FragmentTypeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private final List<ArrayObjectAdapter> mRows = new ArrayList<>();
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private Collect mCollect;
    private String mKeyword;
    private String mType;
    private String mYear;

    public static CollectFragment newInstance(String keyword, Collect collect, String type, String year) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("type", type);
        args.putString("year", year);
        CollectFragment fragment = new CollectFragment().setCollect(collect);
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return mKeyword = mKeyword == null ? getArguments().getString("keyword") : mKeyword;
    }

    private CollectFragment setCollect(Collect collect) {
        this.mCollect = collect;
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentTypeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mType = getArguments().getString("type", "");
        mYear = getArguments().getString("year", "");
        setRecyclerView();
        setViewModel();
        addVideo(mCollect);
    }

    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), SearchResultPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.addOnScrollListener(mScroller = new CustomScroller(this));
        mBinding.recycler.setHeader(getActivity(), R.id.result, R.id.recycler);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            mScroller.endLoading(result);
            mCollect.getList().addAll(result.getList());
            refresh();
        });
    }

    private void addVideo(Collect collect) {
        if (collect != null) refresh();
    }

    public void refresh() {
        if (getActivity() == null || getActivity().isFinishing() || mAdapter == null) return;
        List<SearchResultGroup> items = SearchResultGroup.aggregate(SearchFilter.apply(mCollect.getList(), mType, mYear));
        List<List<SearchResultGroup>> parts = Lists.partition(items, Product.getColumn());
        BaseDiffCallback<SearchResultGroup> diff = new BaseDiffCallback<>();
        for (int i = 0; i < parts.size(); i++) {
            if (i < mRows.size()) {
                mRows.get(i).setItems(parts.get(i), diff);
            } else {
                ArrayObjectAdapter row = new ArrayObjectAdapter(new SearchResultPresenter(this));
                row.addAll(0, parts.get(i));
                mRows.add(row);
                mAdapter.add(new ListRow(row));
            }
        }
        if (mRows.size() > parts.size()) {
            int removeCount = mRows.size() - parts.size();
            mAdapter.removeItems(parts.size(), removeCount);
            mRows.subList(parts.size(), mRows.size()).clear();
        }
    }

    public void setFilter(String type, String year) {
        mType = type;
        mYear = year;
        if (mBinding == null || mAdapter == null) return;
        refresh();
        mBinding.recycler.moveToTop();
    }

    @Override
    public void onItemClick(SearchResultGroup item) {
        if (item.variants().size() == 1) open(item.primary());
        else chooseSource(item);
    }

    private void chooseSource(SearchResultGroup group) {
        CharSequence[] labels = group.variants().stream().map(group::choiceLabel).toArray(CharSequence[]::new);
        new IBoxAlertDialogBuilder(requireActivity())
                .setTitle(R.string.search_choose_source)
                .setItems(labels, (dialog, which) -> open(group.variants().get(which)))
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void open(Vod item) {
        requireActivity().setResult(Activity.RESULT_OK);
        if (item.isFolder()) VodActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(SearchResultGroup item) {
        return false;
    }

    @Override
    public boolean onLoadMore(String page) {
        if (mCollect == null || "all".equals(mCollect.getSite().getKey())) return false;
        mViewModel.searchContent(mCollect.getSite(), getKeyword(), false, page);
        return true;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mBinding != null && !isVisibleToUser) mBinding.recycler.moveToTop();
    }
}
