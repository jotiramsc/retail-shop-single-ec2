package com.retailshop.enums;

import java.util.List;

public enum WhatsAppTemplateKey {
    ORDER_CONFIRMATION("kp_order_confirmed_en", "en_US",
            List.of("Customer Name", "Order ID", "Amount", "Delivery Date")),
    ORDER_DISPATCHED("kp_order_dispatched_en", "en_US",
            List.of("Customer Name", "Order ID", "Tracking ID", "Tracking URL")),
    ORDER_DELIVERED("kp_order_delivered_en", "en_US",
            List.of("Customer Name", "Order ID")),
    ORDER_CANCELLED("kp_order_cancelled_en", "en_US",
            List.of("Customer Name", "Order ID")),
    ORDER_RETURNED("kp_order_returned_en", "en_US",
            List.of("Customer Name", "Order ID")),
    REFUND_INITIATED("kp_refund_initiated_en", "en_US",
            List.of("Customer Name", "Order ID", "Amount")),
    PAYMENT_FAILED("kp_payment_failed_en", "en_US",
            List.of("Customer Name", "Order ID", "Amount")),
    PAYMENT_SUCCESS("kp_payment_success_en", "en_US",
            List.of("Customer Name", "Order ID", "Amount")),
    LOGIN_OTP("kp_customer_login_otp", "en_US",
            List.of("OTP")),
    BOT_WELCOME("kp_bot_welcome_en", "en_US",
            List.of("Customer Name")),
    BOT_MENU("kp_bot_menu_en", "en_US",
            List.of("Customer Name")),
    SUPPORT_ESCALATION("kp_support_escalation_en", "en_US",
            List.of("Customer Name")),
    OUT_OF_OFFICE("kp_out_of_office_en", "en_US",
            List.of("Customer Name")),
    FEEDBACK_REQUEST("kp_feedback_request_en", "en_US",
            List.of("Customer Name", "Order ID"));

    private final String defaultTemplateName;
    private final String defaultLanguageCode;
    private final List<String> variables;

    WhatsAppTemplateKey(String defaultTemplateName, String defaultLanguageCode, List<String> variables) {
        this.defaultTemplateName = defaultTemplateName;
        this.defaultLanguageCode = defaultLanguageCode;
        this.variables = variables;
    }

    public String getDefaultTemplateName() {
        return defaultTemplateName;
    }

    public String getDefaultLanguageCode() {
        return defaultLanguageCode;
    }

    public List<String> getVariables() {
        return variables;
    }
}
