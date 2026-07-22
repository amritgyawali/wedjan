package com.wedjan.api.discovery;

import com.wedjan.api.config.JwtAuthFilter;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discovery")
public class CustomerDiscoveryController {
    private final DiscoveryService service;
    public CustomerDiscoveryController(DiscoveryService service){this.service=service;}
    @GetMapping("/favorites") public DiscoveryDtos.FavoriteListResponse favorites(){return service.favorites(id());}
    @PostMapping("/favorites") @ResponseStatus(HttpStatus.CREATED)
    public DiscoveryDtos.Favorite favorite(@Valid @RequestBody DiscoveryDtos.FavoriteRequest request){return service.favorite(id(),request);}
    @DeleteMapping("/favorites/{type}/{entityId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(@PathVariable DiscoveryDtos.EntityType type,@PathVariable UUID entityId){service.unfavorite(id(),type,entityId);}
    @GetMapping("/shortlists") public DiscoveryDtos.ShortlistListResponse shortlists(){return service.shortlists(id());}
    @PostMapping("/shortlists") @ResponseStatus(HttpStatus.CREATED)
    public DiscoveryDtos.Shortlist create(@Valid @RequestBody DiscoveryDtos.ShortlistRequest request){return service.createShortlist(id(),request);}
    @PostMapping("/shortlists/{shortlistId}/items")
    public DiscoveryDtos.Shortlist add(@PathVariable UUID shortlistId,@Valid @RequestBody DiscoveryDtos.ShortlistItemRequest request){return service.addShortlistItem(id(),shortlistId,request);}
    @DeleteMapping("/shortlists/{shortlistId}/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID shortlistId,@PathVariable UUID itemId){service.deleteShortlistItem(id(),shortlistId,itemId);}
    @GetMapping("/compare") public DiscoveryDtos.CompareResponse compare(@RequestParam List<UUID> packageIds,@RequestParam(required=false) String currency){return service.compare(id(),packageIds,currency);}
    private static UUID id(){return JwtAuthFilter.currentAccountId();}
}
