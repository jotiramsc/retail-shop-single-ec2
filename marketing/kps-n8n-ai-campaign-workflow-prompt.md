# n8n AI Campaign Workflow Generator Prompt

Use this prompt in n8n Chat / AI workflow builder / template generator.

```text
Create a fully automated n8n workflow for AI marketing campaign generation, WhatsApp approval, and approved publishing for:

Brand name:
Krishnai Pearl Shopee / KPSKRISHNAI

Website:
https://kpskrishnai.com/

Contact / WhatsApp:
+91 9175834000

Business type:
Pearl jewellery and gold-plated jewellery retail store with online web portal.

Products:
Pearl jewellery, gold-plated jewellery, bangles, rings, earrings, necklaces, wedding jewellery, festive jewellery, and gift sets.

Recommended configuration:
- AI text provider: OpenAI
- AI image provider: OpenAI Images
- Approval channel: WhatsApp only
- Storage: Google Sheets
- Publishing channels after approval: WhatsApp, Facebook, Instagram

Critical safety rule:
Never publish or send campaign content automatically before approval. The workflow must generate content, save draft, send preview to WhatsApp for approval, wait for APPROVE / CHANGES / REJECT, and publish only after APPROVE.

Create the workflow with these sections and node names:

1. Manual Campaign Trigger
Create a Manual Trigger for testing.

2. Campaign Request Webhook
Create a POST webhook named "New Campaign Request".
Webhook path:
kps-new-campaign

Accept these input fields:
- campaign_goal
- product_focus
- offer_text
- language
- target_audience
- publish_channels
- approval_whatsapp_number

Default values if missing:
- campaign_goal: Generate website visits, WhatsApp enquiries, and jewellery orders
- product_focus: Pearl jewellery, gold-plated jewellery, bangles, rings, earrings, necklaces, wedding jewellery, festive jewellery, gift sets
- offer_text: Explore elegant jewellery collections online. Call or WhatsApp +91 9175834000
- language: English with simple Indian festive tone
- target_audience: Women, families, brides, gifting customers, festive shoppers, and jewellery buyers
- publish_channels: whatsapp, facebook, instagram
- approval_whatsapp_number: 919175834000

3. Campaign Input Normalizer
Create a Code node that creates:
- campaign_id
- created_at
- brand_name
- website
- contact_number
- email placeholder
- campaign_goal
- product_focus
- offer_text
- language
- target_audience
- publish_channels
- approval_whatsapp_number

Campaign ID format:
KPS-YYYYMMDD-HHMMSS

4. OpenAI Campaign Strategy
Use OpenAI Chat model to generate the full campaign draft.
Recommended model:
gpt-4.1-mini or latest available cost-effective OpenAI chat model.

The OpenAI node must return strict JSON only, with this structure:
{
  "campaign_id": "",
  "campaign_status": "WAITING_FOR_APPROVAL",
  "campaign_title": "",
  "campaign_objective": "",
  "target_audience": "",
  "main_offer": "",
  "creative_direction": {
    "theme": "",
    "colors": "",
    "visual_style": "",
    "mood": ""
  },
  "image_content": {
    "square_post_prompt": "",
    "story_prompt": "",
    "whatsapp_image_prompt": "",
    "text_overlay": ""
  },
  "whatsapp_message": {
    "message": "",
    "cta": ""
  },
  "facebook_post": {
    "caption": "",
    "hashtags": []
  },
  "instagram_post": {
    "caption": "",
    "hashtags": []
  },
  "approval_message": {
    "title": "Krishnai Pearl Shopee campaign ready for approval",
    "message": "Reply APPROVE {{campaign_id}} to publish, CHANGES {{campaign_id}}: your changes to regenerate, or REJECT {{campaign_id}} to cancel."
  }
}

OpenAI system prompt:
You are an expert Indian jewellery marketing campaign creator. Create elegant, premium, trustworthy, festive content for Krishnai Pearl Shopee / KPSKRISHNAI. Promote pearl jewellery, gold-plated jewellery, bangles, rings, earrings, necklaces, wedding jewellery, festive jewellery, and gift sets. Use the website https://kpskrishnai.com/ and CTA Call or WhatsApp +91 9175834000. Do not mention mobile app. Do not publish. Return strict JSON only.

5. OpenAI Image Generation
Use OpenAI Images API / OpenAI image node.
Recommended model:
gpt-image-1 or latest available OpenAI image model.

Generate three images:
- Square post: 1024x1024 or 1080x1080 equivalent
- Story creative: 1024x1536 or 1080x1920 equivalent
- WhatsApp promotional creative: 1024x1536 or 1080x1350 equivalent

Use the prompts from OpenAI Campaign Strategy:
- image_content.square_post_prompt
- image_content.story_prompt
- image_content.whatsapp_image_prompt

Image style requirements:
Premium Indian jewellery campaign, pearl jewellery and gold-plated jewellery, elegant bangles, earrings, necklace, ring, festive gifting mood, ivory/champagne/gold background, clean premium product photography, space for text overlay, no clutter, no fake brand logo unless provided.

Save image outputs as media URLs or binary files that can be sent to WhatsApp and posted to Meta channels.

6. Google Sheets Save Draft
Use Google Sheets as campaign storage.

Sheet columns:
- campaign_id
- created_at
- campaign_status
- campaign_title
- campaign_goal
- product_focus
- offer_text
- square_image_url
- story_image_url
- whatsapp_image_url
- whatsapp_message
- facebook_caption
- instagram_caption
- hashtags
- approval_whatsapp_number
- approval_response
- published_channels
- published_at
- error_message

Insert a new row with campaign_status:
WAITING_FOR_APPROVAL

7. Send WhatsApp Approval Preview
Use WhatsApp Business Cloud API via HTTP Request node.

Send approval message to:
919175834000

Message should include:
- Campaign ID
- Campaign title
- Main offer
- Image preview links
- WhatsApp message draft
- Facebook caption
- Instagram caption
- Hashtags
- Reply instructions:
  APPROVE {{campaign_id}}
  CHANGES {{campaign_id}}: your requested changes
  REJECT {{campaign_id}}

WhatsApp API placeholders:
- WHATSAPP_CLOUD_API_TOKEN
- WHATSAPP_PHONE_NUMBER_ID

8. Approval Webhook
Create another POST webhook named "WhatsApp Approval Webhook".
Webhook path:
kps-campaign-approval

Normalize incoming WhatsApp message:
- approval_text
- campaign_id
- sender_phone

Decision logic:
If approval_text starts with APPROVE:
- Continue to publishing branch.

If approval_text starts with CHANGES:
- Update Google Sheet campaign_status = CHANGES_REQUESTED
- Send requested changes back to OpenAI Campaign Strategy
- Regenerate content
- Generate fresh media
- Save new draft
- Send new WhatsApp approval preview

If approval_text starts with REJECT:
- Update Google Sheet campaign_status = REJECTED
- Send WhatsApp confirmation:
  Campaign {{campaign_id}} has been rejected and will not be published.
- Stop workflow.

9. Publish Only After Approval
After approval, load campaign row from Google Sheets by campaign_id.

WhatsApp publishing:
- Send approved WhatsApp message and approved media to selected WhatsApp contacts/groups.
- Use WhatsApp Business Cloud API.

Facebook publishing:
- Use Meta Graph API.
- Post approved image and caption to Facebook Page.

Instagram publishing:
- Use Meta Graph API.
- Create Instagram media container.
- Publish media container to Instagram Business Account.

Meta placeholders:
- META_PAGE_ACCESS_TOKEN
- FACEBOOK_PAGE_ID
- INSTAGRAM_BUSINESS_ACCOUNT_ID

10. Mark Published
Update Google Sheets:
- campaign_status = PUBLISHED
- published_channels
- published_at
- post URLs if returned by Meta APIs

11. Error Handling
Add error branches for:
- OpenAI text generation failed
- OpenAI image generation failed
- WhatsApp approval send failed
- Meta publishing failed
- Google Sheets save/update failed

On error:
- campaign_status = ERROR
- error_message = detailed error
- Send WhatsApp alert to 919175834000
- Do not publish partially if approval or media generation failed
- Retry media generation and publishing maximum 2 times

12. Sticky Notes
Add sticky notes explaining:
- Where to add OpenAI credential
- Where to add WhatsApp Cloud API token
- Where to add Facebook/Instagram IDs
- Where to connect Google Sheet
- Approval must happen before publishing

Required credentials to configure manually in n8n:
- OpenAI API credential
- WhatsApp Cloud API token
- WhatsApp Phone Number ID
- Meta Page Access Token
- Facebook Page ID
- Instagram Business Account ID
- Google Sheets OAuth credential
- Google Sheet ID

The final workflow should be importable, clearly named:
"KPS AI Campaign Generator - WhatsApp Approval - Meta Publishing"

Keep the workflow inactive by default after creation.
```
