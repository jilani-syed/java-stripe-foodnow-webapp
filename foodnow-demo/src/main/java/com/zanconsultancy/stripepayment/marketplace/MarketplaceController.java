package com.zanconsultancy.stripepayment.marketplace;

import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.zanconsultancy.stripepayment.config.StripeProperties;
import com.zanconsultancy.stripepayment.model.*;
import com.zanconsultancy.stripepayment.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static com.zanconsultancy.stripepayment.marketplace.MarketplaceStore.*;

@RestController
public class MarketplaceController {
 private final MarketplaceStore store;
 private final MarketplaceAuth auth;
 private final MarketplaceStripe gateway;
 private final StripeProperties keys;
 private final StripeCustomerService customers;
 private final CheckoutService checkout;
 private final Environment env;
 private final Map<String,List<Long>> loginAttempts=new LinkedHashMap<>();
 public MarketplaceController(MarketplaceStore store,MarketplaceAuth auth,MarketplaceStripe gateway,StripeProperties keys,StripeCustomerService customers,CheckoutService checkout,Environment env){this.store=store;this.auth=auth;this.gateway=gateway;this.keys=keys;this.customers=customers;this.checkout=checkout;this.env=env;}
 private String currency(HttpServletRequest req){String locale=req.getHeader("X-FoodNow-Locale");return switch(locale==null?"en-US":locale){case "en-US"->"usd";case "en-GB"->"gbp";case "fr-FR"->"eur";default->throw new IllegalArgumentException("Unsupported locale");};}
 @GetMapping("/api/insights") public Object insights(HttpServletRequest req){auth.require(req,"operations");return store.read(d->List.of("usd","gbp","eur").stream().map(c->{var orders=d.orders.values().stream().filter(o->c.equals(o.currency)).toList();var paid=orders.stream().filter(o->o.paymentStatus.equals("succeeded")).toList();Map<String,Object> row=new LinkedHashMap<>();row.put("currency",c);row.put("orders",orders.size());row.put("paidOrders",paid.size());row.put("paidVolume",paid.stream().mapToLong(o->o.amount).sum());row.put("averageOrderValue",paid.isEmpty()?0:paid.stream().mapToLong(o->o.amount).sum()/paid.size());row.put("platformRevenue",paid.stream().mapToLong(o->o.platformEarnings).sum());row.put("delivered",paid.stream().filter(o->o.stage.equals("delivered")).count());row.put("pendingPayment",orders.stream().filter(o->!o.paymentStatus.equals("succeeded")).count());return row;}).toList());}
 private boolean demo(){return !"stripe".equals(env.getProperty("foodnow.mode","rehearsal"));}
 private String mode(){return demo()?"rehearsal":"stripe";}
 private static void check(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
 private static String text(String value,int max,String field){check(value!=null&&!value.isBlank()&&value.trim().length()<=max,"Enter a valid "+field);return value.trim();}
 private static String email(String value){String v=text(value,254,"email").toLowerCase(Locale.ROOT);check(v.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"),"Enter a valid email");return v;}
 private static <T> T required(T value){if(value==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found");return value;}
 @ModelAttribute public void guard(HttpServletRequest req){if(!Set.of("GET","HEAD","OPTIONS").contains(req.getMethod())&&!req.getRequestURI().equals("/webhook"))auth.verifyCsrf(req);}
 @GetMapping("/health") public Object health(){return Map.of("status","ok","mode",mode(),"version",2);}
 @GetMapping("/api/session") public Object session(HttpServletRequest req){return Map.of("user",auth.publicUser(auth.user(req)),"csrf",auth.csrf(req),"portal",auth.portal(req));}
 public record Login(String role,String email,String password){}
 @PostMapping("/api/auth/login") public synchronized Object login(@RequestBody Login input,HttpServletRequest req){
  check(Set.of("diner","restaurant","courier","operations").contains(Objects.toString(input.role(),"")),"Choose a workspace");
  String email=email(input.email());text(input.password(),160,"password");String rateKey=req.getRemoteAddr()+":"+email;long now=System.currentTimeMillis();
  if(loginAttempts.size()>1000)loginAttempts.remove(loginAttempts.keySet().iterator().next());
  List<Long> attempts=loginAttempts.computeIfAbsent(rateKey,k->new ArrayList<>());attempts.removeIf(t->t<now-300000);if(attempts.size()>=10)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many attempts. Try again in five minutes.");attempts.add(now);
  User u=store.read(d->d.users.values().stream().filter(x->x.email.equals(email)).findFirst().orElse(null));
  if(u==null||!u.role.equals(input.role())||!Passwords.matches(input.password(),u.password))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email, password or selected workspace is incorrect.");
  attempts.clear();auth.login(req,u);return Map.of("user",auth.publicUser(u),"csrf",auth.csrf(req));
 }
 public record Registration(String role,String name,String email,String password,String businessName,String cuisine,String address,String vehicle){}
 @PostMapping("/api/auth/register") public Object register(@RequestBody Registration r,HttpServletRequest req){
  check(Set.of("diner","restaurant","courier").contains(Objects.toString(r.role(),"")),"Public operations registration is not available");
  String name=text(r.name(),80,"name"),email=email(r.email()),password=text(r.password(),160,"password");check(password.length()>=10,"Use at least 10 characters for your password");
  if(r.role().equals("restaurant")){text(r.businessName(),80,"restaurant name");text(r.address(),200,"business address");check(Set.of("Pizza","Burgers","Healthy","Indian","Japanese").contains(Objects.toString(r.cuisine(),"")),"Choose a cuisine");}
  if(r.role().equals("courier"))check(Set.of("Bicycle","Car","Scooter","Walking").contains(Objects.toString(r.vehicle(),"")),"Choose a vehicle");
  String hash=Passwords.hash(password);
  User u=store.tx(d->{check(d.users.values().stream().noneMatch(x->x.email.equals(email)),"An account already uses that email");User n=new User();n.id=id("user");n.name=name;n.email=email;n.password=hash;n.role=r.role();
   if(r.role().equals("restaurant")){Restaurant b=new Restaurant();b.id=id("restaurant");b.name=r.businessName().trim();b.cuisine=r.cuisine();b.address=r.address().trim();b.description="Freshly made local favorites.";b.ownerId=n.id;b.createdAt=Instant.now().toString();b.image=imageFor(r.cuisine());n.entityId=b.id;d.restaurants.put(b.id,b);}
   if(r.role().equals("courier")){Courier c=new Courier();c.id=id("courier");c.userId=n.id;c.name=name;c.vehicle=r.vehicle();c.createdAt=Instant.now().toString();n.entityId=c.id;d.couriers.put(c.id,c);}
   d.users.put(n.id,n);return n;});auth.login(req,u);return Map.of("user",auth.publicUser(u),"csrf",auth.csrf(req));
 }
 private static String imageFor(String cuisine){return "/images/"+switch(cuisine){case "Burgers"->"burger";case "Healthy"->"bowl";case "Indian"->"curry";case "Japanese"->"sushi";default->"pizza";}+".jpg";}
 @PostMapping("/api/auth/logout") public Object logout(HttpServletRequest req){auth.session(req).removeAttribute("user:"+auth.portal(req));return Map.of("signedOut",true);}
 @GetMapping("/api/stripe/account") public Object stripeAccount(HttpServletRequest req)throws StripeException{
  auth.require(req,"operations");if(demo())return Map.of("mode",mode(),"connected",false,"message","Configure your Stripe test keys and webhook secret, then restart in Stripe mode.");
  return Map.of("mode",mode(),"connected",true,"account",gateway.platform());
 }
 @PostMapping("/api/orders/{id}/stripe-evidence") public Object evidence(@PathVariable String id,HttpServletRequest req)throws StripeException{
  auth.require(req,"operations");Order o=accessible(id,req);check(!demo(),"Stripe evidence is unavailable in payment rehearsal");check(o.paymentIntentId!=null,"Start the payment first");
  var rows=gateway.evidence(o.paymentIntentId);store.tx(d->{Order n=d.orders.get(id);n.stripeEvidence=rows;n.evidenceCheckedAt=Instant.now().toString();return null;});return order(id,req);
 }
 @GetMapping("/api/config") public Object config(){return store.read(d->Map.of("mode",mode(),"publishableKey",demo()?"":keys.publishableKey(),"restaurants",d.restaurants.values().stream().filter(r->r.approval.equals("approved")).map(this::publicRestaurant).toList(),"catalog",d.items.values().stream().filter(i->i.available&&d.restaurants.get(i.restaurantId).approval.equals("approved")).toList()));}
 private Map<String,Object> publicRestaurant(Restaurant r){return Map.of("id",r.id,"name",r.name,"cuisine",r.cuisine,"description",r.description,"image",r.image,"address",r.address,"deliveryTime","20–30 min","rating","4.9");}
 private Order accessible(String id,HttpServletRequest req){User u=auth.user(req);String guest=(String)auth.session(req).getAttribute("guest");Order o=store.read(d->required(d.orders.get(id)));if(!auth.canView(u,guest,o))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found in this workspace");return o;}
 private Object view(Order o,User u){
  Map<String,Object> v=new LinkedHashMap<>();v.put("id",o.id);v.put("restaurantId",o.restaurantId);v.put("restaurantName",o.restaurantName);v.put("courierId",o.courierId);v.put("buyerName",o.buyerName);v.put("items",o.lineItems);v.put("amount",o.amount);v.put("subtotal",o.subtotal);v.put("stage",o.stage);v.put("status",o.paymentStatus);v.put("currency",o.currency);v.put("createdAt",o.createdAt);v.put("history",o.history);v.put("mode",o.mode);v.put("paymentMethod",o.paymentMethod);
  if(u==null||Set.of("diner","courier","operations").contains(u.role))v.put("address",o.address);
  if(u!=null&&!u.role.equals("diner")){
   if(u.role.equals("operations")){v.put("restaurantEarnings",o.restaurantEarnings);v.put("courierEarnings",o.courierEarnings);}
   if(u.role.equals("operations")){v.put("platformEarnings",o.platformEarnings);v.put("processingFee",o.processingFee);v.put("feeReconciled",o.feeReconciled);v.put("paymentIntentId",o.paymentIntentId);v.put("email",o.email);v.put("transfers",o.transfers);v.put("stripeEvidence",o.stripeEvidence);v.put("evidenceCheckedAt",o.evidenceCheckedAt);}
   else {String role=u.role;v.put("transferId",o.transfers.get(role));v.put("earnings",role.equals("courier")?o.courierEarnings:o.restaurantEarnings);}
  }return v;
 }
 @GetMapping("/api/orders") public Object orders(HttpServletRequest req){User u=auth.user(req);String guest=(String)auth.session(req).getAttribute("guest");return store.read(d->d.orders.values().stream().filter(o->auth.canView(u,guest,o)&&o.currency.equals(currency(req))).map(o->view(o,u)).toList());}
 @GetMapping("/api/orders/{id}") public Object order(@PathVariable String id,HttpServletRequest req){Order o=accessible(id,req);Map<String,Object> v=(Map<String,Object>)view(o,auth.user(req));v.put("pickupAddress",store.read(d->d.restaurants.get(o.restaurantId).address));return v;}
 public record CheckoutRequest(String checkoutToken,List<CartItem> items,String email,String name,String address){}
 @PostMapping("/api/checkout") public synchronized Object create(@RequestBody CheckoutRequest r,HttpServletRequest req)throws StripeException{
  String selectedCurrency=currency(req);String buyer=auth.buyer(req);checkout.validateToken(r.checkoutToken());String mail=email(r.email()),name=text(r.name(),80,"name"),address=text(r.address(),200,"delivery address");
  // Once an attempt exists, keep its price snapshot even if the owner edits the menu.
  Order existing=store.read(d->d.orders.values().stream().filter(o->o.checkoutToken.equals(r.checkoutToken())).findFirst().orElse(null));
  Order o;
  if(existing!=null){check(existing.currency.equals(selectedCurrency),"Currency changed. Start a new checkout.");check(existing.buyerId.equals(buyer),"This checkout belongs to another session");check(existing.email.equals(mail)&&existing.address.equals(address)&&existing.buyerName.equals(name),"Checkout details changed. Start a new checkout.");check(r.items()!=null&&r.items().size()==existing.lineItems.size(),"Bag changed. Start a new checkout.");Map<String,Integer> input=new HashMap<>();for(CartItem i:r.items()){check(i!=null&&!input.containsKey(i.dishId()),"Invalid items");input.put(i.dishId(),i.quantity());}check(existing.lineItems.stream().allMatch(l->Objects.equals(input.get(l.dishId()),l.quantity())),"Bag changed. Start a new checkout.");o=existing;}
  else {
   check(r.items()!=null&&!r.items().isEmpty()&&r.items().size()<=30,"Choose at least one menu item");
   o=store.tx(d->{Set<String> seen=new HashSet<>();List<OrderLine> lines=new ArrayList<>();String restaurantId=null;for(CartItem ci:r.items()){check(ci!=null&&ci.quantity()>=1&&ci.quantity()<=10&&seen.add(ci.dishId()),"Invalid quantity or duplicate item");Item item=required(d.items.get(ci.dishId()));check(item.available,"An item is no longer available");if(restaurantId==null)restaurantId=item.restaurantId;check(restaurantId.equals(item.restaurantId),"Each order must come from one restaurant");lines.add(new OrderLine(item.id,item.title,ci.quantity(),item.amount,item.amount*ci.quantity()));}
    Restaurant restaurant=required(d.restaurants.get(restaurantId));check(restaurant.approval.equals("approved"),"Restaurant is not accepting orders");if(!demo())check(restaurant.transfersReady&&!restaurant.stripeAccount.isBlank(),"This restaurant must complete Stripe test onboarding before accepting payments");
    Order n=new Order();n.id=id("ord");n.checkoutToken=r.checkoutToken();n.buyerId=buyer;n.buyerName=name;n.email=mail;n.address=address;n.restaurantId=restaurant.id;n.restaurantName=restaurant.name;n.lineItems=lines;n.itemCount=lines.stream().mapToInt(OrderLine::quantity).sum();n.subtotal=lines.stream().mapToLong(OrderLine::lineTotal).sum();n.amount=n.subtotal+598;n.restaurantEarnings=n.subtotal*(10000-restaurant.commissionBps)/10000;n.platformEarnings=n.amount-n.restaurantEarnings-n.courierEarnings;n.currency=selectedCurrency;n.mode=mode();n.createdAt=n.updatedAt=Instant.now();history(n,"payment_pending","diner","Checkout started.");d.orders.put(n.id,n);return n;});
  }
  if(demo()){if(o.paymentIntentId==null){o=store.tx(d->{Order n=d.orders.get(oId(existing,r.checkoutToken(),d));n.paymentIntentId="demo_pi_"+n.id;n.customerId="demo_customer_"+n.buyerId;n.paymentStatus="requires_payment_method";return n;});}return checkoutView(o,null);}
  check(o.paymentIntentId!=null||Instant.now().isBefore(o.createdAt.plusSeconds(23*3600)),"Checkout expired; reconcile in Stripe before retrying.");
  if(o.customerId==null){String customer=customers.findOrCreate(mail).getId();String orderId=o.id;o=store.tx(d->{Order n=d.orders.get(orderId);n.customerId=customer;return n;});}
  PaymentIntent pi=gateway.intent(o);check(!Boolean.TRUE.equals(pi.getLivemode())&&pi.getAmount()==o.amount&&o.currency.equals(pi.getCurrency())&&o.customerId.equals(pi.getCustomer()),"Stripe payment does not match the order");
  String orderId=o.id;store.tx(d->{Order n=d.orders.get(orderId);check(n.paymentIntentId==null||n.paymentIntentId.equals(pi.getId()),"Payment association mismatch");n.paymentIntentId=pi.getId();if(n.lastStripeEventCreated==0)n.paymentStatus=pi.getStatus();return null;});return checkoutView(store.read(d->d.orders.get(orderId)),pi.getClientSecret());
 }
 private String oId(Order existing,String token,Data d){return existing!=null?existing.id:d.orders.values().stream().filter(o->o.checkoutToken.equals(token)).findFirst().orElseThrow().id;}
 private Object checkoutView(Order o,String secret){Map<String,Object> r=new LinkedHashMap<>();r.put("orderId",o.id);r.put("amount",o.amount);r.put("currency",o.currency);r.put("status",o.paymentStatus);r.put("mode",mode());if(secret!=null)r.put("clientSecret",secret);return r;}
 public record Scenario(String outcome,String method){}
 @PostMapping("/api/orders/{id}/simulate") public Object simulate(@PathVariable String id,@RequestBody Scenario r,HttpServletRequest req){check(demo(),"Simulation is disabled in Stripe mode");Order o=accessible(id,req);check(auth.buyer(req).equals(o.buyerId),"Use the diner session that placed this order");check(Set.of("succeeded","processing","requires_payment_method").contains(Objects.toString(r.outcome(),"")),"Choose a payment outcome");check(Set.of("Card","Apple Pay","Google Pay","Link").contains(Objects.toString(r.method(),"")),"Choose a payment method");store.tx(d->{Order n=d.orders.get(id);if(!n.paymentStatus.equals("succeeded")){n.paymentStatus=r.outcome();n.paymentMethod=r.method()+" (simulated)";history(n,n.stage,"payment",r.outcome().equals("requires_payment_method")?"Payment declined; awaiting another method.":"Payment outcome: "+r.outcome());if(r.outcome().equals("succeeded")){n.paidAt=Instant.now();paymentConfirmed(d,n);}}return null;});return order(id,req);}
 @GetMapping("/api/workspace") public Object workspace(HttpServletRequest req){User u=auth.require(req,"operations","restaurant","courier");return store.read(d->{Map<String,Object> v=new LinkedHashMap<>();v.put("user",auth.publicUser(u));v.put("orders",d.orders.values().stream().filter(o->auth.canView(u,null,o)&&o.currency.equals(currency(req))).map(o->{Map<String,Object> row=(Map<String,Object>)view(o,u);row.put("pickupAddress",d.restaurants.get(o.restaurantId).address);return row;}).toList());v.put("restaurants",d.restaurants.values().stream().filter(r->u.role.equals("operations")||u.role.equals("restaurant")&&u.entityId.equals(r.id)).toList());v.put("couriers",d.couriers.values().stream().filter(c->u.role.equals("operations")||u.role.equals("courier")&&u.entityId.equals(c.id)).toList());v.put("menu",d.items.values().stream().filter(i->u.role.equals("operations")||u.role.equals("restaurant")&&u.entityId.equals(i.restaurantId)).toList());v.put("ledger",d.ledger.stream().filter(e->e.currency.equals(currency(req))&&(u.role.equals("operations")||u.entityId.equals(e.partnerId))).toList());if(u.role.equals("courier")){Courier c=d.couriers.get(u.entityId);v.put("availableOrders",c.approval.equals("approved")?d.orders.values().stream().filter(o->o.stage.equals("ready")&&o.courierId==null&&o.currency.equals(currency(req))).map(o->Map.of("id",o.id,"restaurantName",o.restaurantName,"pickupAddress",d.restaurants.get(o.restaurantId).address,"earnings",o.courierEarnings)).toList():List.of());}return v;});}
 public record Review(String decision,String note){}
 @PostMapping("/api/partners/{kind}/{id}/review") public Object review(@PathVariable String kind,@PathVariable String id,@RequestBody Review r,HttpServletRequest req){auth.require(req,"operations");check(Set.of("approved","rejected").contains(Objects.toString(r.decision(),"")),"Choose approve or reject");check(r.note()==null||r.note().length()<=300,"Review note is too long");return store.tx(d->{if(kind.equals("restaurant")){Restaurant p=required(d.restaurants.get(id));check(p.approval.equals("pending"),"This request has already been reviewed");p.approval=r.decision();p.reviewNote=r.note()==null?"":r.note();}else{check(kind.equals("courier"),"Unknown partner type");Courier p=required(d.couriers.get(id));check(p.approval.equals("pending"),"This request has already been reviewed");p.approval=r.decision();p.reviewNote=r.note()==null?"":r.note();}return Map.of("reviewed",true);});}
 public record MenuRequest(String id,String title,String description,long amount,boolean available){}
 @PostMapping("/api/menu") public Object menu(@RequestBody MenuRequest r,HttpServletRequest req){User u=auth.require(req,"restaurant");String title=text(r.title(),100,"dish name");check(r.description()!=null&&r.description().length()<=400,"Description is too long");check(r.amount()>=100&&r.amount()<=100000,"Price must be between $1 and $1,000");return store.tx(d->{Restaurant restaurant=d.restaurants.get(u.entityId);check(!restaurant.approval.equals("rejected"),"Restaurant registration was declined");Item i=r.id()==null||r.id().isBlank()?new Item():required(d.items.get(r.id()));if(i.id!=null)check(u.entityId.equals(i.restaurantId),"You cannot edit another restaurant's menu");else{i.id=id("dish");i.restaurantId=u.entityId;i.image=restaurant.image;}i.title=title;i.description=r.description().trim();i.amount=r.amount();i.available=r.available();d.items.put(i.id,i);return i;});}
 public record Status(String next,boolean confirmed){}
 @PostMapping("/api/orders/{id}/status") public Object status(@PathVariable String id,@RequestBody Status r,HttpServletRequest req){User u=auth.require(req,"restaurant","courier");accessible(id,req);store.tx(d->{Order o=required(d.orders.get(id));check(o.paymentStatus.equals("succeeded"),"Payment must be confirmed first");if(o.stage.equals(r.next()))return null;
  if(u.role.equals("restaurant")){check(d.restaurants.get(u.entityId).approval.equals("approved"),"Restaurant approval is required");String next=switch(o.stage){case "placed"->"accepted";case "accepted"->"preparing";case "preparing"->"ready";default->"";};check(next.equals(r.next()),"That restaurant order transition is not allowed");}
  else{check(d.couriers.get(u.entityId).approval.equals("approved"),"Courier approval is required");check(Objects.equals(o.courierId,u.entityId),"This delivery is not assigned to you");check(o.stage.equals("ready")&&r.next().equals("picked_up")||o.stage.equals("picked_up")&&r.next().equals("delivered"),"That delivery transition is not allowed");if(r.next().equals("delivered"))check(r.confirmed(),"Confirm that the order was delivered to the customer");}
  o.stage=r.next();o.updatedAt=Instant.now();if(o.stage.equals("delivered"))o.fulfillmentStatus="delivered";history(o,o.stage,u.role,u.name+" updated the order.");return null;});return order(id,req);}
 public record Assignment(String courierId){}
 @PostMapping("/api/orders/{id}/assign") public Object assign(@PathVariable String id,@RequestBody Assignment r,HttpServletRequest req){User u=auth.require(req,"operations","courier");String courier=u.role.equals("courier")?u.entityId:r.courierId();store.tx(d->{Order o=required(d.orders.get(id));Courier c=required(d.couriers.get(courier));check(c.approval.equals("approved"),"Courier approval is required");check(o.paymentStatus.equals("succeeded")&&!Set.of("picked_up","delivered").contains(o.stage),"Order is not available for assignment");if(u.role.equals("courier"))check(o.stage.equals("ready"),"Only ready orders can be claimed");if(Objects.equals(o.courierId,courier))return null;check(o.courierId==null,"Another courier is already assigned");o.courierId=courier;journal(d,o,"assign:"+o.id,"Courier assignment",List.of(entry("courier_payable","unassigned",o.courierEarnings,0),entry("courier_payable",courier,0,o.courierEarnings)));history(o,o.stage,u.role,"Assigned to "+c.name+".");return null;});return order(id,req);}
 private String entity(User u){check(Set.of("restaurant","courier").contains(u.role),"Partner account required");return u.entityId;}
 @PostMapping("/api/connect/start") public synchronized Object connectStart(HttpServletRequest req)throws StripeException{
  User u=auth.require(req,"restaurant","courier");String entity=entity(u);
  String[] state=store.read(d->u.role.equals("restaurant")?new String[]{d.restaurants.get(entity).stripeAccount,d.restaurants.get(entity).connectAttemptAt}:new String[]{d.couriers.get(entity).stripeAccount,d.couriers.get(entity).connectAttemptAt});
  if(demo()){store.tx(d->{if(u.role.equals("restaurant")){Restaurant p=d.restaurants.get(entity);p.onboardingComplete=true;p.transfersReady=true;}else{Courier p=d.couriers.get(entity);p.onboardingComplete=true;p.transfersReady=true;}return null;});return Map.of("simulated",true,"message","Rehearsal onboarding completed. Operations approval is a separate step.");}
  String account=state[0];if(account.isBlank()){
   check(state[1]==null||Instant.now().isBefore(Instant.parse(state[1]).plusSeconds(23*3600)),"Account creation needs reconciliation before retrying");
   store.tx(d->{if(u.role.equals("restaurant")){Restaurant p=d.restaurants.get(entity);if(p.connectAttemptAt==null)p.connectAttemptAt=Instant.now().toString();}else{Courier p=d.couriers.get(entity);if(p.connectAttemptAt==null)p.connectAttemptAt=Instant.now().toString();}return null;});
   Account created=gateway.createAccount(entity,u.email);account=created.getId();String accountId=account;store.tx(d->{if(u.role.equals("restaurant"))d.restaurants.get(entity).stripeAccount=accountId;else d.couriers.get(entity).stripeAccount=accountId;return null;});
  }
  return Map.of("url",gateway.onboarding(account,env.getProperty("foodnow.base-url"),u.role.equals("restaurant")?"owner":"courier"));
 }
 @PostMapping("/api/connect/refresh") public Object connectRefresh(HttpServletRequest req)throws StripeException{User u=auth.require(req,"restaurant","courier");if(demo())return workspace(req);refreshPartner(u.role,u.entityId);return workspace(req);}
 private void refreshPartner(String role,String entity)throws StripeException{
  String id=store.read(d->role.equals("restaurant")?required(d.restaurants.get(entity)).stripeAccount:required(d.couriers.get(entity)).stripeAccount);check(!id.isBlank(),"Start Stripe onboarding first");Account a=gateway.account(id);
  store.tx(d->{boolean ready=a.getCapabilities()!=null&&"active".equals(a.getCapabilities().getTransfers());List<String> needs=a.getRequirements()==null||a.getRequirements().getCurrentlyDue()==null?List.of():a.getRequirements().getCurrentlyDue();if(role.equals("restaurant")){Restaurant p=d.restaurants.get(entity);p.onboardingComplete=Boolean.TRUE.equals(a.getDetailsSubmitted());p.transfersReady=ready;p.payoutsEnabled=Boolean.TRUE.equals(a.getPayoutsEnabled());p.requirements=needs;}else{Courier p=d.couriers.get(entity);p.onboardingComplete=Boolean.TRUE.equals(a.getDetailsSubmitted());p.transfersReady=ready;p.payoutsEnabled=Boolean.TRUE.equals(a.getPayoutsEnabled());p.requirements=needs;}return null;});
 }
 @PostMapping("/api/orders/{id}/allocate") public synchronized Object allocate(@PathVariable String id,HttpServletRequest req)throws StripeException{
  auth.require(req,"operations");Order o=accessible(id,req);check(o.stage.equals("delivered")&&o.paymentStatus.equals("succeeded"),"Only delivered, paid orders can be settled");
  if(!demo()){BalanceTransaction settlement=gateway.fee(o.paymentIntentId);check(settlement!=null&&o.currency.equals(settlement.getCurrency()),"Transfer requires matching settlement currency; configure multi-currency settlement or reconcile FX before allocation.");refreshPartner("restaurant",o.restaurantId);refreshPartner("courier",o.courierId);}
  store.tx(d->{Order n=d.orders.get(id);Restaurant restaurant=d.restaurants.get(n.restaurantId);Courier courier=d.couriers.get(n.courierId);check(restaurant.approval.equals("approved")&&courier.approval.equals("approved"),"Both partners must be approved");if(!demo())check(restaurant.transfersReady&&courier.transfersReady,"Both partners need active Stripe transfers capabilities");if(n.transferAttemptAt==null)n.transferAttemptAt=Instant.now().toString();check(demo()||n.transfers.size()==2||Instant.now().isBefore(Instant.parse(n.transferAttemptAt).plusSeconds(23*3600)),"Transfer retry window expired; reconcile with Stripe before retrying");n.transferDestinations.putIfAbsent("restaurant",restaurant.stripeAccount);n.transferDestinations.putIfAbsent("courier",courier.stripeAccount);return null;});
  for(String role:List.of("restaurant","courier")){
   o=store.read(d->d.orders.get(id));if(o.transfers.containsKey(role))continue;
   long amount=role.equals("restaurant")?o.restaurantEarnings:o.courierEarnings;
   String transfer=demo()?"demo_tr_"+role+"_"+id:gateway.transfer(o,role,o.transferDestinations.get(role),amount);
   store.tx(d->{Order n=d.orders.get(id);if(!n.transfers.containsKey(role)){n.transfers.put(role,transfer);String partner=role.equals("restaurant")?n.restaurantId:n.courierId;journal(d,n,"transfer:"+n.id+":"+role,"Transfer to "+role+" Stripe balance",List.of(entry(role+"_payable",partner,amount,0),entry("stripe_clearing","foodnow",0,amount)));history(n,n.stage,"operations",role+" earnings transferred.");}return null;});
  }return order(id,req);
 }
 @PostMapping("/api/orders/{id}/reconcile-fee") public Object fee(@PathVariable String id,HttpServletRequest req)throws StripeException{
  auth.require(req,"operations");check(!demo(),"Actual processing fees are available only in Stripe test mode");Order o=accessible(id,req);check(o.paymentStatus.equals("succeeded"),"Payment is not confirmed");BalanceTransaction b=gateway.fee(o.paymentIntentId);check(b!=null,"Stripe balance transaction is not yet available");check(o.currency.equals(b.getCurrency()),"Settlement currency differs; currency conversion reconciliation is outside this demo");
  store.tx(d->{Order n=d.orders.get(id);if(!n.feeReconciled){n.processingFee=b.getFee();n.feeReconciled=true;journal(d,n,"fee:"+n.id,"Stripe processing fee",List.of(entry("payment_expense","foodnow",n.processingFee,0),entry("stripe_clearing","foodnow",0,n.processingFee)));}return null;});return order(id,req);
 }
 @PostMapping(value="/webhook",consumes=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<String> webhook(@RequestBody byte[] payload,@RequestHeader(value="Stripe-Signature",required=false)String signature){
  if(demo())return ResponseEntity.status(503).body("Stripe mode disabled");if(payload.length>262144||signature==null)return ResponseEntity.badRequest().body("Invalid payload");
  Event event;try{event=Webhook.constructEvent(new String(payload,StandardCharsets.UTF_8),signature,keys.webhookSecret());}catch(Exception e){return ResponseEntity.badRequest().body("Invalid signature");}
  if(Boolean.TRUE.equals(event.getLivemode()))return ResponseEntity.badRequest().body("Test events only");
  if(!Set.of("payment_intent.succeeded","payment_intent.processing","payment_intent.payment_failed","payment_intent.canceled").contains(event.getType()))return ResponseEntity.ok("ignored");
  Object object=event.getDataObjectDeserializer().getObject().orElse(null);if(!(object instanceof PaymentIntent pi))return ResponseEntity.badRequest().body("Event API version mismatch");
  String id=pi.getMetadata().get("order_id");if(id==null)return ResponseEntity.ok("unrelated");
  return store.tx(d->{Order o=d.orders.get(id);if(o==null)return ResponseEntity.status(409).body("Order missing");if(pi.getAmount()!=o.amount||!o.currency.equals(pi.getCurrency())||!Objects.equals(o.customerId,pi.getCustomer())||o.paymentIntentId!=null&&!o.paymentIntentId.equals(pi.getId()))return ResponseEntity.badRequest().body("Order mismatch");
   if(o.processedEventIds.contains(event.getId()))return ResponseEntity.ok("duplicate");o.processedEventIds.add(event.getId());o.paymentIntentId=pi.getId();
   if(event.getCreated()>=o.lastStripeEventCreated&&!o.paymentStatus.equals("succeeded")){o.lastStripeEventCreated=event.getCreated();o.paymentStatus=pi.getStatus();o.paymentMethod=pi.getPaymentMethodTypes()==null?"Stripe":String.join(", ",pi.getPaymentMethodTypes())+" (enabled methods)";if(pi.getStatus().equals("succeeded")){o.paidAt=Instant.now();paymentConfirmed(d,o);}else history(o,o.stage,"payment","Stripe payment status: "+pi.getStatus());}return ResponseEntity.ok("received");});
 }
 @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> statusError(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",e.getReason()==null?"Request not allowed":e.getReason()));}
 @ExceptionHandler({IllegalArgumentException.class,CartChangedException.class}) public ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()==null?"Invalid request":e.getMessage()));}
 @ExceptionHandler(StripeException.class) public ResponseEntity<?> stripeError(StripeException e){return ResponseEntity.status(502).body(Map.of("error","Stripe could not complete the request. Check test-account setup and Stripe request logs."));}
}
