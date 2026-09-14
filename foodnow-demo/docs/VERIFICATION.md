# Verification — four-persona extension

Verified on September 11, 2026.

- **30 Java tests passed**, zero failures/errors/skips. Includes the 24 starter/v1 regression tests and six new marketplace tests covering signed webhook confirmation, deduplication, stale event handling, invalid/live/mismatched payloads, atomic rollback for unbalanced journals, snapshot isolation and salted password verification.
- **108 HTTP and business assertions passed** against the packaged app in an isolated rehearsal server. Covers five restaurants, guest/account isolation, role restrictions, CSRF, checkout retry consistency, decline recovery, immutable order prices after menu edits, menu availability, sequential restaurant state changes, exclusive courier assignment, explicit delivery confirmation, settlement retry consistency, balanced/scoped journals, partner registration/onboarding/approval, concurrent persona sessions and restart persistence.
- Both browser JavaScript files passed `node --check`.
- Maven built the runnable Spring Boot jar successfully.

The automated HTTP workflow runs on port 4253 with a fresh temporary store and blank Stripe credentials. It does not modify presentation data or contact Stripe. Tests can be repeated using the README commands.

## Verification boundaries

Stripe credentials were not provided. PaymentIntents, Payment Element, hosted onboarding, connected-account requirements, actual transfers and fee retrieval are implemented but have **not been exercised against a real Stripe test account in this extension**. Signed-webhook tests use locally generated cryptographic fixtures. No actual authorization improvement or fraud reduction is measured. JavaScript syntax and HTTP checks do not constitute browser visual, accessibility or wallet verification; the new persona UI has not been browser-tested in this turn.

## Known limitations

- US/USD, one restaurant per order, fixed sample commissions, sample ratings/ETAs and representative photos. No geolocation, dispatch optimization or real courier GPS.
- Test/rehearsal only. No live keys, real orders, real identity review or bank payout execution.
- Local demo authentication with seeded credentials. No password reset, email verification, MFA, managed identity or production abuse controls. Four role slots coexist per browser session; multiple accounts of the same role need separate profiles.
- Single process and JSON storage; migrate to database transactions, durable webhook processing and operational reconciliation for production.
- Refunds, cancellations, disputes, transfer reversals, tax, tips, earnings adjustments and payout reconciliation are future work.
- Connect requirements are refreshed explicitly in the partner workspace and before real settlement. `account.updated` push handling is not implemented.
- Wallets and other payment methods depend on account/country/currency/browser/domain eligibility. The rehearsal method selector is explicitly simulated.
- Restaurant/courier transfers debit payables; bank payout readiness is reported separately. FoodNow retained revenue excludes unreconciled processing and operating costs.
- NEXT FoodNow+ and market expansion are previews. LATER partner products are concepts subject to eligibility and regional validation.

For another clean presentation, use a fresh rehearsal store filename after stopping the server. Preserve old files and all Stripe-mode IDs for reconciliation.

## Payments/Radar/Connect extension verification

The updated Java suite passes **31 tests**, including operations-only risk evidence access, persisted Stripe outcome snapshots and confirmation that reading risk evidence cannot release an unpaid order or create a journal. JavaScript syntax checks pass for the Payment Element/Express Checkout integration and new Stripe insights renderer. Integration checks now also cover account-check authorization, explicit disconnected rehearsal status, courier restaurant pickup address, and rejection of rehearsal/partner risk refresh requests.

Actual account identity, Express Checkout availability, Radar test outcomes and Connect transfers still require user-configured test credentials. No live Stripe account validation or browser testing is claimed for this extension. Evidence counts are fetched FoodNow attempts only, with up to 100 charges per order; unavailable risk data stays unavailable. The UI does not change Radar settings.

Final packaged extension: **116 HTTP/business assertions passed**, including persistence across a server restart. Local application health confirmed after restart in rehearsal mode.

## UK / France locale extension — September 14, 2026

31 Java tests passed. The expanded isolated HTTP suite passed **152 assertions**, including GBP/EUR checkout currency and prices, duplicate requests, rejection of changed-currency retries, historical currency preservation, currency-scoped workspace orders and journals, insight totals, authorization, unsupported locale rejection and persistence after restart. Both changed JavaScript files passed syntax validation. These checks used rehearsal payments; no actual GBP/EUR Stripe payment, wallet or international transfer is claimed as verified. Browser visual QA and full French-site translation were not part of this change.

USD legacy records remain compatible. Real partner allocation now checks Stripe settlement currency and stops when explicit FX reconciliation would be required. See README for nominal demo pricing and supported locale scope.
