package com.oneforall.padobackend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminAuditPageController {
    @GetMapping("/admin/audit")
    public String auditConsole() {
        return "redirect:/admin/audit/";
    }

    @GetMapping("/admin/audit/")
    public String auditConsoleIndex() {
        return "forward:/admin/audit/index.html";
    }
}
