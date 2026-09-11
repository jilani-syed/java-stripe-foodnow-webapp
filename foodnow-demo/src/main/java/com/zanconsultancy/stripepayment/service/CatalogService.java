package com.zanconsultancy.stripepayment.service;
import com.zanconsultancy.stripepayment.model.Dish;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class CatalogService {
 private final List<Dish> dishes=List.of(
  new Dish("1","Wood-fired margherita","Basil & Brick","/images/pizza.jpg",1600,"usd"),
  new Dish("2","Double smash burger","Bun Theory","/images/burger.jpg",1450,"usd"),
  new Dish("3","Harvest grain bowl","The Green Table","/images/bowl.jpg",1300,"usd"),
  new Dish("4","Spicy honey pizza","Basil & Brick","/images/pizza.jpg",1800,"usd"),
  new Dish("5","Classic cheeseburger","Bun Theory","/images/burger.jpg",1250,"usd"),
  new Dish("6","Avocado garden bowl","The Green Table","/images/bowl.jpg",1450,"usd"));
 public List<Dish> all(){return dishes;}
 public Dish require(String id){return dishes.stream().filter(d->d.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown dish"));}
}
