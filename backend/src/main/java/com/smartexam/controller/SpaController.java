package com.smartexam.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 单页应用 (SPA) 路由重定向控制器
 * 将所有非 API、非静态资源（不含点号）的路径请求，统一转发到 index.html
 * 以便前端 Vue 的路由/页面状态恢复逻辑能够正确处理，避免出现 404 错误
 */
@Controller
public class SpaController {

    @GetMapping(value = {
        "/{path:[^\\.]*}",
        "/*/{path:[^\\.]*}",
        "/**/{path:[^\\.]*}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
