package com.xuan.asyncloader.testbean;

/**
 * BaseBean
 * <p>
 * Created by xuan on 17/9/8.
 */
public class BaseBean {

    protected long lastTime = 0;

    protected void start() {
        System.out.println("start");
        lastTime = System.currentTimeMillis();
    }

    protected void on(String name) {
        long nowTime = System.currentTimeMillis();
        System.out.println(name + "-" + (nowTime - lastTime));
        lastTime = nowTime;
    }

    /**
     * 模拟耗时
     *
     * @param second
     */
    protected void sleep(int second) {
        try {
            Thread.sleep(second * 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
