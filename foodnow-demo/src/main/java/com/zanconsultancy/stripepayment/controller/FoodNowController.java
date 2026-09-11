package com.zanconsultancy.stripepayment.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.*;
import com.stripe.param.*;
import com.zanconsultancy.stripepayment.config.StripeProperties;
import com.zanconsultancy.stripepayment.model.*;
import com.zanconsultancy.stripepayment.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

// Legacy v1 controller retained for regression fixtures; no routes are registered.
public class FoodNowController {
 private final CatalogService catalog; private final OrderCalculator calculator; private final CheckoutService checkout;
 private final OrderStore orders; private final StripePaymentService payments; private final StripeCustomerService customers;
 private final StripeProperties stripe; private final Environment env;
 public FoodNowController(CatalogService c, OrderCalculator calc, CheckoutService ch, OrderStore o, StripePaymentService p, StripeCustomerService cs, StripeProperties s, Environment e) {
  catalog=c;calculator=calc;checkout=ch;orders=o;payments=p;customers=cs;stripe=s;env=e;
 }
 private boolean rehearsal(){return !"stripe".equals(env.getProperty("foodnow.mode"));}
 private String account(String role){if(!Set.of("restaurant","courier").contains(role))throw new IllegalArgumentException("Unknown partner");return env.getProperty("foodnow."+role+"-account","");}
 private RequestOptions options(String key){var b=RequestOptions.builder().setApiKey(stripe.secretKey());if(key!=null)b.setIdempotencyKey(key);return b.build();}
 @GetMapping("/health") public Object health(){return Map.of("status","ok","mode",rehearsal()?"rehearsal":"stripe");}
 @GetMapping("/api/config") public Object config(){return Map.of("mode",rehearsal()?"rehearsal":"stripe","publishableKey",rehearsal()?"":stripe.publishableKey(),"catalog",catalog.all());}
 public record CheckoutRequest(String checkoutToken,List<CartItem> items,String email){}
 @PostMapping("/api/checkout") public synchronized Object create(@RequestBody CheckoutRequest r) throws StripeException {
  checkout.validateToken(r.checkoutToken());String email=customers.normalizeEmail(r.email());
  if(email==null)throw new IllegalArgumentException("Enter a valid email address");
  var draft=calculator.calculate(r.items());
  String customer=rehearsal()?"rehearsal_"+email:customers.findOrCreate(email).getId();
  var order=orders.createOrGet(r.checkoutToken(),checkout.cartHash(draft,customer),draft,customer,checkout::newOrderId);
  if(rehearsal()) {
   if(order.paymentIntentId==null)orders.attachPaymentIntent(order.id,"demo_pi_"+order.id,"requires_payment_method");
   return Map.of("orderId",order.id,"amount",order.amount,"mode","rehearsal","status",order.paymentStatus);
  }
  if(order.paymentIntentId==null && Instant.now().isAfter(order.createdAt.plusSeconds(23*3600)))throw new IllegalArgumentException("Checkout expired. Start a new order.");
  PaymentIntent pi=order.paymentIntentId==null?payments.create(order):payments.retrieve(order.paymentIntentId);
  if(Boolean.TRUE.equals(pi.getLivemode()) || pi.getAmount()!=order.amount || !order.currency.equals(pi.getCurrency()) || !order.customerId.equals(pi.getCustomer())) throw new IllegalStateException("Payment does not match order");
  if(order.paymentIntentId==null)orders.attachPaymentIntent(order.id,pi.getId(),pi.getStatus());
  return Map.of("orderId",order.id,"amount",order.amount,"clientSecret",pi.getClientSecret(),"mode","stripe","status",pi.getStatus());
 }
 @GetMapping("/api/orders") public Object all(){return orders.all().stream().map(this::view).toList();}
 @GetMapping("/api/orders/{id}") public Object order(@PathVariable String id){return view(orders.require(id));}
 private Object view(OrderRecord o){
  long subtotal=o.lineItems.stream().mapToLong(OrderLine::lineTotal).sum();long restaurant=subtotal*80/100;
  return Map.ofEntries(Map.entry("id",o.id),Map.entry("items",o.lineItems),Map.entry("amount",o.amount),Map.entry("subtotal",subtotal),Map.entry("restaurantAmount",restaurant),Map.entry("courierAmount",399),Map.entry("platformAmount",o.amount-restaurant-399),Map.entry("currency",o.currency),Map.entry("status",o.paymentStatus),Map.entry("fulfillment",o.fulfillmentStatus),Map.entry("paymentIntentId",o.paymentIntentId==null?"":o.paymentIntentId),Map.entry("createdAt",o.createdAt),Map.entry("events",o.processedEventIds.size()),Map.entry("transfers",o.transfers));
 }
 public record Scenario(String outcome){}
 @PostMapping("/api/orders/{id}/simulate") public Object simulate(@PathVariable String id,@RequestBody Scenario scenario){
  if(!rehearsal())throw new IllegalArgumentException("Simulation is disabled in Stripe mode");
  if(!Set.of("succeeded","requires_payment_method","processing").contains(scenario.outcome()))throw new IllegalArgumentException("Unknown scenario");
  var o=orders.require(id);orders.applyStripeEvent("demo_evt_"+UUID.randomUUID(),Instant.now().getEpochSecond(),o.id,o.paymentIntentId,scenario.outcome());return view(o);
 }
 @PostMapping("/api/orders/{id}/allocate") public synchronized Object allocate(@PathVariable String id) throws StripeException {
  var o=orders.require(id);if(!"succeeded".equals(o.paymentStatus))throw new IllegalArgumentException("Wait for confirmed payment before allocating earnings");
  if(!rehearsal() && (account("restaurant").isBlank() || account("courier").isBlank()))throw new IllegalArgumentException("Configure both test connected accounts first");
  if(!rehearsal() && o.transfers.size()<2 && Instant.now().isAfter(o.paidAt.plusSeconds(23*3600)))throw new IllegalArgumentException("Transfer retry window expired. Reconcile in Stripe before proceeding.");
  PaymentIntent pi=rehearsal()?null:payments.retrieve(o.paymentIntentId);
  if(pi!=null && (!"succeeded".equals(pi.getStatus()) || Boolean.TRUE.equals(pi.getLivemode())))throw new IllegalArgumentException("Payment is not eligible for transfer");
  for(String role:List.of("restaurant","courier")) {
   if(o.transfers.containsKey(role))continue;
   long amount=role.equals("courier")?399:o.lineItems.stream().mapToLong(OrderLine::lineTotal).sum()*80/100;
   String transfer;
   if(rehearsal())transfer="demo_tr_"+role+"_"+o.id;
   else {
    Account a=Account.retrieve(account(role),options(null));
    if(a.getCapabilities()==null || !"active".equals(a.getCapabilities().getTransfers()))throw new IllegalArgumentException(role+" transfers capability is not active");
    var params=TransferCreateParams.builder().setAmount(amount).setCurrency(o.currency).setDestination(a.getId()).setSourceTransaction(pi.getLatestCharge()).setTransferGroup(o.id).putMetadata("foodnow_order",o.id).build();
    transfer=Transfer.create(params,options("foodnow_"+o.id+"_"+role)).getId();
   }
   orders.recordTransfer(o.id,role,transfer);
  }return view(o);
 }
 @GetMapping("/api/partners/{role}") public Object partner(@PathVariable String role) throws StripeException {
  String id=account(role);
  if(rehearsal())return Map.of("mode","rehearsal","role",role,"configured",true,"status","Illustrative account","payoutsEnabled",false,"requirements",List.of("Business details","Identity verification","Bank account"));
  if(id.isBlank())return Map.of("mode","stripe","role",role,"configured",false,"status","Test account needed");
  Account a=Account.retrieve(id,options(null));
  return Map.of("mode","stripe","role",role,"configured",true,"accountId",a.getId(),"status",a.getCapabilities()!=null && "active".equals(a.getCapabilities().getTransfers())?"Transfers active":"Action required","payoutsEnabled",Boolean.TRUE.equals(a.getPayoutsEnabled()),"requirements",a.getRequirements()==null?List.of():a.getRequirements().getCurrentlyDue());
 }
 @PostMapping("/api/partners/{role}/onboard") public Object onboard(@PathVariable String role) throws StripeException {
  if(rehearsal())return Map.of("mode","rehearsal","message","Stripe-hosted onboarding collects partner identity, business and bank details. Configure test mode to open the real flow.");
  String id=account(role);if(id.isBlank())throw new IllegalArgumentException("Configure this partner's test connected account first");
  String base=env.getProperty("foodnow.base-url");
  var link=AccountLink.create(AccountLinkCreateParams.builder().setAccount(id).setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING).setReturnUrl(base+"/?partner="+role+"#partners").setRefreshUrl(base+"/?partner="+role+"&refresh=1#partners").build(),options(null));
  return Map.of("url",link.getUrl());
 }
 @PostMapping("/api/demo/reset") public Object reset(){if(!rehearsal())throw new IllegalArgumentException("Reset is available only in rehearsal mode");orders.reset();return Map.of("reset",true);}
 @PostMapping(value="/webhook",consumes=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<String> webhook(@RequestBody byte[] body,@RequestHeader(value="Stripe-Signature",required=false)String signature){
  if(rehearsal())return ResponseEntity.status(503).body("Stripe mode is disabled");
  if(body.length>262144 || signature==null)return ResponseEntity.badRequest().body("Invalid webhook");
  Event e;try{e=Webhook.constructEvent(new String(body,StandardCharsets.UTF_8),signature,stripe.webhookSecret());}catch(Exception ex){return ResponseEntity.badRequest().body("Invalid signature");}
  if(Boolean.TRUE.equals(e.getLivemode()))return ResponseEntity.badRequest().body("Test events only");
  if(!Set.of("payment_intent.succeeded","payment_intent.payment_failed","payment_intent.processing","payment_intent.canceled").contains(e.getType()))return ResponseEntity.ok("ignored");
  Object obj=e.getDataObjectDeserializer().getObject().orElse(null);
  if(!(obj instanceof PaymentIntent pi))return ResponseEntity.badRequest().body("Event API version must match Stripe SDK");
  String id=pi.getMetadata().get("order_id");
  if(id==null)return ResponseEntity.ok("unrelated");
  OrderRecord o;try{o=orders.require(id);}catch(NoSuchElementException ex){return ResponseEntity.status(409).body("Order not yet available");}
  if(pi.getAmount()!=o.amount || !o.currency.equals(pi.getCurrency()) || !o.customerId.equals(pi.getCustomer()) || (o.paymentIntentId!=null && !o.paymentIntentId.equals(pi.getId())))return ResponseEntity.badRequest().body("Order mismatch");
  orders.applyStripeEvent(e.getId(),e.getCreated(),id,pi.getId(),pi.getStatus());return ResponseEntity.ok("received");
 }
 @ExceptionHandler({IllegalArgumentException.class,CartChangedException.class,NoSuchElementException.class}) public ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()==null?"Invalid request":e.getMessage()));}
 @ExceptionHandler(StripeException.class) public ResponseEntity<?> stripeError(StripeException e){return ResponseEntity.status(502).body(Map.of("error","Stripe could not complete this request. Check your test configuration and Stripe request logs."));}
}
