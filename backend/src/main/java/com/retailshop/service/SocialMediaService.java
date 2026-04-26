package com.retailshop.service;

import com.retailshop.entity.Campaign;

public interface SocialMediaService {
    MarketingChannelResult publishInstagram(Campaign campaign);

    MarketingChannelResult publishFacebook(Campaign campaign);
}
