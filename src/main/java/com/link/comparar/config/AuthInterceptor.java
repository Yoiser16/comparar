package com.link.comparar.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // Obtener la sesión
        Object autenticado = request.getSession().getAttribute("historicoAutenticado");

        // Si no está autenticado, redirigir a login
        if (autenticado == null || !autenticado.equals(true)) {
            response.sendRedirect("/historico/login");
            return false;
        }

        return true;
    }
}
