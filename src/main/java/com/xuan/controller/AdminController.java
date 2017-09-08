package com.xuan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Admin
 * <p>
 * Created by xuan on 17/9/7.
 */
@Controller
@RequestMapping(value = "/")
public class AdminController {

    @RequestMapping("index.htm")
    @ResponseBody
    public String index(String name) {
        return "你好：" + name;
    }

}
