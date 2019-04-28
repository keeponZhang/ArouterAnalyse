package com.lizejun.demo.arouterdemo;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.alibaba.android.arouter.launcher.ARouter;
import com.lizejun.demo.lib.base.RouterMap;
import com.lizejun.demo.lib.base.util.FragmentUtils;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void addFragment(View view) {
        Fragment homeFragment = getHomeFragment();
        FragmentUtils.addFragment(this, homeFragment, R.id.fl_container);
    }

    private Fragment getHomeFragment() {
        return (Fragment) ARouter.getInstance().build(RouterMap.HOME_FRAGMENT).navigation();
    }

    public void initArouter(View view) {
        ARouter.init(getApplication());
    }
}
