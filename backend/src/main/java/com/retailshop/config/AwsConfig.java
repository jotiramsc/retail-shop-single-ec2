package com.retailshop.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class AwsConfig {

    private final AppProperties appProperties;
    private final Environment environment;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(appProperties.getAws().getRegion()));

        if (isDevProfile() && hasDevStaticCredentials()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            appProperties.getAws().getAccessKeyId().trim(),
                            appProperties.getAws().getSecretAccessKey().trim()
                    )
            ));
        }

        return builder.build();
    }

    private boolean isDevProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("dev") || profile.equalsIgnoreCase("local"));
    }

    private boolean hasDevStaticCredentials() {
        return !isBlank(appProperties.getAws().getAccessKeyId())
                && !isBlank(appProperties.getAws().getSecretAccessKey());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
