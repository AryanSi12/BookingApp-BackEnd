package com.example.demo.Controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.demo.Enums.Roles;
import com.example.demo.Models.User;
import com.example.demo.Service.UserService;
import com.example.demo.Utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/Public")
public class PublicController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private Cloudinary cloudinary;


    //Password encryption
    public final static PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder(12);
    }

    //Adding an end-user
    @PostMapping(
            value = "/AddUser",
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE }
    )
    public ResponseEntity<?> saveUser(
            @RequestPart("user") String userJson,
            @RequestPart("image") MultipartFile image
    ) {
        try {
            // Convert JSON to User
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            User user = objectMapper.readValue(userJson, User.class);

            // Upload image (example using Cloudinary or your preferred service)
            Map<?, ?> uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.emptyMap());
            String imageUrl = uploadResult.get("secure_url").toString();
            user.setUserImage(imageUrl);

            // Password encoding and other setup
            String password = user.getPassword();
            password = passwordEncoder().encode(password);
            user.setPassword(password);
            user.setRoles(Arrays.asList(Roles.END_USER));
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());

            userService.saveUser(user);
            return new ResponseEntity<>(user, HttpStatus.CREATED);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error creating user", HttpStatus.BAD_REQUEST);
        }
    }

    //Adding an organizer
    @PostMapping(
            value = "/AddOrganizer",
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE }
    )
    public ResponseEntity<?> saveOrganizer(
            @RequestPart("user") String userJson,       // JSON part for the User object
            @RequestPart("image") MultipartFile image  // Image part
    ) {
        try {
            // Convert JSON string to User object
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            User user = objectMapper.readValue(userJson, User.class);

            // Upload image to Cloudinary (or your preferred service)
            Map<?, ?> uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.emptyMap());
            String imageUrl = uploadResult.get("secure_url").toString();
            user.setUserImage(imageUrl);

            // Password encoding and other setup
            String password = user.getPassword();
            password = passwordEncoder().encode(password);
            user.setPassword(password);
            user.setRoles(Arrays.asList(Roles.END_USER, Roles.ORGANIZER));
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());

            userService.saveUser(user);
            return new ResponseEntity<>(user, HttpStatus.CREATED);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error creating organizer", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/LoginUser")
    public ResponseEntity<?> loginUser(@RequestBody User user, HttpServletResponse response){
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));

            String token = jwtUtil.generateToken(user.getUsername());

            // Use Spring's ResponseCookie to add Secure and SameSite=None
            ResponseCookie cookie = ResponseCookie.from("jwtToken", token)
                    .httpOnly(true)
                    .secure(true) // ✅ Required for SameSite=None
                    .sameSite("None") // ✅ Needed for cross-origin
                    .path("/")
                    .maxAge(50 * 60)
                    .build();

            response.setHeader("Set-Cookie", cookie.toString());

            return new ResponseEntity<>(token, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Incorrect Username or password", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok("OK");
    }


}
