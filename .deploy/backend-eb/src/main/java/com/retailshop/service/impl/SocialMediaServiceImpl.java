package com.retailshop.service.impl;

import com.retailshop.service.SocialMediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SocialMediaServiceImpl implements SocialMediaService {

    @Override
    public boolean postToInstagram(String content) {
        log.info("Mock Instagram post created: {}", content);
        return true;
    }

    @Override
    public boolean postToFacebook(String content) {
        log.info("Mock Facebook post created: {}", content);
        return true;
    }
}
