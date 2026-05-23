package com.retailshop.service.impl;

import com.retailshop.dto.MarketingCampaignSuggestionResponse;
import com.retailshop.enums.MarketingCampaignType;
import com.retailshop.enums.MarketingDiscountType;
import com.retailshop.enums.MarketingLanguage;
import com.retailshop.enums.MarketingPlatform;
import com.retailshop.enums.MarketingTone;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MarketingOccasionLibrary {

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final List<MarketingPlatform> DEFAULT_PLATFORMS = List.of(
            MarketingPlatform.INSTAGRAM,
            MarketingPlatform.FACEBOOK,
            MarketingPlatform.WHATSAPP
    );

    private static final List<Occasion> OCCASIONS = List.of(
            fixed("makar-sankranti", "Makar Sankranti", "मकर संक्रांत", 1, 14,
                    "Tilgul gifting and festive jewellery picks for haldi-kumkum season.",
                    "तिळगूळ समारंभासाठी खास ज्वेलरी आणि भेटवस्तू",
                    "#मकरसंक्रांत", "#MakarSankranti",
                    "haldi-kumkum warmth, black-bead styling, bangles and festive gifting", BigDecimal.valueOf(15)),
            fixed("republic-day", "Republic Day", "प्रजासत्ताक दिन", 1, 26,
                    "Patriotic campaign for tricolour styling, gifting, and store-wide savings.",
                    "प्रजासत्ताक दिन विशेष निवडक कलेक्शन",
                    "#प्रजासत्ताकदिन", "#RepublicDay",
                    "tricolour accents, pride, elegant retail display", BigDecimal.valueOf(10)),
            approximate("vasant-panchami", "Vasant Panchami", "वसंत पंचमी", 2, 6,
                    "Bright spring-ready campaign for elegant festive dressing.",
                    "वसंत पंचमीसाठी ताजेतवाने उत्सवी कलेक्शन",
                    "#वसंतपंचमी", "#VasantPanchami",
                    "yellow festive accents, spring freshness and graceful styling", BigDecimal.valueOf(12)),
            fixed("womens-day", "Women's Day", "महिला दिन", 3, 8,
                    "A women-first self-gifting and celebration campaign for jewellery and beauty.",
                    "महिला दिनासाठी स्वतःसाठी खास निवड",
                    "#महिलादिन", "#WomensDay",
                    "women-first premium gifting, empowering visuals, elegant beauty styling", BigDecimal.valueOf(18)),
            fixed("valentines-day", "Valentine's Day", "व्हॅलेंटाईन डे", 2, 14,
                    "Gift-led romantic campaign for premium gifting and couple occasions.",
                    "व्हॅलेंटाईन डे साठी खास गिफ्ट कलेक्शन",
                    "#व्हॅलेंटाईनडे", "#ValentinesDay",
                    "romantic premium gifting, elegant red, pearl and jewel-tone palette", BigDecimal.valueOf(15)),
            approximate("mahashivratri", "Mahashivratri", "महाशिवरात्री", 3, 5,
                    "Devotional festive campaign with elegant traditional styling.",
                    "महाशिवरात्रीसाठी खास पारंपरिक निवड",
                    "#महाशिवरात्री", "#Mahashivratri",
                    "devotional mood, temple styling and elegant festive jewellery", BigDecimal.valueOf(12)),
            approximate("holi", "Holi", "होळी", 3, 13,
                    "Colorful festive campaign for playful beauty and gifting picks.",
                    "होळीसाठी रंगतदार सौंदर्य आणि गिफ्ट कलेक्शन",
                    "#होळी", "#Holi",
                    "festive colour, celebratory beauty styling and joyful gifting", BigDecimal.valueOf(12)),
            approximate("rang-panchami", "Rang Panchami", "रंग पंचमी", 3, 18,
                    "Post-Holi colour celebration campaign for beauty and festive dressing.",
                    "रंग पंचमीसाठी आकर्षक रंगीत कलेक्शन",
                    "#रंगपंचमी", "#RangPanchami",
                    "playful festive colours, cosmetic highlights and cheerful gifting", BigDecimal.valueOf(10)),
            approximate("gudi-padwa", "Gudi Padwa", "गुढी पाडवा", 4, 7,
                    "Marathi new year campaign with auspicious festive styling.",
                    "गुढी पाडव्यासाठी खास उत्सवी कलेक्शन",
                    "#गुढीपाडवा", "#GudiPadwa",
                    "new year celebration, saffron silk, mangalya and festive entrance decor", BigDecimal.valueOf(15)),
            approximate("ram-navami", "Ram Navami", "राम नवमी", 4, 15,
                    "Traditional family-oriented festive campaign for premium gifting.",
                    "राम नवमीसाठी पारंपरिक निवड",
                    "#रामनवमी", "#RamNavami",
                    "traditional devotion, family festive styling and elegant gifting", BigDecimal.valueOf(10)),
            approximate("hanuman-jayanti", "Hanuman Jayanti", "हनुमान जयंती", 4, 21,
                    "Devotional occasion campaign with graceful festive buying cues.",
                    "हनुमान जयंती निमित्त खास ऑफर",
                    "#हनुमानजयंती", "#HanumanJayanti",
                    "devotional celebration, saffron and pearl accents, family shopping mood", BigDecimal.valueOf(10)),
            approximate("akshaya-tritiya", "Akshaya Tritiya", "अक्षय तृतीया", 5, 3,
                    "Auspicious jewellery-shopping campaign for necklaces, bangles, earrings and festive buying.",
                    "अक्षय तृतीयेची ज्वेलरीसाठी खास ऑफर",
                    "#अक्षयतृतीया", "#AkshayaTritiya",
                    "auspicious jewellery shopping, necklaces, bangles, earrings, sacred festive styling", BigDecimal.valueOf(20)),
            fixed("maharashtra-day", "Maharashtra Day", "महाराष्ट्र दिन", 5, 1,
                    "Proud local campaign celebrating Maharashtra shoppers and regional style.",
                    "महाराष्ट्र दिनासाठी खास स्थानिक ऑफर",
                    "#महाराष्ट्रदिन", "#MaharashtraDay",
                    "Marathi pride, local celebration and premium regional styling", BigDecimal.valueOf(12)),
            nthWeekday("mothers-day", "Mother's Day", "मातृ दिन", Month.MAY, DayOfWeek.SUNDAY, 2,
                    "Gift-driven campaign focused on thoughtful gifting for mothers.",
                    "आईसाठी खास भेटवस्तू",
                    "#मातृदिन", "#MothersDay",
                    "warm family gifting, elegant jewellery accents and emotional storytelling", BigDecimal.valueOf(15)),
            approximate("vat-pournima", "Vat Pournima", "वट पौर्णिमा", 6, 15,
                    "Married-women festive campaign with traditional premium styling.",
                    "वट पौर्णिमेसाठी खास पारंपरिक कलेक्शन",
                    "#वटपौर्णिमा", "#VatPournima",
                    "married women styling, mangalsutra-led elegance and festive devotion", BigDecimal.valueOf(12)),
            nthWeekday("fathers-day", "Father's Day", "पितृ दिन", Month.JUNE, DayOfWeek.SUNDAY, 3,
                    "Family gifting campaign with premium self-care and gifting angle.",
                    "पितृ दिनासाठी खास भेट निवड",
                    "#पितृदिन", "#FathersDay",
                    "family gifting, premium essentials and emotional appreciation", BigDecimal.valueOf(12)),
            approximate("ashadhi-ekadashi", "Ashadhi Ekadashi", "आषाढी एकादशी", 7, 16,
                    "Devotional pilgrimage-season campaign with traditional curation.",
                    "आषाढी एकादशीसाठी भक्तिमय निवड",
                    "#आषाढीएकादशी", "#AshadhiEkadashi",
                    "devotional journey, temple-inspired styling and simple festive grace", BigDecimal.valueOf(10)),
            approximate("guru-pournima", "Guru Purnima", "गुरुपौर्णिमा", 7, 29,
                    "Respect and gifting campaign around gratitude and celebration.",
                    "गुरुपौर्णिमेसाठी आदरपूर्वक खास निवड",
                    "#गुरुपौर्णिमा", "#GuruPurnima",
                    "gratitude, warm gifting and refined festive elegance", BigDecimal.valueOf(10)),
            approximate("nag-panchami", "Nag Panchami", "नाग पंचमी", 8, 9,
                    "Traditional monsoon festival campaign with family shopping cues.",
                    "नाग पंचमीसाठी खास पारंपरिक ऑफर",
                    "#नागपंचमी", "#NagPanchami",
                    "monsoon festive styling, traditional celebration and family gifting", BigDecimal.valueOf(10)),
            approximate("raksha-bandhan", "Raksha Bandhan", "रक्षाबंधन", 8, 19,
                    "Sibling gifting campaign with emotional festive messaging.",
                    "रक्षाबंधनासाठी खास गिफ्ट कलेक्शन",
                    "#रक्षाबंधन", "#RakshaBandhan",
                    "family gifting, emotional celebration, festive elegance", BigDecimal.valueOf(15)),
            approximate("krishna-janmashtami", "Krishna Janmashtami", "कृष्ण जन्माष्टमी", 8, 26,
                    "Festive devotional campaign with premium gifting and ethnic styling.",
                    "कृष्ण जन्माष्टमीसाठी खास उत्सवी निवड",
                    "#जन्माष्टमी", "#Janmashtami",
                    "devotional celebration, peacock tones, festive joy and gifting", BigDecimal.valueOf(12)),
            fixed("independence-day", "Independence Day", "स्वातंत्र्य दिन", 8, 15,
                    "Patriotic seasonal offer campaign for local shoppers.",
                    "स्वातंत्र्य दिनासाठी खास ऑफर",
                    "#स्वातंत्र्यदिन", "#IndependenceDay",
                    "national pride, tricolour accents and local festive shopping", BigDecimal.valueOf(12)),
            approximate("hartalika-teej", "Hartalika Teej", "हरतालिका तीज", 9, 10,
                    "Women's festive dressing campaign with jewellery focus.",
                    "हरतालिका तीजसाठी खास स्त्री-केंद्रित कलेक्शन",
                    "#हरतालिकातीज", "#HartalikaTeej",
                    "women-focused festive styling, mehendi and celebratory dressing", BigDecimal.valueOf(12)),
            approximate("ganesh-chaturthi", "Ganesh Chaturthi", "गणेश चतुर्थी", 9, 17,
                    "High-traffic festive campaign for family shopping season.",
                    "गणेश चतुर्थीसाठी खास उत्सवी ऑफर",
                    "#गणेशचतुर्थी", "#GaneshChaturthi",
                    "festival decor, family shopping, orange flowers and celebratory jewellery palette", BigDecimal.valueOf(15)),
            approximate("navratri", "Navratri", "नवरात्री", 10, 3,
                    "Multi-day festive campaign for traditional styling and beauty looks.",
                    "नवरात्रीसाठी खास उत्सवी कलेक्शन",
                    "#नवरात्री", "#Navratri",
                    "celebration, color, devotion and festive dressing", BigDecimal.valueOf(15)),
            approximate("dussehra", "Dussehra", "दसरा", 10, 12,
                    "Victory-themed festive campaign for auspicious buying.",
                    "दसर्‍यासाठी खास शुभ खरेदी ऑफर",
                    "#दसरा", "#Dussehra",
                    "auspicious buying, festive victory mood and premium gifting", BigDecimal.valueOf(15)),
            approximate("karwa-chauth", "Karwa Chauth", "करवा चौथ", 10, 18,
                    "Bridal and married-women gifting campaign with premium styling.",
                    "करवा चौथसाठी खास एलिगंट कलेक्शन",
                    "#करवाचौथ", "#KarwaChauth",
                    "bridal elegance, festive dressing and emotional gifting", BigDecimal.valueOf(15)),
            approximate("diwali", "Diwali", "दिवाळी", 11, 8,
                    "Peak festive buying campaign for gifting, jewellery, and beauty picks.",
                    "दिवाळीसाठी खास उत्सवी ऑफर",
                    "#दिवाळीऑफर", "#DiwaliOffers",
                    "lamps, festive sparkle, gifting season, necklaces, bangles and celebratory jewellery palette", BigDecimal.valueOf(20)),
            fixed("christmas", "Christmas", "ख्रिसमस", 12, 25,
                    "Gift-ready holiday campaign with premium beauty and accessory picks.",
                    "ख्रिसमससाठी खास गिफ्ट कलेक्शन",
                    "#ख्रिसमस", "#Christmas",
                    "holiday gifting, red, pearl and jewel-tone festive sparkle", BigDecimal.valueOf(12)),
            fixed("year-end-sale", "Year End Sale", "वर्षअखेर सेल", 12, 28,
                    "Clearance-style premium campaign to close the year strongly.",
                    "वर्षअखेरची खास बचत ऑफर",
                    "#वर्षअखेरसेल", "#YearEndSale",
                    "countdown sale, premium offers and festive year-end energy", BigDecimal.valueOf(20)),
            seasonal("wedding-season", "Wedding Season", "लग्नाचा सीझन",
                    "High-intent bridal and gifting season campaign for statement purchases.",
                    "लग्नाच्या सीझनसाठी ब्रायडल आणि गिफ्ट कलेक्शन",
                    "#लग्नसराई", "#WeddingSeason",
                    "bridal luxury, statement jewellery and celebration styling", BigDecimal.valueOf(18),
                    EnumSet.of(Month.JANUARY, Month.FEBRUARY, Month.APRIL, Month.MAY, Month.JUNE, Month.NOVEMBER, Month.DECEMBER)),
            evergreen("shop-anniversary", "Store Anniversary", "दुकान वर्धापन दिन",
                    "Any-time celebratory campaign for anniversary offers, loyalty, and goodwill.",
                    "दुकान वर्धापन दिनासाठी खास ग्राहक आभार ऑफर",
                    "#वर्धापनदिन", "#StoreAnniversary",
                    "store celebration, gratitude, balloons, premium thank-you offer", BigDecimal.valueOf(15))
    );

    private MarketingOccasionLibrary() {
    }

    static Occasion detectOccasion(String... textParts) {
        String haystack = normalize(String.join(" ", textParts));
        if (haystack.isBlank()) {
            return null;
        }
        return OCCASIONS.stream()
                .filter(occasion -> occasion.matches(haystack))
                .findFirst()
                .orElse(null);
    }

    static List<MarketingCampaignSuggestionResponse> buildSuggestions(LocalDate today, int daysAhead, String landingUrl) {
        LocalDate windowEnd = today.plusDays(Math.max(daysAhead, 1));
        List<MarketingCampaignSuggestionResponse> suggestions = new ArrayList<>();
        for (Occasion occasion : OCCASIONS) {
            MarketingCampaignSuggestionResponse suggestion = null;
            switch (occasion.mode()) {
                case FIXED_DATE, APPROXIMATE_DATE, NTH_WEEKDAY -> {
                    LocalDate nextDate = occasion.nextOccurrence(today);
                    if (nextDate != null && !nextDate.isBefore(today) && !nextDate.isAfter(windowEnd)) {
                        suggestion = occasion.toSuggestion("UPCOMING", nextDate, nextDate, today, landingUrl);
                    }
                }
                case SEASONAL -> {
                    LocalDate seasonAnchor = occasion.nextOccurrence(today);
                    if (occasion.isActive(today) || (seasonAnchor != null && !seasonAnchor.isAfter(windowEnd))) {
                        LocalDate startDate = occasion.isActive(today) ? today : seasonAnchor;
                        suggestion = occasion.toSuggestion("SEASONAL", seasonAnchor, startDate, today, landingUrl);
                    }
                }
                case EVERGREEN -> suggestion = occasion.toSuggestion("EVERGREEN", today, today, today, landingUrl);
            }
            if (suggestion == null) {
                LocalDate nextDate = occasion.nextOccurrence(today);
                LocalDate startDate = nextDate == null || nextDate.isBefore(today) ? today : nextDate.minusDays(7);
                if (startDate.isBefore(today)) {
                    startDate = today;
                }
                suggestion = occasion.toSuggestion("TEMPLATE", nextDate, startDate, today, landingUrl);
            }
            suggestions.add(suggestion);
        }

        suggestions.sort(Comparator
                .comparing((MarketingCampaignSuggestionResponse response) -> suggestionPriority(response.getKind()))
                .thenComparing(response -> response.getHighlightDate() == null ? LocalDate.MAX : response.getHighlightDate()));
        return suggestions;
    }

    private static int suggestionPriority(String kind) {
        return switch (kind) {
            case "UPCOMING" -> 0;
            case "SEASONAL" -> 1;
            case "EVERGREEN" -> 2;
            default -> 3;
        };
    }

    private static Occasion fixed(String key,
                                  String englishName,
                                  String marathiName,
                                  int month,
                                  int day,
                                  String rationale,
                                  String offerTitle,
                                  String marathiHashtag,
                                  String englishHashtag,
                                  String visualHints,
                                  BigDecimal discountValue) {
        return new Occasion(key, englishName, marathiName, rationale, offerTitle, marathiHashtag, englishHashtag, visualHints,
                MarketingCampaignType.FESTIVAL, MarketingTone.FESTIVE, MarketingDiscountType.PERCENTAGE, discountValue,
                OccurrenceMode.FIXED_DATE, Month.of(month), day, null, 0, null, buildKeywords(englishName, marathiName));
    }

    private static Occasion approximate(String key,
                                        String englishName,
                                        String marathiName,
                                        int month,
                                        int day,
                                        String rationale,
                                        String offerTitle,
                                        String marathiHashtag,
                                        String englishHashtag,
                                        String visualHints,
                                        BigDecimal discountValue) {
        return new Occasion(key, englishName, marathiName, rationale, offerTitle, marathiHashtag, englishHashtag, visualHints,
                MarketingCampaignType.FESTIVAL, MarketingTone.FESTIVE, MarketingDiscountType.PERCENTAGE, discountValue,
                OccurrenceMode.APPROXIMATE_DATE, Month.of(month), day, null, 0, null, buildKeywords(englishName, marathiName));
    }

    private static Occasion nthWeekday(String key,
                                       String englishName,
                                       String marathiName,
                                       Month month,
                                       DayOfWeek dayOfWeek,
                                       int ordinal,
                                       String rationale,
                                       String offerTitle,
                                       String marathiHashtag,
                                       String englishHashtag,
                                       String visualHints,
                                       BigDecimal discountValue) {
        return new Occasion(key, englishName, marathiName, rationale, offerTitle, marathiHashtag, englishHashtag, visualHints,
                MarketingCampaignType.SEASONAL, MarketingTone.EMOTIONAL, MarketingDiscountType.PERCENTAGE, discountValue,
                OccurrenceMode.NTH_WEEKDAY, month, null, dayOfWeek, ordinal, null, buildKeywords(englishName, marathiName));
    }

    private static Occasion seasonal(String key,
                                     String englishName,
                                     String marathiName,
                                     String rationale,
                                     String offerTitle,
                                     String marathiHashtag,
                                     String englishHashtag,
                                     String visualHints,
                                     BigDecimal discountValue,
                                     Set<Month> activeMonths) {
        return new Occasion(key, englishName, marathiName, rationale, offerTitle, marathiHashtag, englishHashtag, visualHints,
                MarketingCampaignType.SEASONAL, MarketingTone.LUXURY, MarketingDiscountType.PERCENTAGE, discountValue,
                OccurrenceMode.SEASONAL, null, null, null, 0, activeMonths, buildKeywords(englishName, marathiName));
    }

    private static Occasion evergreen(String key,
                                      String englishName,
                                      String marathiName,
                                      String rationale,
                                      String offerTitle,
                                      String marathiHashtag,
                                      String englishHashtag,
                                      String visualHints,
                                      BigDecimal discountValue) {
        return new Occasion(key, englishName, marathiName, rationale, offerTitle, marathiHashtag, englishHashtag, visualHints,
                MarketingCampaignType.CUSTOM, MarketingTone.PREMIUM, MarketingDiscountType.PERCENTAGE, discountValue,
                OccurrenceMode.EVERGREEN, null, null, null, 0, null, buildKeywords(englishName, marathiName));
    }

    private static List<String> buildKeywords(String englishName, String marathiName) {
        List<String> keywords = new ArrayList<>();
        keywords.add(englishName);
        keywords.add(marathiName);
        switch (englishName) {
            case "Women's Day" -> keywords.add("महिला दिन");
            case "Mother's Day" -> keywords.add("मातृ दिन");
            case "Father's Day" -> keywords.add("पितृ दिन");
            case "Wedding Season" -> {
                keywords.add("लग्न");
                keywords.add("लग्नाचा सीझन");
                keywords.add("bridal");
                keywords.add("wedding");
            }
            case "Store Anniversary" -> {
                keywords.add("दुकान वर्धापन दिन");
                keywords.add("anniversary");
                keywords.add("shop anniversary");
            }
            case "Makar Sankranti" -> keywords.add("संक्रांत");
            case "Akshaya Tritiya" -> keywords.add("अक्षय तृतीया");
            case "Gudi Padwa" -> keywords.add("गुढी");
            case "Diwali" -> {
                keywords.add("दिवाळी");
                keywords.add("deepavali");
            }
            case "Raksha Bandhan" -> keywords.add("रक्षाबंधन");
            case "Karwa Chauth" -> keywords.add("करवा चौथ");
            default -> {
            }
        }
        return keywords;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", "");
    }

    private enum OccurrenceMode {
        FIXED_DATE,
        APPROXIMATE_DATE,
        NTH_WEEKDAY,
        SEASONAL,
        EVERGREEN
    }

    record Occasion(
            String key,
            String englishName,
            String marathiName,
            String rationale,
            String offerTitle,
            String marathiHashtag,
            String englishHashtag,
            String visualHints,
            MarketingCampaignType campaignType,
            MarketingTone tone,
            MarketingDiscountType discountType,
            BigDecimal discountValue,
            OccurrenceMode mode,
            Month month,
            Integer dayOfMonth,
            DayOfWeek dayOfWeek,
            int ordinal,
            Set<Month> activeMonths,
            List<String> keywords
    ) {
        boolean matches(String haystack) {
            return keywords.stream()
                    .map(MarketingOccasionLibrary::normalize)
                    .filter(keyword -> !keyword.isBlank())
                    .anyMatch(haystack::contains);
        }

        LocalDate nextOccurrence(LocalDate today) {
            return switch (mode) {
                case FIXED_DATE, APPROXIMATE_DATE -> nextFixedDate(today);
                case NTH_WEEKDAY -> nextNthWeekday(today);
                case SEASONAL -> nextSeasonalAnchor(today);
                case EVERGREEN -> today;
            };
        }

        boolean isActive(LocalDate today) {
            return mode == OccurrenceMode.SEASONAL
                    && activeMonths != null
                    && activeMonths.contains(today.getMonth());
        }

        String marathiVisitCta() {
            return "%s निमित्त आजच दुकानात किंवा वेबसाईटवर भेट द्या".formatted(marathiName);
        }

        MarketingCampaignSuggestionResponse toSuggestion(String kind,
                                                         LocalDate highlightDate,
                                                         LocalDate startDate,
                                                         LocalDate today,
                                                         String landingUrl) {
            LocalDate resolvedStart = startDate == null ? today : startDate;
            LocalDate resolvedHighlight = highlightDate == null ? resolvedStart : highlightDate;
            LocalDate resolvedEnd = switch (kind) {
                case "UPCOMING" -> resolvedHighlight.plusDays(4);
                case "SEASONAL" -> resolvedStart.plusDays(14);
                case "TEMPLATE" -> resolvedHighlight == null ? resolvedStart.plusDays(10) : resolvedHighlight.plusDays(4);
                default -> resolvedStart.plusDays(20);
            };
            String windowLabel = switch (kind) {
                case "UPCOMING" -> "%s within next %d days".formatted(DATE_LABEL.format(resolvedHighlight), Math.max(0, ChronoUnit.DAYS.between(today, resolvedHighlight)));
                case "SEASONAL" -> isActive(today) ? "Active now" : "Season starting soon";
                case "TEMPLATE" -> resolvedHighlight == null || resolvedHighlight.equals(today)
                        ? "Ready template"
                        : "Ready for %s".formatted(DATE_LABEL.format(resolvedHighlight));
                default -> "Ready to use any time";
            };
            String description = "%s Use it as a greeting-only message or attach a coupon/offer before generation.".formatted(rationale);
            String imagePrompt = "Create a premium KPS Krishnai social media campaign image for %s (%s). Visual direction: %s. Use a jewellery and beauty retail mood, elegant Indian festive styling, warm lighting, clear product focus, no text overlays, no logos, no watermarks."
                    .formatted(englishName, marathiName, visualHints);
            return MarketingCampaignSuggestionResponse.builder()
                    .key(key)
                    .kind(kind)
                    .occasionName(marathiName)
                    .campaignName(marathiName)
                    .offerTitle(offerTitle)
                    .rationale(rationale)
                    .description(description)
                    .imagePrompt(imagePrompt)
                    .templateType(mode == OccurrenceMode.EVERGREEN ? "EVERGREEN" : campaignType.name())
                    .windowLabel(windowLabel)
                    .daysUntil((int) ChronoUnit.DAYS.between(today, resolvedHighlight))
                    .highlightDate(resolvedHighlight)
                    .startDate(resolvedStart)
                    .endDate(resolvedEnd)
                    .campaignType(campaignType)
                    .discountType(discountType)
                    .discountValue(discountValue)
                    .language(MarketingLanguage.MARATHI)
                    .tone(tone)
                    .targetPlatforms(DEFAULT_PLATFORMS)
                    .landingUrl(landingUrl)
                    .build();
        }

        private LocalDate nextFixedDate(LocalDate today) {
            if (month == null || dayOfMonth == null) {
                return null;
            }
            LocalDate candidate = LocalDate.of(today.getYear(), month, dayOfMonth);
            return candidate.isBefore(today) ? candidate.plusYears(1) : candidate;
        }

        private LocalDate nextNthWeekday(LocalDate today) {
            if (month == null || dayOfWeek == null || ordinal <= 0) {
                return null;
            }
            LocalDate candidate = LocalDate.of(today.getYear(), month, 1)
                    .with(TemporalAdjusters.dayOfWeekInMonth(ordinal, dayOfWeek));
            return candidate.isBefore(today) ? LocalDate.of(today.getYear() + 1, month, 1)
                    .with(TemporalAdjusters.dayOfWeekInMonth(ordinal, dayOfWeek)) : candidate;
        }

        private LocalDate nextSeasonalAnchor(LocalDate today) {
            if (activeMonths == null || activeMonths.isEmpty()) {
                return today;
            }
            if (isActive(today)) {
                return today;
            }
            for (int offset = 0; offset < 14; offset++) {
                LocalDate candidate = today.plusMonths(offset).withDayOfMonth(1);
                if (activeMonths.contains(candidate.getMonth())) {
                    return candidate;
                }
            }
            return today;
        }
    }
}
