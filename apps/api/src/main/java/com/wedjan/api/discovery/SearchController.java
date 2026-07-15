package com.wedjan.api.discovery;

import com.wedjan.api.config.JwtAuthFilter;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final SearchService service;
    public SearchController(SearchService service) { this.service = service; }

    @GetMapping("/vendors")
    public DiscoveryDtos.VendorSearchResponse vendors(
            @RequestParam(required=false) String q,
            @RequestParam(required=false) String category,
            @RequestParam(required=false) String city,
            @RequestParam(required=false) Double lat,
            @RequestParam(required=false) Double lng,
            @RequestParam(name="radius",required=false) Double radiusKm,
            @RequestParam(name="price_min",required=false) Long priceMin,
            @RequestParam(name="price_max",required=false) Long priceMax,
            @RequestParam(required=false) Integer guests,
            @RequestParam(required=false) List<String> badges,
            @RequestParam(name="booking_mode",required=false) String bookingMode,
            @RequestParam(required=false) DiscoveryDtos.SearchSort sort,
            @RequestParam(required=false) String cursor,
            @RequestHeader(name="X-Search-Session",required=false) String sessionId) {
        return service.search(q,category,city,lat,lng,radiusKm,priceMin,priceMax,guests,
                badges,bookingMode,sort,cursor,JwtAuthFilter.currentAccountId(),sessionId);
    }
}
