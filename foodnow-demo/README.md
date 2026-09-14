# FoodNow — four-persona marketplace demo

A separate Java application derived from `jilani-syed/java-stripe-payment-element`. The starter repository is unchanged. Spring Boot serves the branded storefront, persona workspaces and JSON API on http://localhost:4242.

## Run locally

Requires JDK 25+ and Maven. From this directory:

```sh
cp .env.example .env
./scripts/run.sh
```

Default **rehearsal** mode needs no credentials. Accounts, menus, approvals, orders, delivery transitions and balanced journals actually persist; payments, wallet outcomes, Connect onboarding and transfers are explicitly simulated. Stripe mode uses real Stripe test APIs. No live payments are supported.

Alternatively build with `mvn package` and run `java -jar target/foodnow-demo-1.0.0.jar`. The jar does not automatically load `.env`; export its variables first. On this workstation the existing dependency cache can be used with `FOODNOW_MAVEN_REPO=/private/tmp/foodnow-m2 ./scripts/run.sh`.

## Workspaces and sample accounts

Open **Workspaces** in the header. Every account below uses **FoodNowDemo!2026**. Login forms have a sample-account picker that fills the form; authentication still happens on the server.

| Experience | URL | Email |
|---|---|---|
| Diner or guest | `/#discover` | `diner@foodnow.demo`, or continue without signing in |
| FoodNow operations / customer success | `/#ops` | `ops@foodnow.demo` |
| Basil & Brick owner | `/#owner` | `basil@foodnow.demo` |
| Other restaurant owners | `/#owner` | `bun@foodnow.demo`, `green@foodnow.demo`, `spice@foodnow.demo`, `maki@foodnow.demo` |
| Courier Sam | `/#courier` | `courier@foodnow.demo` |
| Courier Jamie | `/#courier` | `jamie@foodnow.demo` |

Each persona has its own authenticated slot within the browser session, so diner, restaurant, courier and operations tabs stay signed in concurrently. To show two different restaurant owners or couriers simultaneously, use separate browser profiles/private windows. Guests only see orders from their own session; signing into a diner account in that session claims its guest orders.

Restaurant and courier registration creates a pending application. Owners can add their menu while waiting. Operations approves or declines applications. FoodNow approval and Stripe readiness are separate.

## End-to-end journey

1. Diner chooses one of five cuisines, adds dishes from one restaurant, and checks out as a guest or account holder.
2. Payment confirmation releases the order. Decline and processing paths do not release it. In rehearsal select a clearly simulated outcome and method.
3. Owner accepts the order, starts preparation and marks it ready.
4. Operations assigns an approved courier, or a courier claims an unassigned ready pickup.
5. Assigned courier confirms pickup and delivery. The diner sees updates through polling.
6. Operations opens the delivered order and settles the restaurant and courier earnings. Each partner sees its own history and payable entries; operations sees the full balanced journal.

Sample economics: $16 food + $3.99 delivery + $1.99 service = $21.98. Restaurant receives $12.80 (20% food commission); courier earns $3.99; FoodNow retains $5.19 before processing and operating costs. These are illustrative business assumptions, not Stripe prices. A connected-account transfer is not a bank payout.

## Stripe test configuration

Set in the untracked `.env`:

```dotenv
FOODNOW_MODE=stripe
STRIPE_PUBLISHABLE_KEY=pk_test_your_key
STRIPE_SECRET_KEY=sk_test_your_key
STRIPE_WEBHOOK_SECRET=whsec_from_stripe_listen
```

Run `stripe login`, then `stripe listen --forward-to localhost:4242/webhook`; use that listener's signing secret before starting the app. Never paste secret keys into source, screenshots or the conversation.

1. Enable Connect in a US Stripe test platform account. This demo assumes US partners, USD and separate charges/transfers; international availability requires country-specific design.
2. Sign in to each restaurant you intend to use, open **Onboarding**, and launch hosted Stripe test onboarding. Each partner gets its own Express connected account. Finish with Stripe's test data, return, and click **Refresh Stripe status** until transfers are active.
3. Repeat for at least one courier. FoodNow approval remains a separate prerequisite.
4. Enable desired eligible payment methods in Stripe. Payment Element renders what the account, currency, browser and device support. Apple Pay/Google Pay availability is not guaranteed on plain localhost; use a supported HTTPS/domain configuration for wallet demonstrations.
5. Use Stripe test cards, such as `4242 4242 4242 4242` for success or `4000 0000 0000 0002` for a decline. Use future expiry and a test CVC. Never use real card details.
6. Keep the webhook listener running. The signed webhook confirms the payment and posts its journal; a browser redirect alone cannot mark it paid.
7. Deliver the order, settle it in operations, and inspect payment/transfer IDs in Stripe test Dashboard. Reconcile the actual processing fee separately when its balance transaction is available.

