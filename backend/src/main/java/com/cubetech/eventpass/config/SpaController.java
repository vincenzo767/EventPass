package com.cubetech.eventpass.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React single-page app for client-side routes, so deep links and browser refreshes
 * (e.g. /tickets/abc or /staff/check-in/1) load the app instead of returning 404.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/events", "/events/**", "/tickets", "/tickets/**", "/staff", "/staff/**"})
    public String index() {
        return "forward:/index.html";
    }
}
