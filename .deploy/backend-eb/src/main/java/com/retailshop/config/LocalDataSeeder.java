package com.retailshop.config;

import com.retailshop.entity.Customer;
import com.retailshop.entity.Offer;
import com.retailshop.entity.Product;
import com.retailshop.entity.ReceiptSettings;
import com.retailshop.enums.OfferType;
import com.retailshop.enums.ProductCategory;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.OfferRepository;
import com.retailshop.repository.ProductRepository;
import com.retailshop.repository.ReceiptSettingsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
@Profile("local")
public class LocalDataSeeder {

    private static final LocalDateTime SEEDED_AT = LocalDateTime.of(2026, 4, 22, 9, 0);

    @Bean
    CommandLineRunner seedLocalData(ProductRepository productRepository,
                                    CustomerRepository customerRepository,
                                    OfferRepository offerRepository,
                                    ReceiptSettingsRepository receiptSettingsRepository) {
        return args -> {
            if (productRepository.count() == 0) {
                productRepository.saveAll(sampleProducts());
            }

            if (customerRepository.count() == 0) {
                customerRepository.saveAll(sampleCustomers());
            }

            if (offerRepository.count() == 0) {
                Map<UUID, Product> productsById = productRepository.findAll().stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity()));
                offerRepository.saveAll(sampleOffers(productsById));
            }

