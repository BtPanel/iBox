package com.media.ibox.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.media.ibox.App;
import com.media.ibox.R;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.bean.Collect;
import com.media.ibox.bean.Site;
import com.media.ibox.bean.Vod;
import com.media.ibox.databinding.ActivityCollectBinding;
import com.media.ibox.model.SiteViewModel;
import com.media.ibox.setting.Setting;
import com.media.ibox.search.SearchFilter;
import com.media.ibox.ui.adapter.CollectAdapter;
import com.media.ibox.ui.base.BaseActivity;
import com.media.ibox.ui.fragment.CollectFragment;
import com.media.ibox.utils.ResUtil;
import com.google.gson.reflect.TypeToken;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectActivity extends BaseActivity {

    private ActivityCollectBinding mBinding;
    private CollectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private View mOldView;
    private String selectedType = "";
    private String selectedYear = "";

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, true);
    }

    public static void start(Activity activity, String keyword, boolean allSources) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("allSources", allSources);
        activity.startActivity(intent);
    }

    private CollectFragment getFragment() {
        return (CollectFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, 0);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    private boolean isAllSources() {
        return getIntent().getBooleanExtra("allSources", true);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getIntent().putExtras(intent);
        mAdapter.clear();
        setPager();
        search();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        setViewModel();
        saveKeyword();
        setSites();
        setPager();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.typeFilter.setOnClickListener(view -> selectType());
        mBinding.yearFilter.setOnClickListener(view -> selectYear());
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mBinding.recycler.setSelectedPosition(position);
                mBinding.recycler.requestFocus();
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(mAdapter = new CollectAdapter());
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getSearch().observe(this, result -> {
            if (result.getList().isEmpty()) return;
            mAdapter.get(0).getList().addAll(result.getList());
            updateFilterState();
            getFragment().refresh();
            mAdapter.add(Collect.create(result.getList()));
            mBinding.pager.getAdapter().notifyDataSetChanged();
        });
    }

    private void saveKeyword() {
        List<String> items = Setting.getKeyword().isEmpty() ? new ArrayList<>() : App.gson().fromJson(Setting.getKeyword(), TypeToken.getParameterized(List.class, String.class).getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void setSites() {
        if (isAllSources()) mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).toList();
        else mSites = VodConfig.get().getHome().isSearchable() ? List.of(VodConfig.get().getHome()) : List.of();
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void search() {
        if (mSites.isEmpty()) return;
        mAdapter.add(Collect.all());
        mBinding.pager.getAdapter().notifyDataSetChanged();
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        mViewModel.searchContent(mSites, getKeyword(), false);
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        App.post(mRunnable, 100);
    }

    private void selectType() {
        List<String> values = new ArrayList<>();
        values.add("");
        values.addAll(SearchFilter.types(mAdapter.get(0).getList()));
        showFilter(R.string.search_filter_type, values, selectedType, value -> {
            selectedType = value;
            applyFilters();
        });
    }

    private void selectYear() {
        List<String> values = new ArrayList<>();
        values.add("");
        values.addAll(SearchFilter.years(mAdapter.get(0).getList()));
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
        new IBoxAlertDialogBuilder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    consumer.accept(values.get(which));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void applyFilters() {
        ((PageAdapter) mBinding.pager.getAdapter()).applyFilters(selectedType, selectedYear);
        mBinding.typeFilter.setText(selectedType.isEmpty() ? getString(R.string.search_filter_all_types) : getString(R.string.search_filter_value, getString(R.string.search_filter_type), SearchFilter.typeLabel(selectedType)));
        mBinding.yearFilter.setText(selectedYear.isEmpty() ? getString(R.string.search_filter_all_years) : getString(R.string.search_filter_value, getString(R.string.search_filter_year), selectedYear));
    }

    private void updateFilterState() {
        List<Vod> items = mAdapter.get(0).getList();
        mBinding.typeFilter.setEnabled(!SearchFilter.types(items).isEmpty());
        mBinding.yearFilter.setEnabled(!SearchFilter.years(items).isEmpty());
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mBinding.pager.setCurrentItem(mBinding.recycler.getSelectedPosition());
        }
    };

    @Override
    protected void onBackInvoked() {
        mViewModel.stopSearch();
        super.onBackInvoked();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        private final Map<String, CollectFragment> fragments = new HashMap<>();

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Collect collect = mAdapter.get(position);
            CollectFragment fragment = CollectFragment.newInstance(getKeyword(), collect, selectedType, selectedYear);
            fragments.put(collect.getSite().getKey(), fragment);
            return fragment;
        }

        public void applyFilters(String type, String year) {
            fragments.values().forEach(fragment -> fragment.setFilter(type, year));
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.destroyItem(container, position, object);
            fragments.values().removeIf(fragment -> fragment == object);
        }

        @Nullable
        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(@Nullable Parcelable state, @Nullable ClassLoader loader) {
        }
    }
}
