package com.rama.txori_zero.widgets;

import android.content.Context;
import android.util.AttributeSet;

import com.rama.txori_zero.R;

public class WdCheckbox extends WdCompound {
    public WdCheckbox(Context context) {
        this(context, null);
    }

    public WdCheckbox(Context context, AttributeSet attrs) {
        super(context, attrs, R.layout.wd_checkbox, true, "android.widget.CheckBox");
    }
}
