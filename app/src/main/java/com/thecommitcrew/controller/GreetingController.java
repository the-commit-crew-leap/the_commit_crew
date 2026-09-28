package com.thecommitcrew.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;


@RestController 
public class GreetingController {
    @GetMapping("/hello")
    public String hello() {
        return "Hello from The Commit Crew!";
        //curl: curl "http://localhost:8081/hello"
    }
    
}

