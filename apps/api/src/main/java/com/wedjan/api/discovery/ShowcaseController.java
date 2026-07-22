package com.wedjan.api.discovery;

import com.wedjan.api.config.JwtAuthFilter;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/showcases")
public class ShowcaseController {
    private final DiscoveryService service;
    public ShowcaseController(DiscoveryService service){this.service=service;}

    @GetMapping
    public DiscoveryDtos.ShowcaseFeedResponse feed(@RequestParam(required=false) String eventType,
            @RequestParam(required=false) String city,
            @RequestParam(required=false) List<String> styleTags,
            @RequestParam(required=false) String cursor){
        return service.feed(eventType,city,styleTags,cursor,JwtAuthFilter.currentAccountId());
    }
    @GetMapping("/{idOrSlug}")
    public DiscoveryDtos.Showcase detail(@PathVariable String idOrSlug){return service.publicShowcase(idOrSlug,JwtAuthFilter.currentAccountId());}
    @GetMapping("/me/owned") @PreAuthorize("hasRole('VENDOR')")
    public DiscoveryDtos.ShowcaseFeedResponse mine(){return service.myShowcases(JwtAuthFilter.currentAccountId());}
    @GetMapping("/me/pending-tags") @PreAuthorize("hasRole('VENDOR')")
    public DiscoveryDtos.ShowcaseFeedResponse pending(){return service.pendingTags(JwtAuthFilter.currentAccountId());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('VENDOR')")
    public DiscoveryDtos.Showcase create(@Valid @RequestBody DiscoveryDtos.ShowcaseRequest request){return service.createShowcase(JwtAuthFilter.currentAccountId(),request);}
    @PatchMapping("/{id}") @PreAuthorize("hasRole('VENDOR')")
    public DiscoveryDtos.Showcase update(@PathVariable UUID id,@Valid @RequestBody DiscoveryDtos.ShowcaseRequest request){return service.updateShowcase(JwtAuthFilter.currentAccountId(),id,request);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('VENDOR')")
    public void delete(@PathVariable UUID id){service.deleteShowcase(JwtAuthFilter.currentAccountId(),id);}
    @PostMapping("/tags/{tagId}/accept") @PreAuthorize("hasRole('VENDOR')")
    public DiscoveryDtos.Showcase accept(@PathVariable UUID tagId){return service.respondToTag(JwtAuthFilter.currentAccountId(),tagId,true);}
    @PostMapping("/tags/{tagId}/decline") @PreAuthorize("hasRole('VENDOR')")
    public DiscoveryDtos.Showcase decline(@PathVariable UUID tagId){return service.respondToTag(JwtAuthFilter.currentAccountId(),tagId,false);}
}
