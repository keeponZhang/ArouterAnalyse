package com.lizejun.demo.module.home;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.demo.lizejun.module.home.R;
import com.lizejun.demo.lib.base.util.FragmentUtils;

@Route(path="/home/home")
public class HomeActivity extends AppCompatActivity {
    private static Activity activity;
    public static Activity getThis() {
        return activity;
    }
    @Autowired(name = "keepon" )
    String       keepon;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ARouter.init(getApplication());
        activity = this;
        addFragment();
    }

    private void addFragment() {
        FragmentUtils.addFragment(this, new HomeFragment(), R.id.fl_container);
    }


}
