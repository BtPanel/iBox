package com.media.ibox.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.media.ibox.databinding.ActivityFileBinding;
import com.media.ibox.ui.adapter.FileAdapter;
import com.media.ibox.ui.base.BaseActivity;
import com.media.ibox.utils.PermissionUtil;
import com.media.ibox.utils.ResUtil;
import com.github.catvod.utils.Path;

import java.io.File;

public class FileActivity extends BaseActivity implements FileAdapter.OnClickListener {

    private ActivityFileBinding mBinding;
    private FileAdapter mAdapter;
    private File dir;

    private boolean isRoot() {
        return Path.root().equals(dir);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityFileBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setRecyclerView();
        checkPermission();
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setAdapter(mAdapter = new FileAdapter(this));
    }

    private void checkPermission() {
        PermissionUtil.requestFile(this, allGranted -> update(Path.root()));
    }

    private void update(File dir) {
        mBinding.recycler.setSelectedPosition(0);
        mAdapter.addAll(Path.list(this.dir = dir));
        mBinding.progressLayout.showContent(true, mAdapter.getItemCount());
    }

    @Override
    public void onItemClick(File file) {
        if (file.isDirectory()) {
            update(file);
        } else {
            setResult(RESULT_OK, new Intent().setData(Uri.fromFile(file)));
            finish();
        }
    }

    @Override
    protected void onBackInvoked() {
        File parent = dir == null ? null : dir.getParentFile();
        if (isRoot() || parent == null) {
            super.onBackInvoked();
        } else {
            update(parent);
        }
    }
}
