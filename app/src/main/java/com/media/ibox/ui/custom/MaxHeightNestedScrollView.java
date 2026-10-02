package com.media.ibox.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.media.ibox.R;

public class MaxHeightNestedScrollView extends NestedScrollView {

    private int maxHeight;

    public MaxHeightNestedScrollView(@NonNull Context context) {
        this(context, null);
    }

    public MaxHeightNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaxHeightNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs == null) return;
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightNestedScrollView);
        maxHeight = array.getDimensionPixelSize(R.styleable.MaxHeightNestedScrollView_maxHeight, 0);
        array.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeight > 0) {
            int height = View.MeasureSpec.getSize(heightMeasureSpec);
            int mode = View.MeasureSpec.getMode(heightMeasureSpec);
            if (mode == View.MeasureSpec.UNSPECIFIED || height > maxHeight) {
                heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
