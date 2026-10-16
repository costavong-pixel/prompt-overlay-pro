# Alpha20 — Plan card layout QA

This Android-only preview follows Alpha19 and fixes a layout bug visible on
the user's Samsung screen: the Pro Monthly billing cadence previously competed
with the title and price in the same horizontal row and overflowed.

## Changes

- The top row contains **only the plan name and its price**.
- The billing cadence now occupies a **full-width, wrapping subtitle**:
  - Monthly: "Billed monthly after purchase · renews until canceled".
  - Lifetime: "One-time payment · no renewal".
- Full-width cadence is localized for English, Spanish, French, Arabic;
  plan cards expose the full information to screen readers.
- Feedback card's long headline is no longer repeated as its action:
  **"Send detailed feedback"** opens the existing private media/link form.
- The 90-day trial, owner-chosen 500 feedback awards, prices, and access
  policy remain unchanged.

## Isolation

- With no stable private QA signer, this test APK installs under
  `com.costavong.promptoverlay.proalpha20`, **alongside** Alpha18's
  Google OAuth and trial app and Alpha19's purchase-preview app. It does not
  overwrite or delete their local scripts/projects.
- Google Sign-In is deliberately unavailable in the isolated Alpha20 preview;
  **use the already activated Alpha18** for live trial and sign-out/re-login
  checks.
- A future stable QA-signed build may use
  `com.costavong.promptoverlay.protest`, only after the same signing key
  and matching Android OAuth registration are securely configured.
- No purchases, billing, account migrations, host changes, VPS restarts or
  Play Console configuration changes are performed.
- The live trial server remains unchanged.

## Acceptance test

1. Install the Alpha20 APK without uninstalling Alpha18 or Alpha19.
2. Go to Plans, scroll to Basic/Pro Monthly/Pro Lifetime.
3. Confirm **Pro Monthly** shows `$4.99` on the top row and its billing
   terms **below**, not overlapping the name or price.
4. At large system font sizes and in Spanish/French/Arabic, confirm all
   pricing text wraps inside cards.
5. Confirm tapping a plan still opens its feature description and **does not
   request a payment**.
6. Confirm private feedback card displays `Send detailed feedback` and
   media/link support still works.
7. Do not attempt new OAuth setup: the existing Alpha18 QA trial continues.