            if (receiptSettingsRepository.count() == 0) {
                receiptSettingsRepository.save(sampleReceiptSettings());
            }
        };
    }

    private List<Product> sampleProducts() {
        return List.of(
                product("a1111111-1111-1111-1111-111111111111", "Rose Matte Lipstick", ProductCategory.COSMETICS, "COS-LIP-001", "180.00", "299.00", 25, 8, "2027-12-31", null),
                product("a2222222-2222-2222-2222-222222222222", "Gold Tone Earrings", ProductCategory.JEWELLERY, "JEW-EAR-001", "450.00", "899.00", 14, 5, null, null),
                product("a3333333-3333-3333-3333-333333333333", "Hydra Glow Serum", ProductCategory.COSMETICS, "COS-SER-001", "320.00", "599.00", 4, 6, "2027-10-30", null),
                product("a4444444-4444-4444-4444-444444444444", "Velvet Kajal Pencil", ProductCategory.COSMETICS, "COS-KAJ-001", "70.00", "149.00", 40, 10, "2028-03-31", null),
                product("a5555555-5555-5555-5555-555555555555", "Radiance Compact Powder", ProductCategory.COSMETICS, "COS-COM-001", "190.00", "349.00", 22, 7, "2027-11-30", null),
                product("a6666666-6666-6666-6666-666666666666", "Aloe Vera Face Wash", ProductCategory.COSMETICS, "COS-FAC-001", "110.00", "225.00", 30, 8, "2027-09-30", null),
                product("a7777777-7777-7777-7777-777777777777", "Cherry Tint Nail Paint", ProductCategory.COSMETICS, "COS-NAI-001", "55.00", "120.00", 36, 10, "2028-01-15", null),
                product("a8888888-8888-8888-8888-888888888888", "Silk Finish Foundation", ProductCategory.COSMETICS, "COS-FOU-001", "260.00", "499.00", 18, 6, "2027-08-31", null),
                product("a9999999-9999-9999-9999-999999999999", "Crystal Bracelet", ProductCategory.JEWELLERY, "JEW-BRA-001", "320.00", "699.00", 20, 5, null, null),
                product("ab111111-1111-1111-1111-111111111111", "Pearl Pendant Set", ProductCategory.JEWELLERY, "JEW-PEN-001", "540.00", "1199.00", 12, 4, null, null),
                product("ab222222-2222-2222-2222-222222222222", "Bridal Bangles Set", ProductCategory.JEWELLERY, "JEW-BAN-001", "680.00", "1499.00", 10, 4, null, null),
                product("ab333333-3333-3333-3333-333333333333", "Silver Tone Anklet", ProductCategory.JEWELLERY, "JEW-ANK-001", "210.00", "499.00", 26, 6, null, null),
                product("ab444444-4444-4444-4444-444444444444", "Meenakari Ring", ProductCategory.JEWELLERY, "JEW-RIN-001", "180.00", "399.00", 24, 6, null, null),
                product("ac111111-1111-1111-1111-111111111111", "Luminous Blush Palette", ProductCategory.COSMETICS, "COS-BLU-001", "210.00", "389.00", 16, 6, "2028-04-30", "https://picsum.photos/seed/luminous-blush-palette/600/600"),
                product("ac222222-2222-2222-2222-222222222222", "Vitamin C Day Cream", ProductCategory.COSMETICS, "COS-DAY-001", "240.00", "449.00", 20, 7, "2028-02-28", "https://picsum.photos/seed/vitamin-c-day-cream/600/600"),
                product("ac333333-3333-3333-3333-333333333333", "Satin Nude Lip Gloss", ProductCategory.COSMETICS, "COS-GLS-001", "95.00", "189.00", 28, 8, "2028-06-30", "https://picsum.photos/seed/satin-nude-lip-gloss/600/600"),
                product("ac444444-4444-4444-4444-444444444444", "Royal Kohl Eyeliner", ProductCategory.COSMETICS, "COS-EYE-001", "80.00", "169.00", 34, 10, "2028-03-31", "https://picsum.photos/seed/royal-kohl-eyeliner/600/600"),
                product("ac555555-5555-5555-5555-555555555555", "Dew Mist Primer", ProductCategory.COSMETICS, "COS-PRI-001", "180.00", "329.00", 18, 6, "2028-05-31", "https://picsum.photos/seed/dew-mist-primer/600/600"),
                product("ac666666-6666-6666-6666-666666666666", "Charcoal Peel Mask", ProductCategory.COSMETICS, "COS-MSK-001", "120.00", "249.00", 22, 8, "2027-12-31", "https://picsum.photos/seed/charcoal-peel-mask/600/600"),
                product("ac777777-7777-7777-7777-777777777777", "Rosewater Toner", ProductCategory.COSMETICS, "COS-TON-001", "90.00", "199.00", 26, 9, "2028-01-31", "https://picsum.photos/seed/rosewater-toner/600/600"),
                product("ac888888-8888-8888-8888-888888888888", "Velvet Touch Concealer", ProductCategory.COSMETICS, "COS-CON-001", "160.00", "299.00", 19, 6, "2028-07-31", "https://picsum.photos/seed/velvet-touch-concealer/600/600"),
                product("ac999999-9999-9999-9999-999999999999", "Cocoa Brow Kit", ProductCategory.COSMETICS, "COS-BRO-001", "110.00", "229.00", 15, 5, "2028-08-31", "https://picsum.photos/seed/cocoa-brow-kit/600/600"),
                product("ad111111-1111-1111-1111-111111111111", "Moonlight Highlighter", ProductCategory.COSMETICS, "COS-HIG-001", "175.00", "319.00", 17, 6, "2028-09-30", "https://picsum.photos/seed/moonlight-highlighter/600/600"),
                product("ad222222-2222-2222-2222-222222222222", "Kundan Choker Set", ProductCategory.JEWELLERY, "JEW-CHO-001", "890.00", "1799.00", 8, 3, null, "https://picsum.photos/seed/kundan-choker-set/600/600"),
                product("ad333333-3333-3333-3333-333333333333", "Minimal Hoop Earrings", ProductCategory.JEWELLERY, "JEW-HOO-001", "260.00", "599.00", 18, 5, null, "https://picsum.photos/seed/minimal-hoop-earrings/600/600"),
                product("ad444444-4444-4444-4444-444444444444", "Emerald Stone Ring", ProductCategory.JEWELLERY, "JEW-EMR-001", "340.00", "749.00", 14, 5, null, "https://picsum.photos/seed/emerald-stone-ring/600/600"),
                product("ad555555-5555-5555-5555-555555555555", "Temple Design Necklace", ProductCategory.JEWELLERY, "JEW-TEM-001", "980.00", "2099.00", 6, 2, null, "https://picsum.photos/seed/temple-design-necklace/600/600"),
                product("ad666666-6666-6666-6666-666666666666", "Rose Gold Pendant Chain", ProductCategory.JEWELLERY, "JEW-ROS-001", "410.00", "899.00", 11, 4, null, "https://picsum.photos/seed/rose-gold-pendant-chain/600/600"),
                product("ad777777-7777-7777-7777-777777777777", "Pearl Drop Earrings", ProductCategory.JEWELLERY, "JEW-PRL-001", "300.00", "699.00", 13, 4, null, "https://picsum.photos/seed/pearl-drop-earrings/600/600"),
                product("ad888888-8888-8888-8888-888888888888", "Statement Cuff Bracelet", ProductCategory.JEWELLERY, "JEW-CUF-001", "520.00", "1099.00", 9, 3, null, "https://picsum.photos/seed/statement-cuff-bracelet/600/600"),
                product("ad999999-9999-9999-9999-999999999999", "Crystal Nose Pin", ProductCategory.JEWELLERY, "JEW-NOS-001", "90.00", "249.00", 25, 8, null, "https://picsum.photos/seed/crystal-nose-pin/600/600"),
                product("ae111111-1111-1111-1111-111111111111", "Antique Jhumka Pair", ProductCategory.JEWELLERY, "JEW-JHU-001", "380.00", "849.00", 12, 4, null, "https://picsum.photos/seed/antique-jhumka-pair/600/600"),
                product("ae222222-2222-2222-2222-222222222222", "Layered Charm Anklet", ProductCategory.JEWELLERY, "JEW-LAY-001", "170.00", "429.00", 16, 5, null, "https://picsum.photos/seed/layered-charm-anklet/600/600"),
                product("af111111-1111-1111-1111-111111111111", "Petal Glow BB Cream", ProductCategory.COSMETICS, "COS-BBC-001", "175.00", "329.00", 21, 7, "2028-10-31", "https://picsum.photos/seed/petal-glow-bb-cream/600/600"),
                product("af222222-2222-2222-2222-222222222222", "Saffron Night Repair", ProductCategory.COSMETICS, "COS-NGT-001", "290.00", "549.00", 15, 5, "2028-12-31", "https://picsum.photos/seed/saffron-night-repair/600/600"),
                product("af333333-3333-3333-3333-333333333333", "Coral Bloom Lip Crayon", ProductCategory.COSMETICS, "COS-CRY-001", "110.00", "219.00", 27, 8, "2028-08-31", "https://picsum.photos/seed/coral-bloom-lip-crayon/600/600"),
                product("af444444-4444-4444-4444-444444444444", "Matte Velvet Rouge", ProductCategory.COSMETICS, "COS-MVR-001", "150.00", "289.00", 19, 6, "2028-11-30", "https://picsum.photos/seed/matte-velvet-rouge/600/600"),
                product("af555555-5555-5555-5555-555555555555", "Rice Water Cleanser", ProductCategory.COSMETICS, "COS-RWC-001", "125.00", "239.00", 24, 8, "2028-09-30", "https://picsum.photos/seed/rice-water-cleanser/600/600"),
                product("af666666-6666-6666-6666-666666666666", "Orchid Silk Sunscreen", ProductCategory.COSMETICS, "COS-SUN-001", "210.00", "399.00", 18, 6, "2028-07-31", "https://picsum.photos/seed/orchid-silk-sunscreen/600/600"),
                product("af777777-7777-7777-7777-777777777777", "Glow Fix Setting Spray", ProductCategory.COSMETICS, "COS-STS-001", "130.00", "259.00", 26, 9, "2028-05-31", "https://picsum.photos/seed/glow-fix-setting-spray/600/600"),
                product("af888888-8888-8888-8888-888888888888", "Berry Pop Cheek Tint", ProductCategory.COSMETICS, "COS-CHK-001", "115.00", "229.00", 23, 7, "2028-06-30", "https://picsum.photos/seed/berry-pop-cheek-tint/600/600"),
                product("af999999-9999-9999-9999-999999999999", "Silk Lash Mascara", ProductCategory.COSMETICS, "COS-MAS-001", "140.00", "279.00", 20, 7, "2028-04-30", "https://picsum.photos/seed/silk-lash-mascara/600/600"),
                product("ag111111-1111-1111-1111-111111111111", "Pearl Finish Primer", ProductCategory.COSMETICS, "COS-PFP-001", "185.00", "349.00", 17, 5, "2028-12-31", "https://picsum.photos/seed/pearl-finish-primer/600/600"),
                product("ag222222-2222-2222-2222-222222222222", "Zircon Stud Earrings", ProductCategory.JEWELLERY, "JEW-ZIR-001", "240.00", "549.00", 22, 6, null, "https://picsum.photos/seed/zircon-stud-earrings/600/600"),
                product("ag333333-3333-3333-3333-333333333333", "Heritage Kundan Haram", ProductCategory.JEWELLERY, "JEW-HAR-001", "1250.00", "2499.00", 7, 2, null, "https://picsum.photos/seed/heritage-kundan-haram/600/600"),
                product("ag444444-4444-4444-4444-444444444444", "Lotus Charm Bracelet", ProductCategory.JEWELLERY, "JEW-LCB-001", "310.00", "699.00", 18, 5, null, "https://picsum.photos/seed/lotus-charm-bracelet/600/600"),
                product("ag555555-5555-5555-5555-555555555555", "Navratna Finger Ring", ProductCategory.JEWELLERY, "JEW-NAV-001", "280.00", "629.00", 16, 5, null, "https://picsum.photos/seed/navratna-finger-ring/600/600"),
                product("ag666666-6666-6666-6666-666666666666", "Polki Drop Earrings", ProductCategory.JEWELLERY, "JEW-POL-001", "360.00", "799.00", 14, 4, null, "https://picsum.photos/seed/polki-drop-earrings/600/600"),
                product("ag777777-7777-7777-7777-777777777777", "Bridal Maang Tikka", ProductCategory.JEWELLERY, "JEW-MTK-001", "420.00", "949.00", 10, 3, null, "https://picsum.photos/seed/bridal-maang-tikka/600/600"),
                product("ag888888-8888-8888-8888-888888888888", "Sleek Chain Necklace", ProductCategory.JEWELLERY, "JEW-SCN-001", "390.00", "859.00", 13, 4, null, "https://picsum.photos/seed/sleek-chain-necklace/600/600"),
                product("ag999999-9999-9999-9999-999999999999", "Ruby Stone Bangle", ProductCategory.JEWELLERY, "JEW-RUB-001", "520.00", "1129.00", 11, 3, null, "https://picsum.photos/seed/ruby-stone-bangle/600/600"),
                product("ah111111-1111-1111-1111-111111111111", "Floral Nose Ring", ProductCategory.JEWELLERY, "JEW-FLR-001", "95.00", "259.00", 21, 7, null, "https://picsum.photos/seed/floral-nose-ring/600/600"),
                product("ah222222-2222-2222-2222-222222222222", "Pearl Anklet Duo", ProductCategory.JEWELLERY, "JEW-PAD-001", "220.00", "489.00", 19, 6, null, "https://picsum.photos/seed/pearl-anklet-duo/600/600")
        );
    }

    private List<Customer> sampleCustomers() {
        return List.of(
                customer("b1111111-1111-1111-1111-111111111111", "Anika Sharma", "9876543210"),
                customer("b2222222-2222-2222-2222-222222222222", "Riya Patel", "9988776655"),
                customer("b3333333-3333-3333-3333-333333333333", "Kavya Mehta", "9123456780"),
                customer("b4444444-4444-4444-4444-444444444444", "Sneha Iyer", "9123456781"),
                customer("b5555555-5555-5555-5555-555555555555", "Pooja Nair", "9123456782"),
                customer("b6666666-6666-6666-6666-666666666666", "Meera Joshi", "9123456783"),
                customer("b7777777-7777-7777-7777-777777777777", "Ishita Shah", "9123456784"),
                customer("b8888888-8888-8888-8888-888888888888", "Naina Verma", "9123456785"),
                customer("b9999999-9999-9999-9999-999999999999", "Bhavya Rao", "9123456786"),
                customer("ba111111-1111-1111-1111-111111111111", "Simran Kaur", "9123456787"),
                customer("ba222222-2222-2222-2222-222222222222", "Diya Malhotra", "9123456788"),
                customer("ba333333-3333-3333-3333-333333333333", "Tara Desai", "9123456789")
        );
    }

    private List<Offer> sampleOffers(Map<UUID, Product> productsById) {
        return List.of(
                offer("c1111111-1111-1111-1111-111111111111", "Glow Week 10%", OfferType.CATEGORY, "10.00", ProductCategory.COSMETICS, null, LocalDate.now().minusDays(5), LocalDate.now().plusDays(10), true),
                offer("c2222222-2222-2222-2222-222222222222", "Earrings Flat 50", OfferType.FLAT, "50.00", null, productsById.get(UUID.fromString("a2222222-2222-2222-2222-222222222222")), LocalDate.now().minusDays(1), LocalDate.now().plusDays(15), true)
        );
    }

    private ReceiptSettings sampleReceiptSettings() {
        ReceiptSettings settings = new ReceiptSettings();
        settings.setId(parseSeedUuid("d1111111-1111-1111-1111-111111111111"));
        settings.setShopName("Luxe Retail Studio");
        settings.setHeaderLine("Ladies Cosmetics and Jewellery");
        settings.setAddress("12 Fashion Street, City Center");
        settings.setPhoneNumber("+91 98765 43210");
        settings.setGstNumber("GSTIN-22ABCDE1234F1Z5");
        settings.setFooterNote("Thank you for shopping with us");
        settings.setShowAddress(true);
        settings.setShowPhoneNumber(true);
        settings.setShowGstNumber(false);
        return settings;
    }

    private Product product(String id,
                            String name,
                            ProductCategory category,
                            String sku,
                            String costPrice,
                            String sellingPrice,
                            int quantity,
                            int lowStockThreshold,
                            String expiryDate,
                            String imageDataUrl) {
        Product product = new Product();
        product.setId(parseSeedUuid(id));
        product.setName(name);
        product.setCategory(category);
        product.setSku(sku);
        product.setCostPrice(new BigDecimal(costPrice));
        product.setSellingPrice(new BigDecimal(sellingPrice));
        product.setQuantity(quantity);
        product.setLowStockThreshold(lowStockThreshold);
        product.setExpiryDate(expiryDate != null ? LocalDate.parse(expiryDate) : null);
        product.setImageDataUrl(imageDataUrl);
        product.setCreatedAt(SEEDED_AT);
        return product;
    }

    private Customer customer(String id, String name, String mobile) {
        Customer customer = new Customer();
        customer.setId(parseSeedUuid(id));
        customer.setName(name);
        customer.setMobile(mobile);
        customer.setCreatedAt(SEEDED_AT);
        return customer;
    }

    private Offer offer(String id,
                        String name,
                        OfferType type,
                        String value,
                        ProductCategory category,
                        Product product,
                        LocalDate startDate,
                        LocalDate endDate,
                        boolean active) {
        Offer offer = new Offer();
        offer.setId(parseSeedUuid(id));
        offer.setName(name);
        offer.setType(type);
        offer.setValue(new BigDecimal(value));
        offer.setCategory(category);
        offer.setProduct(product);
        offer.setStartDate(startDate);
        offer.setEndDate(endDate);
        offer.setActive(active);
        return offer;
    }

    private UUID parseSeedUuid(String seed) {
        try {
            return UUID.fromString(seed);
        } catch (IllegalArgumentException exception) {
            return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        }
    }
}
