package com.example.springjte.security

import com.example.springjte.model.User
import com.example.springjte.repository.UserRepository
import com.example.springjte.service.ApiKeyService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.savedrequest.HttpSessionRequestCache

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val repo: UserRepository,
    private val apiKeyService: ApiKeyService
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(): UserDetailsService =
        UserDetailsService { email ->
            val user = repo.findByEmail(email)
                ?: throw UsernameNotFoundException("No user found for email: $email")
            toUserDetails(user)
        }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                    // SaaS management pages — admin only
                    .requestMatchers("/saas/**").hasRole("ADMIN")
                    // User management mutations — admin only
                    .requestMatchers("/users/*/edit", "/users/*/update", "/users/*/delete").hasRole("ADMIN")
                    // Book catalog — any authenticated member, matching the ASP.NET Core
                    // "MemberOnly" policy the original BookController used
                    .requestMatchers("/books/**", "/api/books/**").authenticated()
                    // RAG chat pages and API — any authenticated user
                    .requestMatchers("/rag/**").authenticated()
                    .requestMatchers("/api/rag/**").authenticated()
                    // SaaS API endpoints — admin only for mutations
                    .requestMatchers(HttpMethod.POST, "/api/users", "/api/users/*").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/users/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/api-keys", "/api/api-keys/*").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/api-keys/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/api-keys/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/webhooks", "/api/webhooks/*").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/webhooks/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/webhooks/**").hasRole("ADMIN")
                    // Read-only SaaS API endpoints — any authenticated user
                    .requestMatchers(HttpMethod.GET, "/api/dashboard", "/api/usage/**", "/api/audit-logs/**").authenticated()
                    .anyRequest().authenticated()
            }
            .formLogin { form ->
                form
                    .loginPage("/login")
                    .successHandler(RoleAwareSuccessHandler())
                    .permitAll()
            }
            .logout { logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
            }
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
            }
        return http.build()
    }

    private fun toUserDetails(user: User): UserDetails =
        org.springframework.security.core.userdetails.User.builder()
            .username(user.email)
            .password(user.password)
            .roles(*rolesFor(user))
            .disabled(false)
            .build()

    private fun rolesFor(user: User): Array<String> =
        if (user.role?.contains("ADMIN") == true) arrayOf("ADMIN") else arrayOf("USER")
}

/**
 * Picks a landing page the freshly authenticated user is actually allowed to open.
 *
 * `/saas/dashboard` is admin-only, so hard-coding it as the default success URL
 * dropped every ROLE_USER account straight onto a 403 page immediately after a
 * perfectly valid login. Admins still land on the dashboard, everyone else on
 * the user list.
 *
 * A saved request (the protected page that triggered the login redirect) wins,
 * but only when that page is reachable by the user — otherwise a ROLE_USER who
 * typed an `/saas/...` URL first would bounce back into a 403 loop.
 *
 * The handler keeps no mutable state: [HttpSessionRequestCache] stores its data
 * in the session, so concurrent logins cannot interfere with each other.
 */
private class RoleAwareSuccessHandler : AuthenticationSuccessHandler {

    private val requestCache = HttpSessionRequestCache()

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val savedUrl = requestCache.getRequest(request, response)?.redirectUrl
        requestCache.removeRequest(request, response)

        val isAdmin = authentication.authorities.any { it.authority == ROLE_ADMIN }
        val home = if (isAdmin) ADMIN_HOME else USER_HOME

        val target = if (savedUrl != null && (isAdmin || !savedUrl.contains(ADMIN_AREA))) {
            savedUrl
        } else {
            request.contextPath + home
        }
        response.sendRedirect(target)
    }

    private companion object {
        const val ROLE_ADMIN = "ROLE_ADMIN"
        const val ADMIN_AREA = "/saas/"
        const val ADMIN_HOME = "/saas/dashboard"
        const val USER_HOME = "/users"
    }
}
