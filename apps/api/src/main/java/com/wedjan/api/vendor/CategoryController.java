package com.wedjan.api.vendor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final VendorService service;

    public CategoryController(VendorService service) { this.service = service; }

    @GetMapping
    public VendorDtos.CategoryListResponse list() {
        return new VendorDtos.CategoryListResponse(service.categories());
    }
}
