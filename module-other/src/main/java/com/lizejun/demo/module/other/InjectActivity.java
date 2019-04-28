package com.lizejun.demo.module.other;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.demo.lizejun.module.other.R;
import com.lizejun.demo.lib.base.ConstantMap;
import com.lizejun.demo.lib.base.RouterMap;
import com.lizejun.demo.lib.base.bean.SerialBean;
import com.lizejun.demo.lib.base.util.Utils;
import com.lizejun.demo.module.other.service.HelloService;

@Route(path = RouterMap.INJECT_ACTIVITY)
public class InjectActivity extends AppCompatActivity {
    @Autowired(name = ConstantMap.INJECT_AGE)
    int          age;
    @Autowired(name = ConstantMap.INJECT_OBJECT)
    SerialBean   bean;
    @Autowired(name = "keepon" )
    String       keepon;
    @Autowired()
    HelloService mHelloService;
    private static final String TAG = "InjectActivity";
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inject);
        ARouter.getInstance().inject(this);
        Utils.toast(this, "age=" + age + ",bean.age=" + bean.getAge() + ",bean.name=" + bean.getName());
        Log.e(TAG, "onCreate mHelloService:"+mHelloService);
    }
    public void testAutoWrite(View view){
        mHelloService = (HelloService) ARouter.getInstance().navigation(HelloService.class);
        Log.e(TAG, "testAutoWrite mHelloService:"+mHelloService);
    }
    public void testAutoWrite1(View view){
       mHelloService = (HelloService) ARouter.getInstance().build("/service/hello").navigation();
        Log.e(TAG, "testAutoWrite1 mHelloService:"+mHelloService);
    }
}
