package com.imedia.box.ui.custom;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.imedia.box.bean.Result;

public class CustomScroller extends RecyclerView.OnScrollListener {

    private final Callback callback;
    private boolean loading;
    private boolean enable;
    private int page;

    public CustomScroller(Callback callback) {
        this.callback = callback;
        this.enable = true;
        this.page = 1;
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView view, int newState) {
        if (newState != RecyclerView.SCROLL_STATE_IDLE || !isBottom(view)) return;
        loadMore();
    }

    private boolean isBottom(RecyclerView view) {
        if (view == null || view.getLayoutManager() == null || view.getLayoutManager().getItemCount() == 0) return false;
        RecyclerView.LayoutManager manager = view.getLayoutManager();
        int lastVisible = RecyclerView.NO_POSITION;
        for (int i = 0; i < manager.getChildCount(); i++) {
            lastVisible = Math.max(lastVisible, manager.getPosition(manager.getChildAt(i)));
        }
        return lastVisible == manager.getItemCount() - 1;
    }

    public void checkMore() {
        loadMore();
    }

    public void checkMore(RecyclerView view) {
        if (isBottom(view)) loadMore();
    }

    private void loadMore() {
        if (isDisable() || isLoading() || callback == null) return;
        if (callback.onLoadMore(String.valueOf(page + 1))) {
            page++;
            setLoading(true);
        }
    }

    public void reset() {
        loading = false;
        enable = true;
        page = 1;
    }

    public boolean first() {
        return page == 1;
    }

    public boolean isLoading() {
        return loading;
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
    }

    public boolean isDisable() {
        return !enable;
    }

    public void setEnable(int pageCount) {
        this.enable = page < pageCount || pageCount == 0;
    }

    public void endLoading(Result result) {
        if (result.getList().isEmpty()) page--;
        setEnable(result.getPageCount());
        setLoading(false);
    }

    public void endLoading(boolean hasMore) {
        enable = hasMore;
        setLoading(false);
    }

    public interface Callback {
        boolean onLoadMore(String page);
    }
}
