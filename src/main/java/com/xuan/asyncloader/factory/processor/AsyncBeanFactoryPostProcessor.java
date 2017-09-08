package com.xuan.asyncloader.factory.processor;

import com.xuan.asyncloader.Constants;
import com.xuan.asyncloader.factory.interceptor.AsycBeanInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * 异步工厂类后置处理器
 * <p>
 * Created by xuan on 17/9/6.
 */
public class AsyncBeanFactoryPostProcessor implements BeanFactoryPostProcessor, ResourceLoaderAware {
    private static final Logger log = LoggerFactory.getLogger(AsyncBeanFactoryPostProcessor.class);

    /**
     * 异步Bean配置文件
     */
    private String asyncFilePath;
    /**
     * 资源加载器
     */
    private ResourceLoader resourceLoader;
    /**
     * 异步Bean
     */
    private HashSet<String> asyncBeans = new HashSet<String>();
    /**
     * 作为异步加载的起点，加快异步加载触发
     */
    private final String[] beanNamesReferTo = new String[]{"activityApi"};


    public void setAsyncFilePath(String asyncFilePath) {
        this.asyncFilePath = asyncFilePath;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        Resource resource = resourceLoader.getResource(asyncFilePath);
        if (!resource.exists()) {
            return;
        }

        //读取需要异步加载的Bean
        List<String> beanNames = new ArrayList<>();
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(resource.getInputStream(), "UTF-8"));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                beanNames.add(line);
            }
        } catch (Exception e) {
            log.error("[AsyncBeanFactoryPostProcessor-postProcessBeanFactory]error", e);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    log.error("[AsyncBeanFactoryPostProcessor-postProcessBeanFactory]error", e);
                }
            }
        }

        if (!beanNames.isEmpty()) {
            log.warn("init method of beans " + beanNames + " will be async invoked...");
        }

        //修改Bean的属性信息，把这些Bean设置成可异步初始化
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = null;
            try {
                beanDefinition = beanFactory.getBeanDefinition(beanName);
            } catch (NoSuchBeanDefinitionException e) {
                log.warn("bean:" + beanName + ", not found.");
            }
            if (beanDefinition != null) {
                beanDefinition.setAttribute(Constants.ASYNC_INIT, true);
                asyncBeans.add(beanName);
            }
        }
        if (beanFactory instanceof DefaultListableBeanFactory) {
            addProxyBeanDefinition((DefaultListableBeanFactory) beanFactory, beanNames);
            setDependsOnForAsyncBeanInitFirst((DefaultListableBeanFactory) beanFactory);
        }
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 主要目的就是给异步初始化的Bean添加拦截器
     *
     * @param beanFactory
     * @param beanNames
     */
    private void addProxyBeanDefinition(DefaultListableBeanFactory beanFactory, List<String> beanNames) {
        if (beanNames == null || beanNames.size() == 0) {
            return;
        }
        //自己构建一个拦截器的BeanDefinition，并注册到容器中
        BeanDefinition interceptorBeanDefinition = new RootBeanDefinition(AsycBeanInterceptor.class);
        beanFactory.registerBeanDefinition("asycInitBeanInterceptor", interceptorBeanDefinition);

        //注册aop拦截器的bean定义
        MutablePropertyValues propertyValues = new MutablePropertyValues();
        ManagedList<TypedStringValue> listBeanNames = new ManagedList<>(beanNames.size());
        for (String beanName : beanNames) {
            listBeanNames.add(new TypedStringValue(beanName));
        }

        propertyValues.addPropertyValue("beanNames", listBeanNames);
        ManagedList<TypedStringValue> listInterceptorNames = new ManagedList<>(1);
        listInterceptorNames.add(new TypedStringValue("asycInitBeanInterceptor"));
        propertyValues.addPropertyValue("interceptorNames", listInterceptorNames);
        BeanDefinition proxyCreatorBeanDefinition = new RootBeanDefinition(BeanNameAutoProxyCreator.class, null, propertyValues);
        beanFactory.registerBeanDefinition("asycBeanAutoProxyCreator", proxyCreatorBeanDefinition);
    }

    /**
     * 为bean 设置dependson，需要选比较找初始化的bean，这样可以让并行的beans尽早初始化
     *
     * @param beanFactory
     */
    private void setDependsOnForAsyncBeanInitFirst(DefaultListableBeanFactory beanFactory) {
        if (asyncBeans.size() == 0) {
            return;
        }
        for (String beanName : beanNamesReferTo) {
            BeanDefinition beanDefinition = null;
            try {
                beanDefinition = beanFactory.getBeanDefinition(beanName);
            } catch (Throwable e) {
                log.warn("bean:" + beanName + ", not found.");
            }
            if (beanDefinition != null && beanDefinition instanceof AbstractBeanDefinition) {
                AbstractBeanDefinition abd = (AbstractBeanDefinition) beanDefinition;
                String[] oldDepends = abd.getDependsOn();
                List<String> newDepends = new ArrayList<>();
                for (String asyncBean : asyncBeans) {
                    newDepends.add(asyncBean);
                }
                if (oldDepends != null) {
                    Collections.addAll(newDepends, oldDepends);
                }
                abd.setDependsOn(newDepends.toArray(new String[]{}));
                log.info("beanDefinition:" + beanName + " setDependsOn:" + newDepends);
            }
        }
    }

}
