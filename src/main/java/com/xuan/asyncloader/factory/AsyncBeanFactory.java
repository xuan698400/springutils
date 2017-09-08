package com.xuan.asyncloader.factory;

import com.xuan.asyncloader.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.lang.Boolean.TRUE;

/**
 * 异步加载Bean的工厂类
 * <p>
 * Created by xuan on 17/9/6.
 */
public class AsyncBeanFactory extends DefaultListableBeanFactory {
    private static final Logger log = LoggerFactory.getLogger(AsyncBeanFactory.class);

    private static final List<Future<Throwable>> taskList = Collections.synchronizedList(new ArrayList<Future<Throwable>>());

    /**
     * 初始化Bean的线程池
     */
    public static ExecutorService threadPool;
    private boolean contextFinished = false;

    /**
     * 够着方法
     *
     * @param parentBeanFactory 他的父亲
     * @param poolSize          线程池数量
     */
    public AsyncBeanFactory(BeanFactory parentBeanFactory, int poolSize) {
        super(parentBeanFactory);
        threadPool = Executors.newFixedThreadPool(poolSize);
    }

    @Override
    protected void invokeInitMethods(String beanName, Object bean, RootBeanDefinition mbd) throws Throwable {
        //判断Bean是否是需要初始化的Bean
        boolean isInitializingBean = (bean instanceof InitializingBean);
        //判断初始化方法是否需要异步执行
        boolean isAsyncInit = isAsyncInit(bean, mbd);

        if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet"))) {
            logger.debug("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
            if (isAsyncInit) {
                //异步执行初始化方法
                invokeCustomInitMethod(beanName, bean, "afterPropertiesSet", false);
            } else {
                doBeanAfterPropertiesSet((InitializingBean) bean);
            }
        }

        if (mbd != null) {
            //执行在配置文件中定义的初始化方法
            String initMethodName = mbd.getInitMethodName();
            if (initMethodName != null && !(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
                    !mbd.isExternallyManagedInitMethod(initMethodName)) {
                if (isAsyncInit) {
                    invokeCustomInitMethod(beanName, bean, initMethodName, mbd.isEnforceInitMethod());
                } else {
                    super.invokeCustomInitMethod(beanName, bean, mbd);
                }
            }
        }
    }

    /**
     * 常规执行Bean的初始化
     *
     * @param bean
     * @throws Exception
     */
    private void doBeanAfterPropertiesSet(final InitializingBean bean) throws Exception {
        if (System.getSecurityManager() != null) {
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                    @Override
                    public Object run() throws Exception {
                        bean.afterPropertiesSet();
                        return null;
                    }
                }, getAccessControlContext());
            } catch (PrivilegedActionException pae) {
                throw pae.getException();
            }
        } else {
            bean.afterPropertiesSet();
        }
    }

    /**
     * 反射执行初始化方法
     *
     * @param beanName          Bean名称
     * @param bean              Bean实例
     * @param initMethodName    方法名称
     * @param enforceInitMethod 是否强制执行，如果true，但是Bean没有指定方法会抛异常，如果false，但是Bean没有执行方法，默认就不执行
     * @throws Throwable
     */
    protected void invokeCustomInitMethod(String beanName, Object bean, String initMethodName, boolean enforceInitMethod) throws Throwable {
        Method initMethod = BeanUtils.findMethod(bean.getClass(), initMethodName, null);
        if (initMethod == null) {
            if (enforceInitMethod) {
                throw new NoSuchMethodException("Couldn't find an init method named '" + initMethodName +
                        "' on bean with name '" + beanName + "'");
            } else {
                logger.debug("No default init method named '" + initMethodName +
                        "' found on bean with name '" + beanName + "'");
                return;
            }
        }

        logger.debug("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");
        asyncInvoke(bean, beanName, initMethod);
    }

    /**
     * 异步执行初始化方法
     *
     * @param bean       Bean实例
     * @param beanName   Bean名称
     * @param initMethod 初始化方法
     */
    private void asyncInvoke(final Object bean, final String beanName, final Method initMethod) {
        taskList.add(threadPool.submit(new Callable<Throwable>() {
            @Override
            public Throwable call() throws Exception {
                log.warn("asyn bean init begin:" + beanName);
                try {
                    if (!Modifier.isPublic(initMethod.getModifiers()) ||
                            !Modifier.isPublic(initMethod.getDeclaringClass().getModifiers())) {
                        initMethod.setAccessible(true);
                    }
                    initMethod.invoke(bean, (Object[]) null);
                    return null;
                } catch (Throwable throwable) {
                    return new BeanCreationException(
                            beanName + ": Async Invocation of init method failed", throwable);
                } finally {
                    log.warn("asyn bean init end:" + beanName);
                }
            }
        }));
    }

    /**
     * 等待异步线程跑完
     */
    public void waitAsyncInitTaskFinish() {
        if (contextFinished)
            return;
        if (taskList.size() > 0) {
            long start = System.currentTimeMillis();
            try {
                for (Future<Throwable> task : taskList) {
                    Throwable result = task.get();
                    if (result != null) {
                        throw result;
                    }
                }
            } catch (Throwable e) {
                if (e instanceof BeanCreationException) {
                    throw (BeanCreationException) e;
                } else {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        }
        contextFinished = true;
        threadPool.shutdown();
    }

    /**
     * 判断这个Bean是否需要异步初始化
     *
     * @param bean
     * @param mbd
     * @return
     */
    private boolean isAsyncInit(Object bean, RootBeanDefinition mbd) {
        if (contextFinished || mbd == null || mbd.isLazyInit() || bean instanceof FactoryBean) {
            return false;
        }
        Object value = mbd.getAttribute(Constants.ASYNC_INIT);
        return TRUE.equals(value) || "true".equals(value);
    }

}
