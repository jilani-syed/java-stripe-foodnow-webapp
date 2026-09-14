package com.zanconsultancy.stripepayment.marketplace;

import com.zanconsultancy.stripepayment.model.OrderRecord;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Single-process demo store: domain changes and accounting journals commit together. */
@Service
public class MarketplaceStore {
  private final ObjectMapper mapper;
  private final Path path;
  private final boolean rehearsal;
  private Data data = new Data();

  public MarketplaceStore(ObjectMapper mapper, Environment env) {
    this.mapper = mapper;
    rehearsal = !"stripe".equals(env.getProperty("foodnow.mode", "rehearsal"));
    path = Path.of(env.getProperty("foodnow.marketplace-store", "data/marketplace-v2-" + (rehearsal ? "rehearsal" : "stripe") + ".json")).toAbsolutePath();
  }
  @PostConstruct public synchronized void initialize() throws Exception {
    Files.createDirectories(path.getParent());
    if (Files.exists(path)) data = mapper.readValue(path.toFile(), Data.class);
    else { seed(); persist(); }
  }
  public synchronized <T> T read(Function<Data,T> fn) { return fn.apply(mapper.readValue(mapper.writeValueAsBytes(data), Data.class)); }
  public synchronized <T> T tx(Function<Data,T> fn) {
    byte[] before = mapper.writeValueAsBytes(data);
    try { T result = fn.apply(data); validateLedger(); persist(); return result; }
    catch (Exception ex) { data = mapper.readValue(before, Data.class); if(ex instanceof RuntimeException runtime)throw runtime; throw new IllegalStateException("Could not save marketplace data", ex); }
  }
  private void validateLedger() {
    Map<String,Long> balances = new HashMap<>();
    for (LedgerEntry e : data.ledger) {
      if(e.debit<0 || e.credit<0 || (e.debit>0 && e.credit>0))throw new IllegalStateException("Invalid journal entry");
      balances.merge(e.journal+":"+e.currency, e.debit-e.credit, Long::sum);
    }
    if(balances.values().stream().anyMatch(n->n!=0))throw new IllegalStateException("Unbalanced journal");
  }
  private void persist() throws Exception {
    Path temp = path.resolveSibling(path.getFileName()+".tmp");
    mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(),data);
    try { Files.setPosixFilePermissions(temp, Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE)); } catch(UnsupportedOperationException ignored){}
    try { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
    catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
  }
  public static String id(String prefix){return prefix+"_"+UUID.randomUUID();}
  public static void history(Order o,String state,String actor,String note){o.history.add(new History(Instant.now().toString(),state,actor,note));}
  public static void journal(Data d,Order o,String key,String description,List<LedgerEntry> entries){
    if(d.ledger.stream().anyMatch(e->e.journal.equals(key)))return;
    for(LedgerEntry e:entries){e.id=id("entry");e.currency=o.currency;e.journal=key;e.orderId=o.id;e.restaurantId=o.restaurantId;e.createdAt=Instant.now().toString();e.description=description;d.ledger.add(e);}
  }
  public static LedgerEntry entry(String account,String partner,long debit,long credit){var e=new LedgerEntry();e.account=account;e.partnerId=partner;e.debit=debit;e.credit=credit;return e;}
  public static void paymentConfirmed(Data d,Order o){
    if("payment_pending".equals(o.stage)){o.stage="placed";history(o,"placed","payment","Payment confirmed; restaurant can accept the order.");}
    o.fulfillmentStatus="ready_for_fulfillment";
    journal(d,o,"sale:"+o.id,"Customer payment confirmed",List.of(entry("stripe_clearing","foodnow",o.amount,0),entry("restaurant_payable",o.restaurantId,0,o.restaurantEarnings),entry("courier_payable",o.courierId==null?"unassigned":o.courierId,0,o.courierEarnings),entry("platform_revenue","foodnow",0,o.platformEarnings)));
  }
  private void seed(){
    seedUser("ops","FoodNow Operations","ops@foodnow.demo","operations",null);
    seedUser("diner","Alex Morgan","diner@foodnow.demo","diner",null);
    String[][] restaurants={{"basil","Basil & Brick","Pizza","pizza","basil","Wood-fired pizza, fresh from the neighborhood oven."},{"bun","Bun Theory","Burgers","burger","bun","Smash burgers, crisp edges and our signature house sauce."},{"green","The Green Table","Healthy","bowl","green","Seasonal greens, hearty grains and bright dressings."},{"spice","Spice Route","Indian","curry","spice","Comforting curries, fragrant rice and warm naan."},{"maki","Maki House","Japanese","sushi","maki","Fresh sushi, carefully rolled and made to order."}};
    for(String[] row:restaurants){
      Restaurant r=new Restaurant();r.id=row[0];r.name=row[1];r.cuisine=row[2];r.image="/images/"+row[3]+".jpg";r.description=row[5];r.address="River North, Chicago";r.ownerId="owner_"+row[0];r.approval="approved";r.onboardingComplete=rehearsal;r.transfersReady=rehearsal;r.payoutsEnabled=false;r.createdAt=Instant.now().toString();data.restaurants.put(r.id,r);
      seedUser(r.ownerId,row[1]+" Owner",row[4]+"@foodnow.demo","restaurant",r.id);
    }
    seedItem("1","basil","Wood-fired margherita","San Marzano tomato, mozzarella and fresh basil.",1600);
    seedItem("4","basil","Spicy honey pizza","Mozzarella, chili honey and a little extra heat.",1800);
    seedItem("2","bun","Double smash burger","Two patties, brioche bun and house sauce.",1450);
    seedItem("5","bun","Classic cheeseburger","A classic with melted cheddar and crisp lettuce.",1250);
    seedItem("3","green","Harvest grain bowl","Seasonal greens, grains and house dressing.",1300);
    seedItem("6","green","Avocado garden bowl","Avocado, mixed greens and lemon dressing.",1450);
    seedItem("7","spice","Butter chicken bowl","Tomato cream curry with fragrant basmati rice.",1700);
    seedItem("8","spice","Chickpea masala","Chickpeas, warming spices and basmati rice.",1450);
    seedItem("9","maki","Salmon maki set","Fresh salmon rolls with ginger and wasabi.",1900);
    seedItem("10","maki","Garden sushi set","Avocado and cucumber rolls with edamame.",1550);
    for(int i=1;i<=2;i++){
      Courier c=new Courier();c.id="courier_"+i;c.userId=c.id;c.name=i==1?"Sam Rivera":"Jamie Chen";c.vehicle=i==1?"Bicycle":"Car";c.approval="approved";c.onboardingComplete=rehearsal;c.transfersReady=rehearsal;c.createdAt=Instant.now().toString();data.couriers.put(c.id,c);
      seedUser(c.id,c.name,i==1?"courier@foodnow.demo":"jamie@foodnow.demo","courier",c.id);
    }
  }
  private void seedUser(String id,String name,String email,String role,String entity){User u=new User();u.id=id;u.name=name;u.email=email;u.role=role;u.entityId=entity;u.password=Passwords.hash("FoodNowDemo!2026");data.users.put(id,u);}
  private void seedItem(String id,String restaurant,String title,String description,long amount){Item i=new Item();i.id=id;i.restaurantId=restaurant;i.title=title;i.description=description;i.amount=amount;i.image=data.restaurants.get(restaurant).image;data.items.put(id,i);}
  public static class Data {public int version=2;public Map<String,User> users=new LinkedHashMap<>();public Map<String,Restaurant> restaurants=new LinkedHashMap<>();public Map<String,Courier> couriers=new LinkedHashMap<>();public Map<String,Item> items=new LinkedHashMap<>();public Map<String,Order> orders=new LinkedHashMap<>();public List<LedgerEntry> ledger=new ArrayList<>();}
  public static class User {public String id,name,email,role,entityId,password;}
  public static class Restaurant {public String id,name,cuisine,description,address,image,ownerId;public String approval="pending",reviewNote="",stripeAccount="",createdAt;public boolean onboardingComplete,transfersReady,payoutsEnabled;public List<String> requirements=new ArrayList<>();public int commissionBps=2000;public String connectAttemptAt;}
  public static class Courier {public String id,userId,name,vehicle;public String approval="pending",reviewNote="",stripeAccount="",createdAt;public boolean onboardingComplete,transfersReady,payoutsEnabled;public List<String> requirements=new ArrayList<>();public String connectAttemptAt;}
  public static class Item {public String id,restaurantId,title,description,image;public long amount;public boolean available=true;}
  public static class Order extends OrderRecord {public List<Map<String,Object>> stripeEvidence=new ArrayList<>();public String evidenceCheckedAt;public String buyerId,buyerName,email,address,restaurantId,restaurantName,courierId;public String stage="payment_pending",mode,paymentMethod="",transferAttemptAt;public long subtotal,restaurantEarnings,courierEarnings=399,platformEarnings,processingFee;public boolean feeReconciled;public List<History> history=new ArrayList<>();public Map<String,String> transferDestinations=new HashMap<>();}
  public record History(String at,String state,String actor,String note){}
  public static class LedgerEntry {public String currency="usd";public String id,journal,orderId,restaurantId,partnerId,account,description,createdAt;public long debit,credit;}
}
