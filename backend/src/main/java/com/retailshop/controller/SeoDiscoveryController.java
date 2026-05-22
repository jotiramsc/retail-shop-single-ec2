package com.retailshop.controller;

import com.retailshop.dto.PublicProductResponse;
import com.retailshop.entity.ProductCategoryOption;
import com.retailshop.repository.ProductCategoryOptionRepository;
import com.retailshop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SeoDiscoveryController {

    private static final String SITE_URL = "https://kpskrishnai.com";
    private final ProductService productService;
    private final ProductCategoryOptionRepository productCategoryOptionRepository;

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return """
                User-agent: *
                Allow: /
                Allow: /product/
                Allow: /products
                Allow: /assets/
                Disallow: /app
                Disallow: /login
                Disallow: /api/

                Sitemap: https://kpskrishnai.com/sitemap.xml
                Sitemap: https://kpskrishnai.com/sitemap-products.xml
                Sitemap: https://kpskrishnai.com/sitemap-categories.xml
                Sitemap: https://kpskrishnai.com/sitemap-images.xml
                """;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        return xmlUrlSet(List.of(
                url(SITE_URL + "/", null),
                url(SITE_URL + "/products", null),
                url(SITE_URL + "/privacy-policy", null),
                url(SITE_URL + "/ai-catalog.json", null)
        ));
    }

    @GetMapping(value = "/sitemap-products.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String productSitemap() {
        return xmlUrlSet(productService.getPublicCatalog().stream()
                .map(product -> url(productUrl(product), product.getCreatedAt() == null ? null : product.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE)))
                .toList());
    }

    @GetMapping(value = "/sitemap-categories.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String categorySitemap() {
        return xmlUrlSet(productCategoryOptionRepository.findByActiveTrueOrderByDisplayNameAsc().stream()
                .map(category -> url(SITE_URL + "/products?category=" + encodeSlug(category.getCode()), null))
                .toList());
    }

    @GetMapping(value = "/sitemap-images.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String imageSitemap() {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:image="http://www.google.com/schemas/sitemap-image/1.1">
                """);
        productService.getPublicCatalog().forEach(product -> {
            xml.append("  <url><loc>").append(escape(productUrl(product))).append("</loc>");
            safeList(product.getProductImages()).forEach(image -> xml.append("<image:image><image:loc>")
                    .append(escape(image))
                    .append("</image:loc><image:title>")
                    .append(escape(product.getName()))
                    .append("</image:title></image:image>"));
            xml.append("</url>\n");
        });
        return xml.append("</urlset>").toString();
    }

    @GetMapping(value = "/ai-catalog.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> aiCatalog() {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("site", "KPS Krishnai Pearl Shopee");
        catalog.put("url", SITE_URL);
        catalog.put("purpose", "AI-readable product catalog for jewellery discovery, WhatsApp sales assistant, and multilingual search.");
        catalog.put("products", productService.getPublicCatalog().stream().map(this::aiProduct).toList());
        return catalog;
    }

    private Map<String, Object> aiProduct(PublicProductResponse product) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", product.getName());
        item.put("description", firstNonBlank(product.getAiDescription(), product.getDescription(), product.getName()));
        item.put("keywords", List.of(product.getName(), safe(product.getCategory()), "jewellery", "mala", "haar", "हार", "mangalsutra", "bridal", "gift", "daily wear"));
        item.put("category", product.getCategory());
        item.put("price", money(product.getOfferPrice() == null ? product.getSellingPrice() : product.getOfferPrice()));
        item.put("image", product.getImageDataUrl());
        item.put("images", safeList(product.getProductImages()));
        item.put("occasion", List.of("daily wear", "festival", "wedding", "gift"));
        item.put("color", List.of("gold", "green", "traditional", "pearl"));
        item.put("multilingualKeywords", Map.of(
                "marathi", List.of("माळ", "हार", "मंगळसूत्र", "दागिने", "बांगड्या"),
                "hindi", List.of("हार", "मंगलसूत्र", "गहने", "चूड़ियां"),
                "hinglish", List.of("mala", "haar", "neckless", "mangalsutra", "bangles")
        ));
        item.put("offer", Map.of(
                "name", safe(product.getOfferName()),
                "couponCode", safe(product.getCouponCode()),
                "discountPercent", money(product.getDiscountPercent())
        ));
        item.put("url", productUrl(product));
        return item;
    }

    private String productUrl(PublicProductResponse product) {
        return SITE_URL + "/product/" + firstNonBlank(product.getSlug(), String.valueOf(product.getId()));
    }

    private Map<String, String> url(String loc, String lastmod) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("loc", loc);
        if (lastmod != null) {
            row.put("lastmod", lastmod);
        }
        return row;
    }

    private String xmlUrlSet(List<Map<String, String>> urls) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        urls.forEach(row -> {
            xml.append("  <url><loc>").append(escape(row.get("loc"))).append("</loc>");
            if (row.containsKey("lastmod")) {
                xml.append("<lastmod>").append(escape(row.get("lastmod"))).append("</lastmod>");
            }
            xml.append("</url>\n");
        });
        return xml.append("</urlset>").toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String encodeSlug(String value) {
        return firstNonBlank(value).replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private String escape(String value) {
        return safe(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
