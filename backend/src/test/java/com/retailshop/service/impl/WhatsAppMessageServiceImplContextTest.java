package com.retailshop.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailshop.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppMessageServiceImplContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AppProperties.class, AppProperties::new)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(WhatsAppMessageServiceImpl.class);

    @Test
    void shouldCreateWhatsAppMessageServiceBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(WhatsAppMessageServiceImpl.class));
    }
}
