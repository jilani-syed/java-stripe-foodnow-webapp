# FoodNow solution brief and capability matrix

FoodNow operates in ten countries and wants five more markets, better acceptance and fraud management, simpler restaurant/courier onboarding, and new subscription revenue. The panel evaluates solution design, technical expertise, business value, stakeholder engagement and communication. The meeting is 45 minutes with 25 minutes of presentation and approximately 15 minutes of Q&A. The demo should support that narrative in roughly seven minutes.

NOW demonstrates one US/USD market with five cuisines and four authenticated experiences. This makes operational handoffs and money movement concrete without claiming that a US implementation solves every cross-border requirement.

| Phase | Capability / Stripe product | Business value | Implementation status |
|---|---|---|---|
| NOW | Diner account or guest, five restaurant menus, one-restaurant bag, tracking | Familiar low-friction discovery and clear fulfillment status | Working local workflows with persistent orders |
| NOW | Payment Element, PaymentIntents, eligible wallets and Link | Consolidated checkout and recovery paths | Stripe test integration implemented; requires keys/account validation. Explicit success, decline and processing rehearsal |
| NOW | Restaurant and courier accounts, applications, operations approval | Less fragmented onboarding and clear readiness | Working account and approval workflows; Connect readiness is separate |
| NOW | Connect hosted onboarding, per-partner Express accounts | Stripe collects required partner information | Test integration implemented; rehearsal clearly simulated |
| NOW | Owner menus and queues; courier assignments, pickup and delivery | Shared source of truth across fulfillment handoffs | Working persistent, ownership-restricted workflows |
| NOW | Connect separate charges/transfers; balanced journal and fee reconciliation | Traceable partner earnings, commissions, and payment operations | Test integration implemented; explicit simulated transfers in rehearsal. Actual fee requires Stripe balance transaction |
| NOW discussion | Radar and payment performance | Balance fraud losses and acceptance using measured outcomes | Stripe payment capability discussion, no claimed custom rules, fraud scoring model or measured uplift in app |
| NEXT | FoodNow+ with Billing | Recurring revenue and retention | Interactive preview; no actual subscription creation or billing |
| NEXT | Market-aware payment methods and rollout | Expand local relevance and acceptance | Country planning preview; no implemented cross-border marketplace |
| LATER | Eligible Instant Payouts and partner financial experiences; optional restaurant SaaS subscriptions | Partner liquidity and additional revenue streams | Concepts and technical extension path only; eligibility and economics require validation |

## Stakeholder story

**Head of Payments:** Follow the payment ID, commission allocation, payable balance and transfer IDs. Compare authorization, fraud/disputes, processing cost and onboarding completion against a measured baseline. A transfer to a connected balance is distinct from a bank payout.

**Head of Engineering:** Reuse the Java starter, server-owned prices and customer/PaymentIntent services. Explain authenticated ownership, webhook authority, durable identity and retry boundaries. Production migration adds a relational store, queues, managed identity and operational reconciliation around the existing domain boundaries.

**Product / UX:** Show guest checkout, account history, cuisine discovery, visible fees, payment recovery and fulfillment updates. Owners and couriers get focused workspaces; operations coordinates exceptions and partner reviews.

## Delivery sequence and business assumptions

Implemented sequence: shared identity/data model → marketplace APIs and journals → four interfaces → isolated end-to-end checks → signed-webhook tests → setup and presentation guide. A real rollout sequence would validate country/entity/Connect feasibility first, build a single-market pilot, test compliance and operational exceptions, measure pilot outcomes, then expand in market cohorts. Do not promise calendar dates without team, migration and regional requirements.

Illustrative economics use 20% restaurant commission, $3.99 courier delivery earnings and $1.99 service fee. No taxes, tips or Stripe pricing estimates are calculated. The value calculator is scenario planning, not evidence of FoodNow revenue uplift. The original presentation review remains in `PRESENTATION-REVIEW.md`.

## Sources checked

- [Payment Element](https://docs.stripe.com/payments/payment-element): eligible payment methods and checkout UI integration.
- [Payment method integration options](https://docs.stripe.com/payments/payment-methods/integration-options): method support differs by integration and market.
- [Connect hosted onboarding](https://docs.stripe.com/connect/hosted-onboarding): account links and requirements; returning alone does not prove completion.
- [Separate charges and transfers](https://docs.stripe.com/connect/separate-charges-and-transfers): platform charge and transfers to multiple connected accounts; geography and liability considerations remain design decisions.

These sources support integration choices, not a claim that FoodNow or the reference businesses use this exact architecture. UX research and photograph credits are in `BRAND-AND-RESEARCH.md`.
