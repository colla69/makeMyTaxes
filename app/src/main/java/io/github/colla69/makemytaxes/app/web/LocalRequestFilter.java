package io.github.colla69.makemytaxes.app.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binding to 127.0.0.1 does not stop the browser of the user from sending a request for another
 * site. The Host check stops DNS rebinding, where a foreign site reads this app under its own name.
 * The Origin check stops a foreign page from sending a form to this app.
 */
@Component
class LocalRequestFilter extends OncePerRequestFilter {

  private static final Set<String> LOCAL_NAMES = Set.of("127.0.0.1", "localhost");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!LOCAL_NAMES.contains(request.getServerName())
        || !isSameOrigin(request.getHeader("Origin"), request)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    chain.doFilter(request, response);
  }

  /** A browser sends Origin with a form from another site, so a missing header is same-site. */
  private static boolean isSameOrigin(String origin, HttpServletRequest request) {
    if (origin == null) {
      return true;
    }
    try {
      URI uri = URI.create(origin);
      int originPort = uri.getPort() == -1 ? 80 : uri.getPort();
      return request.getServerName().equals(uri.getHost()) && request.getServerPort() == originPort;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
