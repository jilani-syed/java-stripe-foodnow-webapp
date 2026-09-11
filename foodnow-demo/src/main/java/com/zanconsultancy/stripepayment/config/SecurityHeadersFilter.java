package com.zanconsultancy.stripepayment.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {
  private static final Set<String> NO_STORE = Set.of("/checkout", "/success", "/create-payment-intent");
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    String host=request.getServerName();
    if (!Set.of("localhost","127.0.0.1","[::1]","::1").contains(host)) {response.sendError(403);return;}
    if (!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) && !request.getRequestURI().equals("/webhook")) {
      String origin=request.getHeader("Origin");
      String expected=request.getScheme()+"://"+request.getHeader("Host");
      if ((origin!=null && !origin.equals(expected)) || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {response.sendError(403);return;}
    }
    if(request.getRequestURI().startsWith("/api/"))response.setHeader("Cache-Control","no-store");
    String requestId = request.getHeader("X-Request-ID");
    if (requestId == null || requestId.isBlank() || requestId.length() > 100) requestId = UUID.randomUUID().toString();
    response.setHeader("X-Request-ID", requestId);
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    if (request.isSecure()) response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    if (NO_STORE.contains(request.getRequestURI())) response.setHeader("Cache-Control", "no-store");
    chain.doFilter(request, response);
  }
}
