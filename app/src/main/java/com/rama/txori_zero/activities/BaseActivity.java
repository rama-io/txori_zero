package com.rama.txori_zero.activities;

import android.app.Activity;
import android.view.View;

import com.rama.txori_zero.R;
import com.rama.txori_zero.managers.ZoomManager;

public abstract class BaseActivity extends Activity {
    @Override
    protected void onResume() {
        super.onResume();
        View root = findViewById(R.id.root);
        if (root != null) {
            ZoomManager.apply(this, root);
        }
    }
}
