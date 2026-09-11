package com.zanconsultancy.stripepayment.marketplace;

import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import java.util.*;
import com.zanconsultancy.stripepayment.config.StripeProperties;
import com.zanconsultancy.stripepayment.service.StripePaymentService;
import org.springframework.stereotype.Service;

@Service
public class MarketplaceStripe {
 private final StripeProperties keys;
 private final StripePaymentService payments;
 public MarketplaceStripe(StripeProperties keys,StripePaymentService payments){this.keys=keys;this.payments=payments;}
 private RequestOptions opts(String key){var b=RequestOptions.builder().setApiKey(keys.secretKey());if(key!=null)b.setIdempotencyKey(key);return b.build();}
 public Map<String,Object> platform()throws StripeException{
  Account a=Account.retrieve(opts(null));
  Map<String,Object> result=new LinkedHashMap<>();result.put("id",a.getId());result.put("country",a.getCountry());result.put("name",a.getBusinessProfile()==null?null:a.getBusinessProfile().getName());result.put("chargesEnabled",a.getChargesEnabled());result.put("payoutsEnabled",a.getPayoutsEnabled());return result;
 }
 public List<Map<String,Object>> evidence(String intent)throws StripeException{
  var charges=Charge.list(ChargeListParams.builder().setPaymentIntent(intent).setLimit(100L).build(),opts(null));
  List<Map<String,Object>> rows=new ArrayList<>();
  for(Charge c:charges.getData()){
   if(Boolean.TRUE.equals(c.getLivemode()))throw new IllegalArgumentException("Test charges only");
   Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("status",c.getStatus());r.put("amount",c.getAmount());r.put("created",c.getCreated());r.put("failureCode",c.getFailureCode());r.put("failureMessage",c.getFailureMessage());r.put("disputed",c.getDisputed());r.put("refunded",c.getRefunded());
   if(c.getOutcome()!=null){var o=c.getOutcome();r.put("riskLevel",o.getRiskLevel());r.put("riskScore",o.getRiskScore());r.put("type",o.getType());r.put("reason",o.getReason());r.put("sellerMessage",o.getSellerMessage());}
   if(c.getPaymentMethodDetails()!=null)r.put("method",c.getPaymentMethodDetails().getType());rows.add(r);
  }return rows;
 }
 public PaymentIntent intent(MarketplaceStore.Order o)throws StripeException{return o.paymentIntentId==null?payments.create(o):payments.retrieve(o.paymentIntentId);}
 public PaymentIntent retrieve(String id)throws StripeException{return payments.retrieve(id);}
 public Account account(String id)throws StripeException{return Account.retrieve(id,opts(null));}
 public Account createAccount(String entity,String email)throws StripeException{
  var p=AccountCreateParams.builder().setType(AccountCreateParams.Type.EXPRESS).setCountry("US").setEmail(email)
   .setCapabilities(AccountCreateParams.Capabilities.builder().setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build()).build())
   .putMetadata("foodnow_partner",entity).build();
  return Account.create(p,opts("foodnow_partner_"+entity));
 }
 public String onboarding(String account,String base,String portal)throws StripeException{
  return AccountLink.create(AccountLinkCreateParams.builder().setAccount(account).setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
   .setRefreshUrl(base+"/#"+portal+"/onboarding?refresh=1").setReturnUrl(base+"/#"+portal+"/onboarding?returned=1").build(),opts(null)).getUrl();
 }
 public String transfer(MarketplaceStore.Order o,String role,String destination,long amount)throws StripeException{
  PaymentIntent pi=payments.retrieve(o.paymentIntentId);
  if(Boolean.TRUE.equals(pi.getLivemode())||!"succeeded".equals(pi.getStatus())||pi.getAmount()!=o.amount)throw new IllegalArgumentException("Payment is not eligible for transfer");
  var params=TransferCreateParams.builder().setAmount(amount).setCurrency(o.currency).setDestination(destination).setSourceTransaction(pi.getLatestCharge()).setTransferGroup(o.id).putMetadata("foodnow_order",o.id).build();
  return Transfer.create(params,opts("foodnow_"+o.id+"_"+role)).getId();
 }
 public BalanceTransaction fee(String paymentIntent)throws StripeException{
  var pi=payments.retrieve(paymentIntent);if(pi.getLatestCharge()==null)return null;
  Charge charge=Charge.retrieve(pi.getLatestCharge(),ChargeRetrieveParams.builder().addExpand("balance_transaction").build(),opts(null));
  return charge.getBalanceTransactionObject();
 }
}
