package com.retailshop.controller;

import com.retailshop.dto.OfferRequest;
import com.retailshop.dto.OfferResponse;
import com.retailshop.dto.OfferSuggestionResponse;
import com.retailshop.dto.PaginatedResponse;
import com.retailshop.service.AutomationService;
import com.retailshop.service.OfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_OFFERS')")
public class OfferController {

    private final OfferService offerService;
    private final AutomationService automationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse createOffer(@Valid @RequestBody OfferRequest request) {
        return offerService.createOffer(request);
    }

    @PutMapping("/{id}")
    public OfferResponse updateOffer(@PathVariable UUID id, @Valid @RequestBody OfferRequest request) {
        return offerService.updateOffer(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOffer(@PathVariable UUID id) {
        offerService.deleteOffer(id);
    }

    @GetMapping
    public PaginatedResponse<OfferResponse> getOffers(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return offerService.getActiveOffers(pageable);
    }

    @GetMapping("/suggested")
    public List<OfferSuggestionResponse> getSuggestedOffers() {
        return automationService.suggestOffersForSlowMovingProducts();
    }
}
