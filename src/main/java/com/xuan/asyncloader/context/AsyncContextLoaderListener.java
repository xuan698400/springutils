package com.xuan.asyncloader.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextException;
import org.springframework.web.context.ContextLoaderListener;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;


/**
 * 在web.xml中配置，启动Web容器时，用来选定容器装配
 * <p>
 * Created by xuan on 17/9/6.
 */
public class AsyncContextLoaderListener extends ContextLoaderListener {

    private static final Logger log = LoggerFactory.getLogger(AsyncContextLoaderListener.class);

    @Override
    public void contextInitialized(ServletContextEvent event) {
        long start = System.currentTimeMillis();
        super.contextInitialized(event);
        log.warn("ContextInit finish...cost:" + (System.currentTimeMillis() - start));
    }

    @Override
    protected Class determineContextClass(ServletContext servletContext) throws ApplicationContextException {
        Class contextClass = AsyncContextLoaderListener.this.determineContextClass();
        if (contextClass != null) {
            return contextClass;
        }
        return super.determineContextClass(servletContext);
    }

    /**
     * 这里会优先于web.xml 里的contextClass 配置
     */
    protected Class determineContextClass() {
        return AsyncXmlWebApplicationContext.class;
    }
}
