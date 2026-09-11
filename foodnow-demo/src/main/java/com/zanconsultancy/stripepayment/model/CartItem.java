package com.zanconsultancy.stripepayment.model;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
public record CartItem(@NotBlank String dishId, @Min(1) @Max(10) int quantity) {}
