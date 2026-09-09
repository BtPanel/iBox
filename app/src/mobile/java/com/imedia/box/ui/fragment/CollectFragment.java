package com.imedia.box.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.api.config.VodConfig;
import com.imedia.box.Product;
import com.imedia.box.R;
import com.imedia.box.bean.Collect;
import com.imedia.box.bean.Result;
import com.imedia.box.bean.Site;
import com.imedia.box.bean.Vod;
import com.imedia.box.databinding.FragmentCollectBinding;
import com.imedia.box.model.SiteViewModel;
import com.imedia.box.search.SearchFilter;
import com.imedia.box.search.SearchResultGroup;
import com.imedia.box.ui.activity.FolderActivity;
import com.imedia.box.ui.activity.VideoActivity;
import com.imedia.box.ui.adapter.CollectAdapter;
import com.imedia.box.ui.adapter.SearchAdapter;
import com.imedia.box.ui.base.BaseFragment;
import com.imedia.box.ui.custom.CustomScroller;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class CollectFragment extends BaseFragment implements MenuProvider, CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private FragmentCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private String selectedType = "";
    private String selectedYear = "";

    public static CollectFragment newInstance(String keyword, boolean allSources) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putBoolean("allSources", allSources);
        CollectFragment fragment = new CollectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private boolean isAllSources() {
        return getArguments().getBoolean("allSources", true);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initMenu() {
        if (isHidden()) return;
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(mBinding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        activity.setTitle(getKeyword());
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        setRecyclerView();
        setViewModel();
        setSites();
        setWidth();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setOnClickListener(v -> editSearch());
        mBinding.typeFilter.setOnClickListener(view -> selectType());
        mBinding.yearFilter.setOnClickListener(view -> selectYear());
    }

    private void setRecyclerView() {
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
        ((GridLayoutManager) (mBinding.recycler.getLayoutManager())).setSpanCount(getCount());
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class).init();
        mViewModel.getSearch().observe(getViewLifecycleOwner(), this::setCollect);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setSearch);
    }

    private void setSites() {
        if (isAllSources()) mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).toList();
        else mSites = VodConfig.get().getHome().isSearchable() ? List.of(VodConfig.get().getHome()) : List.of();
    }

    private void setWidth() {
        int width = 0;
        int space = ResUtil.dp2px(48);
        int maxWidth = ResUtil.getScreenWidth() / (getCount() + 1) - ResUtil.dp2px(40);
        for (Site site : mSites) width = Math.max(width, ResUtil.getTextWidth(site.getName(), 14));
        int contentWidth = width + space;
        int minWidth = ResUtil.dp2px(120);
        int finalWidth = Math.clamp(contentWidth, minWidth, Math.max(minWidth, maxWidth));
        ViewGroup.LayoutParams params = mBinding.collect.getLayoutParams();
        params.width = finalWidth;
        mBinding.collect.setLayoutParams(params);
    }

    private void search() {
        if (mSites.isEmpty()) return;
        mCollectAdapter.setItems(List.of(Collect.all()), () -> {
            if (mBinding == null) return;
            mViewModel.searchContent(mSites, getKeyword(), false);
        });
    }

    private int getCount() {
        return Product.getSearchColumn(requireActivity());
    }

    private void editSearch() {
        Bundle result = new Bundle();
        result.putBoolean("edit", true);
        getParentFragmentManager().setFragmentResult("result", result);
        getParentFragmentManager().popBackStack();
    }

    private void setCollect(Result result) {
        if (result == null || result.getList().isEmpty()) return;
        mCollectAdapter.add(Collect.create(result.getList()));
        mCollectAdapter.add(result.getList());
        updateFilterState();
        if (mCollectAdapter.getPosition() == 0) refreshResults(false);
    }

    private void setSearch(Result result) {
        if (result == null) return;
        mScroller.endLoading(result);
        Collect activated = mCollectAdapter.getActivated();
        boolean same = activated != null && result.getVod() != null && !result.getList().isEmpty() && activated.getSite().equals(result.getVod().getSite());
        if (same) activated.getList().addAll(result.getList());
        if (same) refreshResults(false);
    }

    @Override
    public void onItemClick(int position, Collect item) {
        mCollectAdapter.setSelected(position);
        mScroller.setPage(item.getPage());
        refreshResults(true);
    }

    private void selectType() {
        Collect activated = mCollectAdapter.getActivated();
        if (activated == null) return;
        List<String> values = new ArrayList<>();
        values.add("");
        values.addAll(SearchFilter.types(activated.getList()));
        showFilter(R.string.search_filter_type, values, selectedType, value -> {
            selectedType = value;
            applyFilters();
        });
    }

    private void selectYear() {
        Collect activated = mCollectAdapter.getActivated();
        if (activated == null) return;
        List<String> values = new ArrayList<>();
        values.add("");
        values.addAll(SearchFilter.years(activated.getList()));
        showFilter(R.string.search_filter_year, values, selectedYear, value -> {
            selectedYear = value;
            applyFilters();
        });
    }

    private void showFilter(int title, List<String> values, String selected, java.util.function.Consumer<String> consumer) {
        CharSequence[] labels = new CharSequence[values.size()];
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).isEmpty()) labels[i] = getString(title == R.string.search_filter_type ? R.string.search_filter_all_types : R.string.search_filter_all_years);
            else labels[i] = title == R.string.search_filter_type ? SearchFilter.typeLabel(values.get(i)) : values.get(i);
        }
        int checked = Math.max(0, values.indexOf(selected));
        new IBoxAlertDialogBuilder(requireActivity())
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    consumer.accept(values.get(which));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void applyFilters() {
        refreshResults(true);
        mBinding.typeFilter.setText(selectedType.isEmpty() ? getString(R.string.search_filter_all_types) : getString(R.string.search_filter_value, getString(R.string.search_filter_type), SearchFilter.typeLabel(selectedType)));
        mBinding.yearFilter.setText(selectedYear.isEmpty() ? getString(R.string.search_filter_all_years) : getString(R.string.search_filter_value, getString(R.string.search_filter_year), selectedYear));
    }

    private void updateFilterState() {
        if (mBinding == null || mCollectAdapter.getItemCount() == 0) return;
        List<Vod> items = mCollectAdapter.getItem(0).getList();
        mBinding.typeFilter.setEnabled(!SearchFilter.types(items).isEmpty());
        mBinding.yearFilter.setEnabled(!SearchFilter.years(items).isEmpty());
    }

    private void refreshResults(boolean scrollToTop) {
        Collect activated = mCollectAdapter.getActivated();
        if (activated == null) return;
        List<Vod> filtered = SearchFilter.apply(activated.getList(), selectedType, selectedYear);
        mSearchAdapter.setItems(SearchResultGroup.aggregate(filtered), () -> {
            if (mBinding != null && scrollToTop) mBinding.recycler.scrollToPosition(0);
        });
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
        if (item.isFolder()) FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLoadMore(String page) {
        Collect activated = mCollectAdapter.getActivated();
        if (activated == null) return false;
        if ("all".equals(activated.getSite().getKey())) return false;
        mViewModel.searchContent(activated.getSite(), getKeyword(), false, page);
        activated.setPage(Integer.parseInt(page));
        return true;
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_search_result, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) requireActivity().getOnBackPressedDispatcher().onBackPressed();
        if (menuItem.getItemId() == R.id.action_search) editSearch();
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        if (mViewModel != null) mViewModel.stopSearch();
        if (isAdded()) requireActivity().removeMenuProvider(this);
        mBinding = null;
        super.onDestroyView();
    }
}
