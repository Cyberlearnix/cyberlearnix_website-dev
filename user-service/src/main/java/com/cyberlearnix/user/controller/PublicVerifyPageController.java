package com.cyberlearnix.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PublicVerifyPageController {

    @GetMapping({"/verify/{enrollmentNumber}", "/CLX-{enrollmentNumber}"})
    public String forwardToVerifyHtml(@PathVariable String enrollmentNumber) {
        return "forward:/verify.html";
    }
}