The configured webhook event API version must match the Stripe Java SDK event version; mismatches are rejected. Existing two shared-account variables from v1 are no longer used by marketplace routes.

## Repeat, reset and verify

State is in `data/marketplace-v2-rehearsal.json` or `data/marketplace-v2-stripe.json`. Old v1 order files are retained separately. Restarting preserves users, menus and orders; sessions require signing in again.

For a fresh rehearsal without deleting history, stop the server and change `MARKETPLACE_STORE_PATH` to a new filename, for example `data/rehearsal-run-2.json`, then restart. Use a new browser session for a fresh guest bag. Do not reset Stripe-mode associations to repeat a presentation: create new orders and retain the reconciliation history. Keep only one server process per data file.

```sh
mvn test package
python3 scripts/verify-marketplace.py
node --check src/main/resources/static/app.js
node --check src/main/resources/static/future.js
```

The Python check starts a separate rehearsal server on port 4253, uses temporary data, checks all four personas and restarts it to verify persistence. It never contacts Stripe.

See [solution and roadmap](docs/SOLUTION.md), [architecture](docs/ARCHITECTURE.md), [demo script](docs/DEMO-SCRIPT.md), and [verification and limitations](docs/VERIFICATION.md).

## Payments, Radar and Connect showcase

Operations → **Stripe insights** identifies the configured Stripe account, explains the three modules, retrieves actual payment/risk outcomes, and lists partner connected-account readiness. Use **Check account connection** and **Refresh Stripe evidence** explicitly. Failed attempts remain visible alongside successful retries. Risk data is never simulated or invented; missing risk fields are labeled unavailable.

The real checkout now combines Payment Element and Express Checkout for eligible wallet buttons. In rehearsal, the card layout is a clearly labeled non-interactive preview. The courier's assigned-order card shows both the restaurant pickup address and customer destination.

A private `.env` template is ready locally. Fill its test keys and webhook secret, change `FOODNOW_MODE=stripe`, and restart using `scripts/run.sh`; both keys must belong to the same Stripe test account/sandbox. Keep the webhook listener running. The UI can verify the account behind the secret key, but only a real test checkout verifies the full publishable-key/webhook path.

For the exact two-terminal restart procedure, account logins, fresh-clone setup and preservation of existing connected-account mappings, see [Run FoodNow again](docs/RUN-AGAIN.md).

## UK and France checkout locales

Use the header's **Checkout locale** selector: `en-US / USD`, `en-GB / GBP`, or `fr-FR / EUR`. This changes actual PaymentIntent currency, currency/date formatting, and French payment form/core checkout labels. The wider application remains English. Existing Chicago restaurants and US partner accounts are retained; this is presentment localization, not a launch of UK/French legal entities or delivery markets.

Menu prices are sample nominal prices in the selected currency (a 16.00 dish costs USD 16.00, GBP 16.00 or EUR 16.00), not FX conversions. Delivery is 3.99 and service 1.99 in that currency. Menu edits affect that common nominal price; independently maintained regional price books, VAT/tax, regional addresses and full-site translation are future extensions.

Orders retain their currency permanently. Switching locale starts a new checkout attempt and filters account/workspace orders, pickup queues and journal entries to the selected currency. Direct order links still display the order's original currency. Legacy orders/journals remain USD. Operations → **Stripe insights** adds an all-currency table with separate paid volume, average paid order value, platform revenue, delivered counts and pending-payment counts; there is no summed cross-currency money metric. Paid share means paid orders / created orders, not issuer authorization rate.

GBP/EUR test payments depend on Stripe account/method eligibility. For transfers, the app checks that Stripe's settlement balance transaction uses the order currency. If Stripe converted it to USD, allocation stops with an FX reconciliation message rather than posting incorrect partner amounts. Enable appropriate multi-currency settlement or implement explicit FX accounting before completing that funds flow. No Stripe account settings are changed automatically.

References: [Stripe currencies](https://docs.stripe.com/currencies), [Connect currency handling](https://docs.stripe.com/connect/currencies).
