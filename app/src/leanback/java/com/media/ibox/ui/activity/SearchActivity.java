package com.media.ibox.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.App;
import com.media.ibox.R;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.bean.Word;
import com.media.ibox.databinding.ActivitySearchBinding;
import com.media.ibox.impl.Callback;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.adapter.RecordAdapter;
import com.media.ibox.ui.adapter.WordAdapter;
import com.media.ibox.ui.base.BaseActivity;
import com.media.ibox.ui.custom.CustomKeyboard;
import com.media.ibox.ui.custom.CustomTextListener;
import com.media.ibox.ui.dialog.SiteDialog;
import com.media.ibox.utils.KeyUtil;
import com.media.ibox.utils.Util;
import com.media.ibox.utils.ZhuToPin;
import com.github.catvod.net.OkHttp;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Response;

public class SearchActivity extends BaseActivity implements WordAdapter.OnClickListener, RecordAdapter.OnClickListener, CustomKeyboard.Callback {

    private static final long SUGGEST_DEBOUNCE_MS = 250;

    private ActivitySearchBinding mBinding;
    private RecordAdapter mRecordAdapter;
    private WordAdapter mWordAdapter;
    private Call mWordCall;
    private int wordRequestId;
    private String pendingWord = "";
    private final Runnable requestWord = () -> requestWord(pendingWord);
    private boolean allSources = true;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SearchActivity.class));
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    private String getKeyword() {
        String keyword = getIntent().getStringExtra("keyword");
        return keyword != null ? keyword : "";
    }

    private boolean empty() {
        return mBinding.keyword.getText().toString().trim().isEmpty();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        CustomKeyboard.init(this, mBinding);
        mBinding.scopeCurrent.setText(getString(R.string.search_scope_current, VodConfig.get().getHome().getName()));
        mBinding.scopeCurrent.setEnabled(VodConfig.get().getHome().isSearchable());
        setRecyclerView();
        checkKeyword();
        onSearch();
    }

    @Override
    protected void initEvent() {
        mBinding.scope.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) allSources = checkedId == R.id.scopeAll;
        });
        mBinding.historyClear.setOnClickListener(view -> clearHistory());
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onSearch();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                getWord(s.toString());
            }
        });
        mBinding.mic.setOnClickListener(v -> mBinding.mic.start());
        mBinding.mic.setListener(this, new CustomTextListener() {
            @Override
            public void onResults(String result) {
                if (!result.isEmpty()) setKeyword(result);
                mBinding.keyword.requestFocus();
            }
        });
    }

    private void setRecyclerView() {
        mBinding.wordRecycler.setItemAnimator(null);
        mBinding.wordRecycler.setHasFixedSize(false);
        mBinding.wordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.wordRecycler.setAdapter(mWordAdapter = new WordAdapter(this));
        mBinding.recordRecycler.setHasFixedSize(false);
        mBinding.recordRecycler.setLayoutManager(new FlexboxLayoutManager(this, FlexDirection.ROW));
        mBinding.recordRecycler.setAdapter(mRecordAdapter = new RecordAdapter(this));
    }

    private void checkKeyword() {
        setKeyword(getKeyword());
        getWord(getKeyword());
    }

    private void setKeyword(String text) {
        mBinding.keyword.setText(text);
        mBinding.keyword.setSelection(text.length());
    }

    private void getWord(String text) {
        wordRequestId++;
        App.removeCallbacks(requestWord);
        if (mWordCall != null) mWordCall.cancel();
        pendingWord = text;
        if (text.isEmpty()) requestWord(text);
        else App.post(requestWord, SUGGEST_DEBOUNCE_MS);
    }

    private void requestWord(String text) {
        if (isFinishing() || isDestroyed()) return;
        if (text.isEmpty()) getHot();
        else getSuggest(text);
    }

    private void getHot() {
        mBinding.word.setText(R.string.search_hot);
        mWordAdapter.setItems(Word.objectFrom(Setting.getHot()).getData());
        int requestId = wordRequestId;
        mWordCall = OkHttp.newCall("https://api.web.360kan.com/v1/rank?cat=1", Map.of(HttpHeaders.REFERER, "https://www.360kan.com/rank/general"));
        mWordCall.enqueue(getCallback(true, requestId, ""));
    }

    private void getSuggest(String text) {
        mBinding.word.setText(R.string.search_suggest);
        int requestId = wordRequestId;
        mWordCall = OkHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + Uri.encode(ZhuToPin.get(text)));
        mWordCall.enqueue(getCallback(false, requestId, text));
    }

    private Callback getCallback(boolean hot, int requestId, String query) {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = response.body().string();
                if (TextUtils.isEmpty(result)) return;
                App.post(() -> setAdapter(result, hot, requestId, query));
            }
        };
    }

    private void setAdapter(String result, boolean save, int requestId, String query) {
        if (isFinishing() || isDestroyed() || requestId != wordRequestId) return;
        if (!save && !TextUtils.equals(query, mBinding.keyword.getText().toString())) return;
        if (save) Setting.putHot(result);
        mWordAdapter.setItems(Word.objectFrom(result).getData());
    }

    @Override
    public void onItemClick(String text) {
        setKeyword(text);
        onSearch();
    }

    @Override
    public void onDataChanged(int size) {
        mBinding.recordLayout.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        if (size == 0) focusFirst(mBinding.wordRecycler);
    }

    @Override
    public void onSearch() {
        if (empty()) return;
        String keyword = mBinding.keyword.getText().toString().trim();
        App.post(() -> mRecordAdapter.add(keyword), 250);
        Util.hideKeyboard(mBinding.keyword);
        CollectActivity.start(this, keyword, allSources);
    }

    @Override
    public void showDialog() {
        SiteDialog.create().search().show(this);
    }

    private void clearHistory() {
        new IBoxAlertDialogBuilder(this)
                .setTitle(R.string.search_history_clear_title)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.search_history_clear, (dialog, which) -> mRecordAdapter.clear())
                .show();
    }

    @Override
    public void onRemote() {
        PushActivity.start(this, 1);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        if (KeyUtil.isActionDown(event) && findFocus(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean findFocus(KeyEvent event) {
        View current = getCurrentFocus();
        if (current == mBinding.keyword) return handleKeywordKey(event);
        if (current == mBinding.historyClear) return handleHistoryClearKey(event);
        View inKeyboard = mBinding.keyboard.findContainingItemView(current);
        View inWord = mBinding.wordRecycler.findContainingItemView(current);
        View inRecord = mBinding.recordRecycler.findContainingItemView(current);
        if (inKeyboard != null) return handleKeyboardKey(event, inKeyboard);
        if (inRecord != null) return handleRecordKey(event, inRecord);
        if (inWord != null) return handleWordKey(event, inWord);
        return false;
    }

    private View findNearestInLastRow(RecyclerView rv, int targetLeft) {
        if (rv.getChildCount() == 0) return null;
        int lastTop = rv.getChildAt(rv.getChildCount() - 1).getTop();
        View nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == lastTop) {
                int dist = Math.abs(child.getLeft() - targetLeft);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = child;
                }
            }
        }
        return nearest;
    }

    private boolean isFirstRow(RecyclerView rv, View item) {
        View first = rv.getChildAt(0);
        return first != null && item.getTop() == first.getTop();
    }

    private boolean isLastRow(RecyclerView rv, View item) {
        View last = rv.getChildAt(rv.getChildCount() - 1);
        return last != null && item.getTop() == last.getTop();
    }

    private boolean isFirstInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int left = focused.getLeft();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getLeft() < left) return false;
        }
        return true;
    }

    private boolean isLastInRow(RecyclerView rv, View focused) {
        int top = focused.getTop();
        int right = focused.getRight();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getTop() == top && child.getRight() > right) return false;
        }
        return true;
    }

    private boolean handleKeywordKey(KeyEvent event) {
        if (!KeyUtil.isRightKey(event)) return false;
        if (mBinding.keyword.getSelectionEnd() < mBinding.keyword.getText().length()) return false;
        boolean hasRecord = mBinding.recordLayout.getVisibility() == View.VISIBLE;
        return focusFirst(hasRecord ? mBinding.recordRecycler : mBinding.wordRecycler);
    }

    private boolean handleKeyboardKey(KeyEvent event, View item) {
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.keyboard, item)) {
            mBinding.keyword.requestFocus();
            return true;
        }
        if (KeyUtil.isLeftKey(event) && isFirstInRow(mBinding.keyboard, item)) return true;
        return KeyUtil.isDownKey(event) && isLastRow(mBinding.keyboard, item);
    }

    private boolean handleWordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.wordRecycler, item);
        if (KeyUtil.isDownKey(event)) return isLastRow(mBinding.wordRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.wordRecycler, item)) {
            if (mBinding.recordLayout.getVisibility() == View.VISIBLE) {
                View child = findNearestInLastRow(mBinding.recordRecycler, item.getLeft());
                if (child != null) {
                    mBinding.scroll.smoothScrollTo(0, 0);
                    child.requestFocus();
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleRecordKey(KeyEvent event, View item) {
        if (KeyUtil.isRightKey(event)) return isLastInRow(mBinding.recordRecycler, item);
        if (KeyUtil.isUpKey(event) && isFirstRow(mBinding.recordRecycler, item)) {
            mBinding.historyClear.requestFocus();
            return true;
        }
        if (KeyUtil.isDownKey(event) && isLastRow(mBinding.recordRecycler, item)) return focusFirst(mBinding.wordRecycler);
        return false;
    }

    private boolean handleHistoryClearKey(KeyEvent event) {
        if (KeyUtil.isDownKey(event)) return focusFirst(mBinding.recordRecycler);
        return KeyUtil.isRightKey(event) || KeyUtil.isUpKey(event);
    }

    private boolean focusFirst(RecyclerView rv) {
        View child = rv.getChildAt(0);
        if (child == null) return false;
        child.requestFocus();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBinding.mic.setFocusable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBinding.mic.setFocusable(true);
        mBinding.keyword.requestFocus();
    }

    @Override
    protected void onDestroy() {
        wordRequestId++;
        App.removeCallbacks(requestWord);
        if (mWordCall != null) mWordCall.cancel();
        mWordCall = null;
        mBinding.mic.destroy();
        super.onDestroy();
    }
}
