package com.xuan.asyncloader.factory.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * 用来拦截被异步初始化的Bean，确保这些Bean在使用前，Spring是已经启动成功的
 * <p>
 * Created by xuan on 17/9/6.
 */
public class AsycBeanInterceptor implements MethodInterceptor, ApplicationListener {
    private static final Logger log = LoggerFactory.getLogger(AsycBeanInterceptor.class);

    /**
     * 表示是否Spring容器启动了
     */
    private volatile boolean isSpringRefreshed = false;

    @Override
    public Object invoke(MethodInvocation inv) throws Throwable {
        if (!isSpringRefreshed) {
            //如果Spring容器还没有被初始化好，但是被定义为异步加载的Bean已经在被调用了，需要返回异常让上层知道
            String errMsg = "Fatal Error:" + inv.getClass() + " is set to be init Asynchronized,but its method:" + inv.getMethod().getName() + " is called when initiation is not finish.Jvm will be shut down!";
            log.error("[AsycBeanInterceptor-invoke]error," + errMsg);
            throw new RuntimeException(errMsg);
        }
        return inv.proceed();
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            isSpringRefreshed = true;
        }
    }

}
