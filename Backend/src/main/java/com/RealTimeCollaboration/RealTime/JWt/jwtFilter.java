package com.RealTimeCollaboration.RealTime.JWt;

import com.RealTimeCollaboration.RealTime.redis.refreshTokenServiceRedis;
import com.RealTimeCollaboration.RealTime.service.userDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import io.jsonwebtoken.ExpiredJwtException;

import java.io.IOException;
//@EnableWebSecurity
@Component
//@Configuration
public class jwtFilter extends OncePerRequestFilter {
    @Autowired
    private jwtService jwtService;
    @Autowired
    userDetailsService userDetailsService;
    @Autowired
    refreshTokenServiceRedis refreshTokenServiceRedis;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

//        String authHeader = request.getHeader("Authorization");
//        if (authHeader==null || !authHeader.startsWith("Bearer "))
//        {
//
//            filterChain.doFilter(request,response);
//            return;
//        }
//        String token = authHeader.substring(7);
//
//
//
//        if (refreshTokenServiceRedis.isBlackListed(token))
//        {
//            response.setStatus(401);
//            return;
//        }
        String path = request.getRequestURI();


        if (
                path.equals("/refresh") ||
                        path.equals("/login") || path.equals("/actuator/health")
        ) {
            filterChain.doFilter(request, response);
            return;
        }
        System.out.println(
                request.getMethod() +
                        " " +
                        request.getRequestURI()
        );

        System.out.println(
                "QUERY = " +
                        request.getQueryString()
        );
//        if(request.getRequestURI().equals("/logout"))
//        {
//            filterChain.doFilter(request,response);
//            return;
//        }
        System.out.println(
                "PATH = " +
                        request.getRequestURI()
        );
        String token = null;
        String username = null;

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }



        System.out.println("JWT FILTER HIT");
        if (token == null)
        {
            Cookie [] cookies = request.getCookies();
            if (cookies != null)
            {
                for (Cookie cookie : cookies)
                {
                    System.out.println(
                            cookie.getName() +
                                    " = " +
                                    cookie.getValue()
                    );

                    if ("accessToken".equals(cookie.getName()))
                    {
                        token = cookie.getValue();

                        System.out.println(
                                "ACCESS TOKEN FOUND"
                        );

                        break;
                    }
                }
            }
        }

        System.out.println(
                "TOKEN = " + token
        );
        try{
            if (token!=null)
            {
                username = jwtService.extractUsername(token);
                if (username!=null && SecurityContextHolder.getContext().getAuthentication()==null)
                {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (jwtService.validateToken(token,userDetails))
                    {

                        UsernamePasswordAuthenticationToken token1 = new UsernamePasswordAuthenticationToken(userDetails,null,userDetails.getAuthorities());

                        token1.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(token1);
                    }
                }
            }
        } catch (ExpiredJwtException e) {

            System.out.println("TOKEN EXPIRED");

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Token expired"
            );

            return;
        }






        filterChain.doFilter(request,response);
    }
}
