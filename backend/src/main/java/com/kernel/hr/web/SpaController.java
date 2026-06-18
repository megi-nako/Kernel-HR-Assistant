package com.kernel.hr.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

// Forwards client-side React Router routes to index.html so the browser can
// bootstrap the SPA. Without this, Spring Boot returns 404 for /login and /chat
// on hard refresh or direct link.
@Controller
public class SpaController {

    @RequestMapping(value = {"/", "/login", "/chat"})
    public String index() {
        return "forward:/index.html";
    }
}
