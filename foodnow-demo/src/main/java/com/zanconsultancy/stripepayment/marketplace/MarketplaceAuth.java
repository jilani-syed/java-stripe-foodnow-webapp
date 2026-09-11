package com.zanconsultancy.stripepayment.marketplace;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static com.zanconsultancy.stripepayment.marketplace.MarketplaceStore.*;
@Service
public class MarketplaceAuth {
 private final MarketplaceStore store;
 public MarketplaceAuth(MarketplaceStore store){this.store=store;}
 public String portal(HttpServletRequest req){String p=req.getHeader("X-FoodNow-Portal");return p==null?"diner":p;}
 public HttpSession session(HttpServletRequest req){HttpSession s=req.getSession(true);s.setMaxInactiveInterval(60*60*8);synchronized(s){if(s.getAttribute("csrf")==null)s.setAttribute("csrf",UUID.randomUUID().toString());if(s.getAttribute("guest")==null)s.setAttribute("guest",MarketplaceStore.id("guest"));}return s;}
 public String csrf(HttpServletRequest req){return (String)session(req).getAttribute("csrf");}
 public void verifyCsrf(HttpServletRequest req){if(!csrf(req).equals(req.getHeader("X-CSRF-Token")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Session expired. Refresh this page and try again.");}
 public User user(HttpServletRequest req){String id=(String)session(req).getAttribute("user:"+portal(req));return id==null?null:store.read(d->d.users.get(id));}
 public User require(HttpServletRequest req,String... roles){User u=user(req);if(u==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please sign in to this workspace.");if(!Set.of(roles).contains(u.role))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This workspace is not available to this account.");return u;}
 public String buyer(HttpServletRequest req){if(!portal(req).equals("diner"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Use the diner checkout.");User u=user(req);if(u!=null&&!u.role.equals("diner"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Diner account required.");return u==null?(String)session(req).getAttribute("guest"):u.id;}
 public boolean canView(User u,String guest,Order o){if(u==null)return o.buyerId.equals(guest);return switch(u.role){case "operations"->true;case "restaurant"->Objects.equals(u.entityId,o.restaurantId);case "courier"->Objects.equals(u.entityId,o.courierId);case "diner"->u.id.equals(o.buyerId);default->false;};}
 public Map<String,Object> publicUser(User u){if(u==null)return Map.of();return Map.of("id",u.id,"name",u.name,"email",u.email,"role",u.role,"entityId",u.entityId==null?"":u.entityId);}
 public void login(HttpServletRequest req,User u){HttpSession s=session(req);req.changeSessionId();s.setAttribute("user:"+u.role,u.id);if(u.role.equals("diner")){String guest=(String)s.getAttribute("guest");store.tx(d->{d.orders.values().stream().filter(o->guest.equals(o.buyerId)).forEach(o->o.buyerId=u.id);return null;});}}
}
