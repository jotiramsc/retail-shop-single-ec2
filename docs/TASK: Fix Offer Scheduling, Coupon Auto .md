TASK: Fix Offer Scheduling, Coupon Auto Generation, Day Selection, and Category Icon Logic

Read first:
- krishnai.md
- TASK_TRACKER.md
- CURRENT_TASK.md

Rules:
- Work only on this task.
- Do not break existing offer/product/category flows.
- Show locally first.
- Verify all scenarios locally.
- Then deploy to EC2.
- Update krishnai.md and TASK_TRACKER.md after completion.

==================================================
1. Default One Month Offer Period
==================================================

Current issue:
When Date Range or Specific Days schedule is selected, start date and end date are empty.

Required behavior:
When user selects:
- Date Range
- Specific Days
- Weekend Only

Auto-fill:
- Start date = today
- End date = today + 1 month

Example:
If today is 2026-05-19:
- Start date = 2026-05-19
- End date = 2026-06-19

Rules:
- Apply defaults only when fields are empty.
- Do not overwrite user-selected dates.
- On edit page preserve existing values.
- Use yyyy-MM-dd format.
- Backend should also safely apply fallback defaults if missing.

==================================================
2. Enable Specific Day Selection
==================================================

Current issue:
Mon/Tue/Wed/Thu/Fri/Sat/Sun buttons are visible but not selectable.

Required behavior:
When Specific Days schedule is selected:
- Enable weekday chips/buttons.
- Allow multi-select.
- Clicking selected day again should unselect it.
- Selected days must visually highlight.
- Include selected days in API payload.

Payload example:
{
  "scheduleType": "SPECIFIC_DAYS",
  "startDate": "2026-05-19",
  "endDate": "2026-06-19",
  "specificDays": ["MON","WED","SAT"]
}

Validation:
- At least one day required.
- Show validation message:
  "Please select at least one day for this offer."

==================================================
3. Weekend Only Logic
==================================================

When Weekend Only is selected:
- Auto-fill one month date range.
- Automatically use:
  ["SAT","SUN"]

Payload:
{
  "scheduleType": "WEEKEND_ONLY",
  "specificDays": ["SAT","SUN"]
}

No manual day selection required.

==================================================
4. Always Active Logic
==================================================

When Always Active is selected:
- No date validation.
- No day validation.
- Do not send invalid empty date strings.

Payload:
{
  "scheduleType": "ALWAYS_ACTIVE"
}

==================================================
5. Auto Generate Coupon Code From Offer Name
==================================================

Current issue:
Coupon code must be manually typed.

Required behavior:
Auto-generate coupon code from Offer Name.

Rules:
- Max 6 characters.
- Uppercase only.
- Remove spaces/special chars.
- Use A-Z and 0-9 only.
- Auto-fill while typing offer name.
- Allow manual editing.
- Once manually edited, stop auto regeneration.

Examples:
- Necklace Offer → NECKLA
- Diwali Gold Deal → DIWALI
- Buy One Get One → BUYONE
- 20% Pearl Discount → 20PEAR

Validation:
- Coupon code max 6 chars.
- Must be unique.

Duplicate handling:
- NECKLA exists → NECKL1
- NECKL1 exists → NECKL2

Backend:
- Apply same fallback generation logic if coupon code missing in API request.
- Ensure uniqueness before save.

==================================================
6. Category Icon Generation Logic
==================================================

Current issue:
Inventory/category page may generate multiple icons or inconsistent icons for same category.

Required behavior:
Only ONE category icon per category.

Rules:
- Each category should have:
  - One generated icon/image only.
  - One stored icon path.
- Re-generating icon should:
  - Replace old icon.
  - Not create duplicates.
- Prevent multiple icon records/files for same category.

UI:
- Add:
  [Generate Icon]
  [Regenerate Icon]

Behavior:
- Generate Icon:
  - Works only if no icon exists.
- Regenerate Icon:
  - Replaces existing icon.
  - Deletes/replaces previous generated image reference.

Storage:
- Store only one active icon URL/path in category table/entity.

Backend:
- Prevent duplicate icon entries.
- Cleanup old generated image if regenerated.

AI Prompt behavior:
Generate premium clean category icon suitable for:
- Jewelry shop
- WhatsApp bot menus
- Product category listing
- Mobile responsive UI

Style:
- Luxury
- Minimal
- Gold/black premium style
- Transparent background preferred
- Circular or rounded style
- Consistent branding with KRISHNAI Pearl Shopee

==================================================
7. UI Improvements
==================================================

Offer Schedule UI:
- Make active schedule tab visually stronger.
- Make selected weekday chips clearly highlighted.
- Disable weekday chips unless Specific Days selected.
- Add helper text:
  "Select the days when this offer should be active."

Category UI:
- Improve icon preview section.
- Show loading state while generating icon.
- Show regenerate confirmation dialog.

==================================================
8. API + Database Validation
==================================================

Validate:
- Coupon code uniqueness.
- Proper specificDays values.
- Valid scheduleType.
- No duplicate category icons.
- Dates are valid.

==================================================
9. Local Testing
==================================================

Test all below locally:

OFFERS
-------
1. Date Range auto-fill works.
2. Specific Days auto-fill works.
3. Day selection works.
4. Validation works if no day selected.
5. Weekend Only auto-selects SAT/SUN.
6. Always Active works without dates.
7. Coupon code auto-generation works.
8. Manual coupon edit stops auto-generation.
9. Duplicate coupon handling works.
10. Edit offer preserves saved data.

CATEGORY ICONS
--------------
1. Generate icon creates only one icon.
2. Regenerate replaces existing icon.
3. No duplicate DB records.
4. UI preview updates correctly.
5. Loading states work.
6. Old icon cleanup works.

==================================================
10. Deployment
==================================================

After successful local verification:
- Deploy to EC2.
- Verify admin panel live.
- Verify category icons.
- Verify offer creation/edit flow.
- Update TASK_TRACKER.md.
- Update krishnai.md.