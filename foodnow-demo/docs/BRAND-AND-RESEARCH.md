# FoodNow brand and UX direction

## Identity

FoodNow is an approachable neighborhood delivery marketplace. The distinctive choices are a compact lowercase wordmark, purple punctuation, an address-led header, appetizing photography and a bright storefront balanced by deep navy operational and membership surfaces.

| Token | Value | Use |
| --- | --- | --- |
| FoodNow purple | `#635BFF` | Primary actions, active navigation, identity |
| Deep navy | `#0A2540` | Headlines, demo context, membership banner |
| White | `#FFFFFF` | Main canvas and cards |
| Surface | `#F6F8FB` | Metrics and secondary panels |
| Text | `#152C43` | Body copy |
| Muted | `#637184` | Supporting information |
| Confirmation | `#087B55` | Confirmed outcomes, ratings |
| Display font | Manrope 700–800 | Wordmark and headings |
| Interface font | DM Sans 400–700 | Controls, paragraphs and labels |

The palette is Stripe-inspired at the user's request; FoodNow remains fictional and is not presented as an official Stripe product. Fonts load from Google Fonts with Arial/system fallbacks. The favicon is original simple typographic geometry. Restaurant imagery is locally bundled for consistent loading.

## Research observations, 11 September 2026

| Reference | What was observed on the public website | FoodNow design decision |
| --- | --- | --- |
| [Deliveroo](https://deliveroo.co.uk/) | Postcode entry leads discovery; separate partner/rider paths and a Plus membership destination | Keep delivery location visible, distinguish partner access, preview membership after the core order journey |
| [Postmates](https://postmates.com/) | Address entry, deliver-now selection, restaurant and delivery-partner routes | Put the delivery context ahead of search; keep the first customer task simple |
| [Skip](https://www.skipthedishes.com/) | Address-first discovery, cuisine links, order-tracking messaging, Skip+ and partner/courier paths | Cuisine filters, clear order progress and a visible membership extension |

These are public-page observations, not authenticated checkout studies or evidence of internal architecture. The restaurant grid, transparent bag summary, one-restaurant cart, decline recovery and FoodNow operations views are our design decisions. No reference business's logo, copy or proprietary restaurant assets were copied.

## Interaction principles

- Start with food discovery, location and useful search in the first viewport.
- Show fees before payment and retain the bag during a declined attempt.
- Require an explicit decision before replacing a bag from another restaurant.
- Use an order reference to connect diner, payment and partner narratives.
- Distinguish simulated, test and future features at the point of use.
- Keep architecture and Stripe object details in operator/demo surfaces; checkout stays customer-oriented.
- Use semantic links, buttons, labeled inputs, native dialogs, visible focus and live error/status announcements. Layouts adapt from desktop grids to mobile stacks.

## Photo attribution

All three photos are downloaded from Unsplash under the [Unsplash License](https://unsplash.com/license). They depict representative dishes; variants deliberately reuse the restaurant's image.

- Pizza: [Sam Moghadam Khamseh](https://unsplash.com/photos/pizza-on-brown-wooden-table-8bX4l0hFJfA)
- Burger: [Giorgi Iremadze](https://unsplash.com/photos/burger-with-lettuce-and-tomato-5ZR4DxAG3RQ)
- Salad bowl: [Janesca](https://unsplash.com/photos/a-fresh-salad-in-a-bowl-on-a-wooden-table-J9CA-D8QzGs)

## Four-persona extension

Five sample cuisines now provide discovery variety. Operations uses restaurant-grouped queues and a full journal; owners see their own menus and payable history; couriers get minimal unassigned pickup details and their own delivery/earnings views. These are FoodNow design decisions inspired by the delivery-marketplace category, not documented claims about reference businesses' internal dashboards.

Additional representative food images: [Frank Holleman, curry and rice](https://unsplash.com/photos/white-rice-with-green-vegetable-on-white-ceramic-plate-7oYPIMGUhjA) and [Karin Kim, sushi](https://unsplash.com/photos/a-variety-of-sushi-is-displayed-on-a-platter-eTMBxAvc9mc), from Unsplash. Images are illustrative of cuisine, not exact dish photography.
