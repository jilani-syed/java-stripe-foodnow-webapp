package com.zanconsultancy.stripepayment.marketplace;
import static org.junit.jupiter.api.Assertions.*;
import static com.zanconsultancy.stripepayment.marketplace.MarketplaceStore.*;
import com.stripe.Stripe;
import com.zanconsultancy.stripepayment.config.StripeProperties;
import com.zanconsultancy.stripepayment.service.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class MarketplaceTests {
 @TempDir Path temp;
 MarketplaceStore store; MarketplaceController controller;
 StripeProperties keys=new StripeProperties("pk_test_fixture","sk_test_fixture","whsec_fixture");
 @BeforeEach void setup()throws Exception{
  var env=new MockEnvironment().withProperty("foodnow.mode","stripe").withProperty("foodnow.marketplace-store",temp.resolve("marketplace.json").toString());
  store=new MarketplaceStore(new ObjectMapper(),env);store.initialize();
  controller=new MarketplaceController(store,new MarketplaceAuth(store),new MarketplaceStripe(keys,new StripePaymentService(keys)),keys,new StripeCustomerService(keys),new CheckoutService(new ObjectMapper()),env);
  store.tx(d->{Order o=new Order();o.id="ord_signed";o.restaurantId="basil";o.buyerId="guest_x";o.customerId="cus_x";o.paymentIntentId="pi_signed";o.amount=2198;o.currency="usd";o.restaurantEarnings=1280;o.courierEarnings=399;o.platformEarnings=519;o.paymentStatus="requires_payment_method";d.orders.put(o.id,o);return null;});
 }
 byte[] event(String id,String status,long amount,boolean live,long created){return ("{\"id\":\""+id+"\",\"object\":\"event\",\"api_version\":\""+Stripe.API_VERSION+"\",\"created\":"+created+",\"livemode\":"+live+",\"type\":\"payment_intent."+(status.equals("requires_payment_method")?"payment_failed":status)+"\",\"data\":{\"object\":{\"id\":\"pi_signed\",\"object\":\"payment_intent\",\"amount\":"+amount+",\"currency\":\"usd\",\"customer\":\"cus_x\",\"status\":\""+status+"\",\"metadata\":{\"order_id\":\"ord_signed\"}}}}").getBytes(StandardCharsets.UTF_8);}
 String sign(byte[] bytes)throws Exception{long now=System.currentTimeMillis()/1000;Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(keys.webhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return "t="+now+",v1="+HexFormat.of().formatHex(mac.doFinal((now+"."+new String(bytes,StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));}
 @Test void signedPaymentAndDuplicateProduceOneBalancedJournal()throws Exception{
  byte[] e=event("evt_success","succeeded",2198,false,100);assertEquals(200,controller.webhook(e,sign(e)).getStatusCode().value());assertEquals(200,controller.webhook(e,sign(e)).getStatusCode().value());
  var d=store.read(x->x);assertEquals("placed",d.orders.get("ord_signed").stage);assertEquals(4,d.ledger.size());assertEquals(1,d.orders.get("ord_signed").processedEventIds.size());assertEquals(0,d.ledger.stream().mapToLong(x->x.debit-x.credit).sum());
 }
 @Test void delayedFailureCannotDowngradePaymentOrUndoJournal()throws Exception{
  for(byte[] e:List.of(event("evt_success","succeeded",2198,false,100),event("evt_old","requires_payment_method",2198,false,90),event("evt_new","requires_payment_method",2198,false,110)))assertEquals(200,controller.webhook(e,sign(e)).getStatusCode().value());
  assertEquals("succeeded",store.read(d->d.orders.get("ord_signed").paymentStatus));assertEquals(4,(int)store.read(d->d.ledger.size()));
 }
 @Test void invalidSignedPayloadCannotCreateAccountingEntries()throws Exception{
  byte[] good=event("evt_good","succeeded",2198,false,100);assertEquals(400,controller.webhook(good,null).getStatusCode().value());assertEquals(400,controller.webhook(good,"t=1,v1=wrong").getStatusCode().value());
  for(byte[] e:List.of(event("evt_amount","succeeded",1,false,100),event("evt_live","succeeded",2198,true,100)))assertEquals(400,controller.webhook(e,sign(e)).getStatusCode().value());
  assertEquals(0,(int)store.read(d->d.ledger.size()));assertEquals("payment_pending",store.read(d->d.orders.get("ord_signed").stage));
 }
 @Test void unbalancedTransactionRollsBackDomainAndLedger(){
  assertThrows(IllegalStateException.class,()->store.tx(d->{d.orders.get("ord_signed").stage="delivered";journal(d,d.orders.get("ord_signed"),"bad","Invalid",List.of(entry("cash","foodnow",100,0)));return null;}));
  assertEquals("payment_pending",store.read(d->d.orders.get("ord_signed").stage));assertEquals(0,(int)store.read(d->d.ledger.size()));
 }
 @Test void returnedSnapshotCannotMutateStoredAccount(){
  var d=store.read(x->x);d.restaurants.get("basil").approval="rejected";assertEquals("approved",store.read(x->x.restaurants.get("basil").approval));
 }
 @Test void passwordHashesAreSaltedAndVerifiable(){String a=Passwords.hash("ExamplePassword2026"),b=Passwords.hash("ExamplePassword2026");assertNotEquals(a,b);assertTrue(Passwords.matches("ExamplePassword2026",a));assertFalse(Passwords.matches("wrong",a));}

 @Test void evidencePersistsOnlyForOperationsAndDoesNotReleaseOrder()throws Exception{
  var env=new MockEnvironment().withProperty("foodnow.mode","stripe");
  var fake=new MarketplaceStripe(keys,new StripePaymentService(keys)){
   @Override public List<Map<String,Object>> evidence(String intent){assertEquals("pi_signed",intent);return List.of(Map.of("id","ch_fixture","type","blocked","riskLevel","highest"));}
  };
  var c=new MarketplaceController(store,new MarketplaceAuth(store),fake,keys,new StripeCustomerService(keys),new CheckoutService(new ObjectMapper()),env);
  var request=new org.springframework.mock.web.MockHttpServletRequest();request.addHeader("X-FoodNow-Portal","operations");request.getSession().setAttribute("user:operations","ops");
  c.evidence("ord_signed",request);
  assertEquals("blocked",store.read(d->d.orders.get("ord_signed").stripeEvidence.get(0).get("type")));
  assertEquals("payment_pending",store.read(d->d.orders.get("ord_signed").stage));assertEquals(0,(int)store.read(d->d.ledger.size()));
  request.removeHeader("X-FoodNow-Portal");request.addHeader("X-FoodNow-Portal","restaurant");request.getSession().setAttribute("user:restaurant","owner_basil");
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->c.evidence("ord_signed",request));
  var view=(Map<?,?>)c.order("ord_signed",request);assertFalse(view.containsKey("stripeEvidence"));
 }
}
