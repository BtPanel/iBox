package com.media.ibox.ui.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;

public abstract class BaseFragment extends Fragment {

    private boolean isViewCreated;
    private boolean isDataLoaded;

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        isViewCreated = true;
        if (getUserVisibleHint()) {
            lazyLoad();
            isDataLoaded = true;
        }
    }

    private void lazyLoad() {
        initMenu();
        initView();
        initEvent();
    }

    protected void initMenu() {
    }

    /** 二级页面工具栏统一的返回入口：左侧箭头 + 交给系统返回栈 */
    protected void bindBackNavigation(com.google.android.material.appbar.MaterialToolbar toolbar) {
        if (toolbar == null) return;
        toolbar.setNavigationIcon(R.drawable.ic_msr_arrow_back);
        toolbar.setNavigationIconTint(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.ibox_text_primary));
        toolbar.setNavigationOnClickListener(view -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    public boolean canBack() {
        return true;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser && isViewCreated && !isDataLoaded) {
            lazyLoad();
            isDataLoaded = true;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isViewCreated = false;
        isDataLoaded = false;
    }
}
