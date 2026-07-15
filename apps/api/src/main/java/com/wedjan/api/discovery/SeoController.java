package com.wedjan.api.discovery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seo")
public class SeoController {
    private final DiscoveryService service;
    public SeoController(DiscoveryService service){this.service=service;}
    @GetMapping("/routes") public DiscoveryDtos.LandingRouteList routes(){return service.landingRoutes();}
    @GetMapping("/{country}/{city}/{category}")
    public DiscoveryDtos.LandingPage landing(@PathVariable String country,@PathVariable String city,@PathVariable String category){return service.landing(country,city,category);}
}
