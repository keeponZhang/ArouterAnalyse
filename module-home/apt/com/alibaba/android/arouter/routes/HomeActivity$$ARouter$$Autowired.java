package com.lizejun.demo.module.home;

import com.alibaba.android.arouter.facade.service.SerializationService;
import com.alibaba.android.arouter.facade.template.ISyringe;
import com.alibaba.android.arouter.launcher.ARouter;
import java.lang.Object;
import java.lang.Override;

/**
 * DO NOT EDIT THIS FILE!!! IT WAS GENERATED BY AROUTER. */
public class HomeActivity$$ARouter$$Autowired implements ISyringe {
  private SerializationService serializationService;

  @Override
  public void inject(Object target) {
    serializationService = ARouter.getInstance().navigation(SerializationService.class);
    HomeActivity substitute = (HomeActivity)target;
    substitute.keepon = substitute.getIntent().getStringExtra("keepon");
    substitute.mIKeeponService = (IKeeponService)ARouter.getInstance().build("keeponService").navigation();;
  }
}
