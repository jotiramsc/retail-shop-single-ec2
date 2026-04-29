package com.retailshop.service;

import com.retailshop.dto.GeoLookupResult;

public interface GeoLookupService {
    GeoLookupResult lookup(String ipAddress);
    GeoLookupResult reverseLookup(Double latitude, Double longitude);
}
