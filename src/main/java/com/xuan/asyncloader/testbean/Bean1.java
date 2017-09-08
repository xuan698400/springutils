package com.xuan.asyncloader.testbean;

import org.springframework.beans.factory.InitializingBean;

/**
 * Bean1
 * <p>
 * Created by xuan on 17/9/8.
 */
public class Bean1 extends BaseBean implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        start();
        sleep(1);
        on("bean1 init finished.");
    }

}
