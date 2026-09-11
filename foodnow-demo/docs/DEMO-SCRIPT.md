# Presentation-ready walkthrough

## Before the meeting

Open four tabs using **Workspaces**: diner, Basil & Brick owner, courier Sam and operations. Sign each persona in with the sample accounts in README; leave the diner signed out if demonstrating guest checkout. The browser retains a separate authenticated slot for each persona. Use a fresh rehearsal file for a clean run. Confirm the mode banner and never describe rehearsal transactions as Stripe transactions.

For a Stripe test demo, finish onboarding for Basil and Sam, refresh their readiness, verify the webhook listener, and rehearse one successful payment and both transfers before presenting. If credentials or account setup are incomplete, use the explicitly labeled rehearsal and explain the integration evidence boundary.

## Suggested 25-minute presentation

- 0–4: FoodNow goals and discovery assumptions. Ask which markets, acceptance baseline, fraud loss and partner onboarding bottlenecks matter most.
- 4–8: NOW design and stakeholder value, using the architecture diagram.
- 8–15: One order through four personas, as below.
- 15–19: Integration boundaries, webhook/retry behavior, phased rollout and operational responsibilities.
- 19–23: NEXT FoodNow+ and market expansion; LATER partner opportunities.
- 23–25: Summarize desired business outcomes and agree what to validate in a pilot. Leave the remaining meeting for Q&A.

## Seven-minute NOW demo

1. **Diner, 60 seconds.** “FoodNow brings local kitchens into one experience.” Browse five cuisines; choose Basil & Brick and a $16 pizza. Show $21.98 total with explicit delivery/service fees. Continue as a guest or show account login/history. Place the order. In rehearsal choose a simulated payment method and success. In Stripe mode confirm through Payment Element and wait for the webhook-confirmed tracking state.
2. **Owner, 60 seconds.** Open the new-order queue. “Only this restaurant sees its orders.” Accept → start preparation → ready for pickup. Briefly show menu editing/availability and payment history. Avoid changing the demonstrated dish mid-checkout.
3. **Courier, 60 seconds.** Claim the ready pickup, confirm pickup, then delivery. “The courier sees the task and their earnings, and FoodNow avoids double assignment.” The final confirmation requires an explicit delivery acknowledgment.
4. **Diner, 30 seconds.** Return to tracking and show delivered status and history. Updates poll every five seconds; Refresh is available.
5. **Operations, 90 seconds.** Show open orders grouped by restaurant, application counts and partner details. Open the delivered order: restaurant $12.80, courier $3.99, FoodNow $5.19 before processing/operating costs. Settle earnings. Show the balanced debits/credits and both transfer references. Repeating settlement retains the same references.
6. **Onboarding, 60 seconds.** Register a new restaurant or courier in a separate browser profile, or prepare a pending registration beforehand. Show the operations review queue. Explain that FoodNow approval is independent of Stripe requirements. In the partner's workspace, launch hosted onboarding in Stripe mode or clearly simulated onboarding in rehearsal.
7. **Transition, 30 seconds.** “The NOW foundation connects customer payment, fulfillment and partner earnings. NEXT adds FoodNow+ subscriptions and market configuration; LATER evaluates partner liquidity and additional business models.”

## Stripe evidence and recovery

In Stripe mode, open **View payment in Stripe** from the operations order inspector. Match amount, customer and PaymentIntent metadata to the FoodNow order; inspect request/event logs and the two transfers. Connected balances and bank payouts are different evidence. Use **Reconcile Stripe fee** only when the actual balance transaction exists.

For a decline rehearsal, choose Decline, show that the restaurant cannot fulfill an unpaid order, retry payment successfully, then proceed. For an asynchronous payment, show Processing and the pending queue; only confirmed success releases it. Do not manually mark real Stripe payments paid in the browser.

## Likely panel questions

- **Why separate charges/transfers?** One diner charge funds a restaurant and courier; the courier may be selected later. Explain platform liability and verify regional support before rollout.
- **What improves acceptance/fraud?** Payment methods, checkout experience and Stripe payment/fraud capabilities are levers. Establish control metrics; do not promise unmeasured uplift.
- **What if a webhook repeats or arrives late?** Validate and persist the event with the state/journal; deduplicate and preserve confirmed success.
- **What if one transfer fails?** Persist the successful transfer, retry only the missing partner with the same key, then reconcile uncertain requests beyond the retry window.
- **What is production work?** Managed auth, relational transactions, durable event processing, observability, fraud operations, refunds/disputes, payout reconciliation, regional/legal design and load/accessibility testing.

## Payments, Radar and Connect evidence extension

Open operations → **Stripe insights**. Use **Check account connection** to retrieve the platform account ID from the configured secret key and open the corresponding Stripe test Dashboard. It cannot claim a connection in rehearsal mode.

At checkout, enter delivery details and click **Continue to payment**. Stripe mode then mounts the real Payment Element plus Express Checkout for eligible wallets. Rehearsal shows a non-interactive layout preview labeled as such; it never asks for card details.

Run a success card `4242 4242 4242 4242`, issuer decline `4000 0000 0000 0002`, and Radar always-blocked card `4100 0000 0000 0019` as separate test attempts. Use a future expiry and test CVC. In operations, click **Refresh Stripe evidence**. This retrieves Stripe Charge outcomes, risk level/score when available, failure information, and current dispute/refund flags for FoodNow orders. It shows prior failed attempts as well as successful ones. Counts cover fetched attempts, not the full account. These test scenarios illustrate behavior, not actual fraudulent customers or measured fraud reduction.

Match FoodNow order IDs and `application=FoodNow` metadata in Stripe. The account's Dashboard provides available Radar controls and broader reports; detailed features depend on account access/product availability. No Radar rules are changed by the demo.

Complete the fulfillment cycle: owner marks ready → courier claims that restaurant's pickup → pickup address and customer delivery address appear together → courier confirms pickup/delivery → operations transfers earnings. The Connect section lists restaurant/courier account IDs and transfer/payout readiness. Local journal entries, connected-account transfers and bank payouts are three different views of the flow.

Billing remains NEXT; Stripe Data Pipeline, a warehouse and durable event bus remain future architecture components, not implemented integrations.

Sources: [Stripe test scenarios](https://docs.stripe.com/testing#fraud-prevention), [Radar risk evaluation](https://docs.stripe.com/radar/transaction-risk-prevention), [Express Checkout](https://docs.stripe.com/elements/express-checkout-element).
