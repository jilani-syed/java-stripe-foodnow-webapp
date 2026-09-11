package com.zanconsultancy.stripepayment;
import static org.junit.jupiter.api.Assertions.*;
import com.zanconsultancy.stripepayment.config.*;
import com.zanconsultancy.stripepayment.controller.FoodNowController;
import com.zanconsultancy.stripepayment.model.*;
import com.zanconsultancy.stripepayment.service.*;
import com.stripe.Stripe;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.Path;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

class FoodNowFlowTests {
 @TempDir Path temp;
 OrderStore store; FoodNowController controller; StripeProperties stripe=new StripeProperties("pk_test_example","sk_test_example","whsec_test_fixture");
 @BeforeEach void setup() throws Exception {store=new OrderStore(new ObjectMapper(),new AppProperties(temp.resolve("orders.json")));store.initialize();controller=controller("rehearsal");}
 FoodNowController controller(String mode){var c=new CatalogService();return new FoodNowController(c,new OrderCalculator(c),new CheckoutService(new ObjectMapper()),store,new StripePaymentService(stripe),new StripeCustomerService(stripe),stripe,new MockEnvironment().withProperty("foodnow.mode",mode));}
 Map<?,?> create(String token,int quantity)throws Exception {return (Map<?,?>)controller.create(new FoodNowController.CheckoutRequest(token,List.of(new CartItem("1",quantity)),"alex@example.com"));}
 @Test void declineRecoveryAndRepeatedAllocationPreserveOneOrder()throws Exception {
  String token=UUID.randomUUID().toString();var first=create(token,1);String id=(String)first.get("orderId");
  assertEquals(2198L,first.get("amount"));assertEquals(id,create(token,1).get("orderId"));assertEquals(1,store.all().size());
  assertThrows(IllegalArgumentException.class,()->controller.allocate(id));
  controller.simulate(id,new FoodNowController.Scenario("requires_payment_method"));
  controller.simulate(id,new FoodNowController.Scenario("succeeded"));
  controller.allocate(id);var transfers=Map.copyOf(store.require(id).transfers);controller.allocate(id);
  assertEquals(transfers,store.require(id).transfers);assertEquals(2,transfers.size());
  var view=(Map<?,?>)controller.order(id);assertEquals(1280L,view.get("restaurantAmount"));assertEquals(399,view.get("courierAmount"));assertEquals(519L,view.get("platformAmount"));
  OrderStore reopened=new OrderStore(new ObjectMapper(),new AppProperties(temp.resolve("orders.json")));reopened.initialize();assertEquals(transfers,reopened.require(id).transfers);
 }
 @Test void changedCartCannotReuseToken()throws Exception {String token=UUID.randomUUID().toString();create(token,1);assertThrows(CartChangedException.class,()->create(token,2));}
 @Test void stripeModeDisablesSimulationAndReset(){var c=controller("stripe");assertThrows(IllegalArgumentException.class,()->c.simulate("x",new FoodNowController.Scenario("succeeded")));assertThrows(IllegalArgumentException.class,c::reset);}
 @Test void liveKeysAreRejected(){assertThrows(IllegalStateException.class,()->new StartupValidator(new StripeProperties("pk_live_bad","sk_live_bad",""),new MockEnvironment()));}
 @Test void stripeModeRequiresWebhookSecret(){assertThrows(IllegalStateException.class,()->new StartupValidator(new StripeProperties("pk_test_x","sk_test_x",""),new MockEnvironment().withProperty("foodnow.mode","stripe")));}
 @Test void earlyWebhookCannotBeOverwrittenByCreateResponse(){
  store.createOrGet("t","h",new OrderDraft(List.of(new OrderLine("1","Pizza",1,1600,1600)),1,2198,"usd"),"cus_x",()->"ord_x");
  store.applyStripeEvent("evt_early",20,"ord_x","pi_x","succeeded");store.attachPaymentIntent("ord_x","pi_x","requires_payment_method");
  assertEquals("succeeded",store.require("ord_x").paymentStatus);
 }
 byte[] event(long amount,boolean live){String json="{\"id\":\"evt_signed\",\"object\":\"event\",\"api_version\":\""+Stripe.API_VERSION+"\",\"created\":"+(System.currentTimeMillis()/1000)+",\"livemode\":"+live+",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_signed\",\"object\":\"payment_intent\",\"amount\":"+amount+",\"currency\":\"usd\",\"customer\":\"cus_x\",\"status\":\"succeeded\",\"metadata\":{\"order_id\":\"ord_signed\"}}}}";return json.getBytes(StandardCharsets.UTF_8);}
 String sign(byte[] bytes)throws Exception {long now=System.currentTimeMillis()/1000;Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(stripe.webhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return "t="+now+",v1="+HexFormat.of().formatHex(mac.doFinal((now+"."+new String(bytes,StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));}
 void signedOrder(){store.createOrGet("token","hash",new OrderDraft(List.of(new OrderLine("1","Pizza",1,1600,1600)),1,2198,"usd"),"cus_x",()->"ord_signed");store.attachPaymentIntent("ord_signed","pi_signed","requires_payment_method");}
 @Test void signedWebhookConfirmsAndDeduplicates()throws Exception {signedOrder();var c=controller("stripe");byte[] e=event(2198,false);assertEquals(200,c.webhook(e,sign(e)).getStatusCode().value());assertEquals(200,c.webhook(e,sign(e)).getStatusCode().value());assertEquals("succeeded",store.require("ord_signed").paymentStatus);assertEquals(1,store.require("ord_signed").processedEventIds.size());}
 @Test void rejectsUnsignedLiveAndMismatchedWebhook()throws Exception {signedOrder();var c=controller("stripe");byte[] valid=event(2198,false),wrong=event(1,false),live=event(2198,true);assertEquals(400,c.webhook(valid,null).getStatusCode().value());assertEquals(400,c.webhook(valid,"t=1,v1=bad").getStatusCode().value());assertEquals(400,c.webhook(wrong,sign(wrong)).getStatusCode().value());assertEquals(400,c.webhook(live,sign(live)).getStatusCode().value());assertEquals("requires_payment_method",store.require("ord_signed").paymentStatus);}
}
