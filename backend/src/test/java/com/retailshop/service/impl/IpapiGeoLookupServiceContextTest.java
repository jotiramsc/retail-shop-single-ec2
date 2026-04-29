package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class IpapiGeoLookupServiceContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(IpapiGeoLookupService.class);

    @Test
    void shouldCreateGeoLookupServiceBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(IpapiGeoLookupService.class));
    }
}
