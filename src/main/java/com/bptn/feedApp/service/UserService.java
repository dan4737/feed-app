package com.bptn.feedApp.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import com.bptn.feedApp.repository.UserRepository;
import com.bptn.feedApp.exception.domain.EmailExistsException;
import com.bptn.feedApp.exception.domain.UsernameExistException;
import com.bptn.feedApp.jpa.User;
import java.util.Optional;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import com.bptn.feedApp.exception.domain.UserNotFoundException;
import com.bptn.feedApp.exception.domain.EmailNotVerifiedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.bptn.feedApp.provider.ResourceProvider;
import com.bptn.feedApp.security.JwtService;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import org.springframework.http.HttpHeaders;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.util.StringUtils;
import com.bptn.feedApp.jpa.Profile;

@Service
public class UserService {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    UserRepository userRepository;

    @Autowired
    EmailService emailService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    JwtService jwtService;

    @Autowired
    ResourceProvider provider;

    public List<User> listUsers() {
        return this.userRepository.findAll();
    }

    public Optional<User> findByUsername(String username) {
        return this.userRepository.findByUsername(username);
    }

//	public void createUser(User user) {
//		this.userRepository.save(user);
//	}

    public User signup(User user){
        user.setUsername(user.getUsername().toLowerCase());
        user.setEmailId(user.getEmailId().toLowerCase());
        this.validateUsernameAndEmail(user.getUsername(), user.getEmailId());
        user.setEmailVerified(false);
        user.setPassword(this.passwordEncoder.encode(user.getPassword()));
        user.setCreatedOn(Timestamp.from(Instant.now()));
        this.userRepository.save(user);
        this.emailService.sendVerificationEmail(user);
        return user;
    }

    private void validateUsernameAndEmail(String username, String emailId) {

        this.userRepository.findByUsername(username).ifPresent(u -> {
            throw new UsernameExistException(String.format("Username already exists, %s", u.getUsername()));
        });

        this.userRepository.findByEmailId(emailId).ifPresent(u -> {
            throw new EmailExistsException(String.format("Email already exists, %s", u.getEmailId()));
        });

    }

    public void verifyEmail() {

        /* The JWT filter already validated the token from the email link and stored
           the username in Spring Security's context. Read it back from there. */
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = this.userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

        user.setEmailVerified(true);

        /* save() on an entity that already has an id does an UPDATE, not an INSERT. */
        this.userRepository.save(user);
    }

    /* Blocks login until the verification link has been clicked. */
    private static User isEmailVerified(User user) {

        if (user.getEmailVerified().equals(false)) {
            throw new EmailNotVerifiedException(String.format("Email requires verification, %s", user.getEmailId()));
        }

        return user;
    }

    /* Hands the raw credentials to Spring Security, which loads the user through
       CustomUserDetailsService and compares the password with the BCrypt hash.
       A mismatch throws BadCredentialsException, handled in ExceptionHandling. */
    private Authentication authenticate(String username, String password) {
        return this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    }

    public User authenticate(User user) {

        /* Spring Security Authentication. */
        this.authenticate(user.getUsername(), user.getPassword());

        /* Get User from the DB. */
        return this.userRepository.findByUsername(user.getUsername()).map(UserService::isEmailVerified).get();
    }

    /* Builds the response header that carries the JWT. The client sends it back
       as "Bearer <token>" on every protected request. */
    public HttpHeaders generateJwtHeader(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(AUTHORIZATION, this.jwtService.generateJwtToken(username, this.provider.getJwtExpiration()));

        return headers;
    }

    public void sendResetPasswordEmail(String emailId) {

        Optional<User> opt = this.userRepository.findByEmailId(emailId);

        if (opt.isPresent()) {
            this.emailService.sendResetPasswordEmail(opt.get());
        } else {
            /* Log but do not throw, so outsiders cannot use this endpoint
               to discover which emails have accounts. */
            logger.debug("Email doesn't exist, {}", emailId);
        }
    }

    /* Returns the user that owns the JWT on the current request. */
    public User getUser() {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        /* Get User from the DB. */
        return this.userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));
    }

    /* Copies one field from the request onto the stored user, trimmed.
       A null in the request means "leave this field alone". */
    private void updateValue(Supplier<String> getter, Consumer<String> setter) {

        Optional.ofNullable(getter.get())
                .map(String::trim)
                .ifPresent(setter);
    }

    /* Same idea for the password, but blank is also ignored and the value is hashed first. */
    private void updatePassword(Supplier<String> getter, Consumer<String> setter) {

        Optional.ofNullable(getter.get())
                .filter(StringUtils::hasText)
                .map(this.passwordEncoder::encode)
                .ifPresent(setter);
    }

    /* user = what the client sent, currentUser = what is in the DB.
       Method references pair each getter on one with the setter on the other. */
    private User updateUser(User user, User currentUser) {

        this.updateValue(user::getFirstName, currentUser::setFirstName);
        this.updateValue(user::getLastName, currentUser::setLastName);
        this.updateValue(user::getPhone, currentUser::setPhone);
        this.updateValue(user::getEmailId, currentUser::setEmailId);
        this.updatePassword(user::getPassword, currentUser::setPassword);

        return this.userRepository.save(currentUser);
    }

    public User updateUser(User user) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        /* Validates the new email if provided. The filter skips the match when the
           email already belongs to this same user, so re-sending your own email is fine. */
        this.userRepository.findByEmailId(user.getEmailId())
                .filter(u -> !u.getUsername().equals(username))
                .ifPresent(u -> {throw new EmailExistsException(String.format("Email already exists, %s", u.getEmailId()));});

        /* Get and Update User */
        return this.userRepository.findByUsername(username)
                .map(currentUser -> this.updateUser(user, currentUser))
                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));
    }

    private User updateUserProfile(Profile profile, User user) {

        Profile currentProfile = user.getProfile();

        if (Optional.ofNullable(currentProfile).isPresent()) {
            /* Profile already exists: copy over only the fields that were sent. */
            this.updateValue(profile::getHeadline, currentProfile::setHeadline);
            this.updateValue(profile::getBio, currentProfile::setBio);
            this.updateValue(profile::getCity, currentProfile::setCity);
            this.updateValue(profile::getCountry, currentProfile::setCountry);
            this.updateValue(profile::getPicture, currentProfile::setPicture);
        }
        else {
            /* First time: attach the new profile to the user on both sides of the link.
               Cascade on the User side inserts the Profile row during save(). */
            user.setProfile(profile);
            profile.setUser(user);
        }

        return this.userRepository.save(user);
    }

    public User updateUserProfile(Profile profile) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        /* Get and Update User */
        return this.userRepository.findByUsername(username)
                .map(user -> this.updateUserProfile(profile, user))
                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));
    }

    public void resetPassword(String password) {

        /* Username comes from the JWT in the reset link, not from the request body,
           so a user can only reset their own password. */
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = this.userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(String.format("Username doesn't exist, %s", username)));

        user.setPassword(this.passwordEncoder.encode(password));

        this.userRepository.save(user);
    }

}
