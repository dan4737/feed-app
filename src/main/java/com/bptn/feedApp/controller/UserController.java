package com.bptn.feedApp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.bptn.feedApp.service.UserService;
import java.util.List;
import java.util.Optional;

import com.bptn.feedApp.jdbc.UserBean;
import com.bptn.feedApp.jpa.User;

import org.springframework.web.bind.annotation.PathVariable;
import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import static org.springframework.http.HttpStatus.OK;
import tools.jackson.databind.JsonNode;

// REST -> REpresentational State Transfer
/* Browsers hide custom response headers from front-end JavaScript unless the server
   exposes them. Without this the React app could never read the Authorization header. */
@CrossOrigin(exposedHeaders = "Authorization")
@RestController
@RequestMapping("/user")
public class UserController {

    final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    UserService userService;

    @GetMapping("/test")
    public String testController() {
        logger.debug("The testController() method was invoked!");
        return "<h1>The FeedApp application is up and running</h1>";
    }

    @GetMapping("/")
    public List<User> listUsers() {
        logger.debug("The listUsers() method was invoked!");
        return this.userService.listUsers();
    }

    @GetMapping("/{username}")
    public Optional<User> findByUsername(@PathVariable String username) {
        logger.debug("The findByUsername() method was invoked!, username={}", username);
        return this.userService.findByUsername(username);
    }

    //	@GetMapping("/{first}/{last}/{username}/{password}/{phone}/{emailId}")
//	public String createUser( @PathVariable String first, @PathVariable String last, @PathVariable String username, @PathVariable String password, @PathVariable String phone, @PathVariable String emailId) {
//		User user = new User();
//
//		user.setFirstName(first);
//		user.setLastName(last);
//		user.setUsername(username);
//		user.setPassword(password);
//		user.setPhone(phone);
//		user.setEmailId(emailId);
//		user.setEmailVerified(false);
//		user.setCreatedOn(Timestamp.from(Instant.now()));
//
//		logger.debug("The createUser() method was invoked!, user={}", user.toString());
//
//		this.userService.createUser(user);
//
//		return "User Created Successfully";
//}
    @PostMapping("/signup")
    public User signup(@RequestBody User user) {
        logger.debug("Signing up, username: {}", user.getUsername());
        return this.userService.signup(user);
    }

    /* GET because the user arrives here by clicking a link in an email.
       Protected: the token in the link is what identifies the user. */
    @GetMapping("/verify")
    public void verifyEmail() {
        logger.debug("Verifying Email");
        this.userService.verifyEmail();
    }

    /* /user/login is in jwt.excluded.urls, so the filter lets it through without a token. */
    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody User user) {
        logger.debug("Authenticating, username: {}", user.getUsername());

        /* Spring Security Authentication. */
        user = this.userService.authenticate(user);

        /* Generate JWT and HTTP Header */
        HttpHeaders jwtHeader = this.userService.generateJwtHeader(user.getUsername());

        logger.debug("User Authenticated, username: {}", user.getUsername());

        /* User goes in the body, token goes in the Authorization header. */
        return new ResponseEntity<>(user, jwtHeader, OK);
    }

    /* /user/reset/* is in jwt.excluded.urls: the user is locked out and has no token yet. */
    @GetMapping("/reset/{emailId}")
    public void sendResetPasswordEmail(@PathVariable String emailId) {

        logger.debug("Sending Reset Password Email, emailId: {}", emailId);

        this.userService.sendResetPasswordEmail(emailId);
    }

    /* POST /user/reset is not matched by /user/reset/*, so the JWT filter still
       protects it. The token from the reset email is required. */
    @PostMapping("/reset")
    public void passwordReset(@RequestBody JsonNode json) {

        logger.debug("Resetting Password, password: {}", json.get("password").asText());

        this.userService.resetPassword(json.get("password").asText());
    }

}

