package org.ganjp.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/")
    public String getWelcomeMessage() {
        return "Welcome to Gan Jian Ping Admin APIs";
    }
}

