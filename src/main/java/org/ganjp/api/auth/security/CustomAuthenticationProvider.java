package org.ganjp.api.auth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.auth.token.LoginRequest;
import org.ganjp.api.auth.user.AccountStatus;
import org.ganjp.api.auth.user.User;
import org.ganjp.api.auth.user.UserRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginFailureService loginFailureService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String credential = authentication.getName();
        String password = authentication.getCredentials().toString();

        // Extract login request from authentication details
        LoginRequest loginRequest = extractLoginRequest(authentication);

        // Find the user based on provided credentials
        Optional<User> userOptional = findUser(credential, loginRequest);

        // If user not found, throw generic error to prevent username enumeration
        if (userOptional.isEmpty()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = userOptional.get();

        // Check account status BEFORE password verification
        // This prevents attackers from confirming passwords on locked accounts
        validateAccountStatus(user);

        // Verify password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            // Record login failure for known user with wrong password
            loginFailureService.recordFailedLogin(user);
            throw new BadCredentialsException("Invalid credentials");
        }

        // Return the authenticated token with authorities
        return new UsernamePasswordAuthenticationToken(
                user,
                password,
                user.getAuthorities()
        );
    }

    private LoginRequest extractLoginRequest(Authentication authentication) {
        if (authentication.getDetails() instanceof LoginRequest request) {
            return request;
        }
        return null;
    }
    
    private Optional<User> findUser(String credential, LoginRequest loginRequest) {
        if (loginRequest != null) {
            // First validate that only one login method is provided
            if (!loginRequest.isValidLoginMethod()) {
                throw new BadCredentialsException("Please provide exactly one login method: username, email, or mobile number");
            }
            
            if (loginRequest.getEmail() != null && !loginRequest.getEmail().isEmpty()) {
                // Email login
                return userRepository.findByEmail(loginRequest.getEmail());
            } else if (loginRequest.getMobileNumber() != null && !loginRequest.getMobileNumber().isEmpty()
                    && loginRequest.getMobileCountryCode() != null && !loginRequest.getMobileCountryCode().isEmpty()) {
                // Mobile login
                return userRepository.findByMobileCountryCodeAndMobileNumber(
                        loginRequest.getMobileCountryCode(), loginRequest.getMobileNumber());
            } else {
                // Username login
                return userRepository.findByUsername(loginRequest.getUsername());
            }
        } else {
            // Default to username login if no loginRequest is provided
            Optional<User> userOptional = userRepository.findByUsername(credential);
            
            // If not found, try with email as a fallback (if it looks like an email)
            if (userOptional.isEmpty() && credential != null && credential.contains("@")) {
                userOptional = userRepository.findByEmail(credential);
            }
            return userOptional;
        }
    }

    private void validateAccountStatus(User user) {
        if (user.getAccountStatus() == AccountStatus.pending_verification) {
            throw new BadCredentialsException("Please verify your email before logging in");
        }

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is not active");
        }
        
        if (!user.isAccountNonLocked()) {
            throw new BadCredentialsException("Account is locked");
        }

        if (!user.isAccountNonExpired()) {
            throw new BadCredentialsException("Account has expired");
        }
        
        if (!user.isCredentialsNonExpired()) {
            throw new BadCredentialsException("Credentials have expired");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
