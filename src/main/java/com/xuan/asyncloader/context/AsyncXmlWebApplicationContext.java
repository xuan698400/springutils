package com.xuan.asyncloader.context;

import com.xuan.asyncloader.Constants;
import com.xuan.asyncloader.factory.AsyncBeanFactory;
import com.xuan.asyncloader.factory.processor.AsyncBeanFactoryPostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.context.support.XmlWebApplicationContext;

/**
 * 异步加载容器
 * <p>
 * Created by xuan on 17/9/6.
 */
public class AsyncXmlWebApplicationContext extends XmlWebApplicationContext {

    private static final Logger log = LoggerFactory.getLogger(AsyncXmlWebApplicationContext.class);

    private AsyncBeanFactory asyncBeanFactory;

    @Override
    protected DefaultListableBeanFactory createBeanFactory() {
        log.info("CreateBeanFactory in Threadid:\t" + Thread.currentThread().getId() + Thread.currentThread().getName());
        asyncBeanFactory = new AsyncBeanFactory(getParentBeanFactory(), Constants.DEFAULT_POOL_SIZE);
        return asyncBeanFactory;
    }

    @Override
    public void refresh() throws BeansException, IllegalStateException {
        log.info("refresh() in Threadid:\t" + Thread.currentThread().getId() + Thread.currentThread().getName());
        Long initStartTime = System.currentTimeMillis();
        AsyncBeanFactoryPostProcessor beanFactoryPostProcessor = new AsyncBeanFactoryPostProcessor();
        beanFactoryPostProcessor.setAsyncFilePath("WEB-INF/asyncBean");
        beanFactoryPostProcessor.setResourceLoader(this);
        this.addBeanFactoryPostProcessor(beanFactoryPostProcessor);

        super.refresh();

        log.warn("TotalInitTime:" + (System.currentTimeMillis() - initStartTime));
    }

    @Override
    public void publishEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            finishRefresh();
        }
        super.publishEvent(event);
    }

    protected void finishRefresh() {
        asyncBeanFactory.waitAsyncInitTaskFinish();
    }

}
