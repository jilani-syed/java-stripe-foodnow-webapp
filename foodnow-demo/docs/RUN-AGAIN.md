# Run FoodNow again

Requires JDK 25+, Maven, Python 3 and Stripe CLI. The existing `.env` and `data/` are private local files; Git does not contain them.

## Existing workstation: Stripe test demo

Stop any previous FoodNow app and Stripe listener before starting replacements. Leave both terminal windows below open during your demo.

**Terminal 1 — webhook listener**

```sh
cd /Users/jilani/Documents/ChatGPT/Stripe/foodnow-demo
python3 scripts/run-webhooks.py
```

Wait for **Listener ready**. The helper verifies your configured test secret key, starts the Stripe listener for payment events, saves its signing secret to `.env` without displaying it, and selects Stripe mode. It uses the key in `.env`, so a different account selected in Stripe CLI does not redirect this demo's events.

**Terminal 2 — application**

```sh
cd /Users/jilani/Documents/ChatGPT/Stripe/foodnow-demo
./scripts/run.sh
```

On this workstation, reuse the previously populated Maven cache if necessary:

```sh
FOODNOW_MAVEN_REPO=/private/tmp/foodnow-m2 ./scripts/run.sh
```

Open http://localhost:4242. If you restart the webhook listener and its secret changes, restart the app too. Stop each process with Ctrl+C when finished.

## Demo accounts and sequence

All seeded passwords: **FoodNowDemo!2026**.

| Persona | Email | Page |
|---|---|---|
| Diner | `diner@foodnow.demo` or guest | `/#restaurant/basil` |
| Basil & Brick owner | `basil@foodnow.demo` | `/#owner/orders` |
| Sam, courier | `courier@foodnow.demo` | `/#courier/deliveries` |
| Operations | `ops@foodnow.demo` | `/#ops/stripe` |

Open one tab per persona. Order a Basil dish, enter delivery details, then load the real Stripe payment form. Use test card `4242 4242 4242 4242`, a future expiry and any three-digit CVC. The owner accepts → prepares → marks ready; Sam claims → picks up → confirms delivery; operations settles earnings and refreshes Stripe evidence.

Existing local data retains the restaurant and courier connected-account mappings. The courier's Stripe onboarding must be complete and transfers active before settlement. Check **Courier → Onboarding → Refresh Stripe status**. Other seeded restaurants also need their own Stripe onboarding before accepting test payments.

## Fresh clone / different computer

1. Enter the `foodnow-demo` directory in your clone.
2. Copy `.env.example` to `.env`; configure matching Stripe test publishable and secret keys. Never overwrite an existing working `.env` unnecessarily.
3. Start `python3 scripts/run-webhooks.py`, then `./scripts/run.sh` in another terminal.
4. A fresh store seeds accounts and menus but has no past orders or partner mappings. Complete partner onboarding from each workspace. If API account creation is blocked, finish Stripe platform Connect setup; existing manually linked accounts are stored in the private data file, not Git.
5. To preserve this exact prepared demo on another computer, transfer `.env` and `data/marketplace-v2-stripe.json` privately while the app is stopped. These files contain credentials or private demo records and must remain outside Git. Start a fresh listener to obtain its current signing secret.

## Key-free rehearsal

Set `FOODNOW_MODE=rehearsal` in `.env`, then run `./scripts/run.sh`. Do not start the webhook helper for rehearsal because it selects Stripe mode. Payments and onboarding are labeled simulated; the account, menu and delivery workflows still persist in a separate rehearsal file.

## Verification

```sh
mvn test package
python3 scripts/verify-marketplace.py
```

The HTTP checks use temporary rehearsal data and port 4253. See `VERIFICATION.md` for the completed test results and limitations.
