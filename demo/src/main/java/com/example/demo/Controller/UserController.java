package com.example.demo.Controller;

import com.example.demo.Dto.UpdateUserRequest;
import com.example.demo.Models.User;
import com.example.demo.Service.UserDetailServiceImpl;
import com.example.demo.Service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import static com.example.demo.Controller.PublicController.passwordEncoder;

@RestController
@RequestMapping("/User")
public class UserController {

    @Autowired
    UserService userService;


    @GetMapping("/getUserDetails")
    public ResponseEntity<?> getUserDetails(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getCurrUser(username);
            if (user != null)return new ResponseEntity<>(user, HttpStatus.OK);
            return new ResponseEntity<>("Faced problem fetching the user",HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Can't fetch the userDetails",HttpStatus.FORBIDDEN);
        }
    }

    @PostMapping("/Logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        try{
            Cookie cookie = new Cookie("jwtToken", null);
            cookie.setHttpOnly(true);
            cookie.setPath("/"); // Match the path of the original cookie
            cookie.setMaxAge(0); // Delete the cookie immediately

            response.addCookie(cookie);

            return new ResponseEntity<>("Logged out successfully", HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Failed to logout",HttpStatus.FORBIDDEN);
        }

    }

    @GetMapping("/getCurrUser")
    public ResponseEntity<?> getCurrUser(){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            User user = userService.getCurrUser(username);
            return new ResponseEntity<>(user,HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Unable to fetch the current user",HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/updateUserDetails")
    public ResponseEntity<?> updateUserDetails(@RequestBody UpdateUserRequest updatedDetails) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            User user = userService.getUserByName(username);
            System.out.println(user);
            if (user == null) {
                return new ResponseEntity<>("User was not fetched while updating", HttpStatus.BAD_REQUEST);
            }

            // Check current password before allowing password update
            if (updatedDetails.getPassword() != null && !updatedDetails.getPassword().isEmpty()) {
                if (updatedDetails.getCurrentPassword() == null ||
                        !passwordEncoder().matches(updatedDetails.getCurrentPassword(), user.getPassword())) {
                    return new ResponseEntity<>("Incorrect current password", HttpStatus.UNAUTHORIZED);
                }

                user.setPassword(passwordEncoder().encode(updatedDetails.getPassword()));
            }

            // Username update (optional)
            if (updatedDetails.getUsername() != null && !updatedDetails.getUsername().isEmpty()) {
                user.setUsername(updatedDetails.getUsername());
            }

            user.setUpdatedAt(LocalDateTime.now());
            userService.saveUser(user);

            return new ResponseEntity<>(user, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Unable to update the user", HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/deleteUserById/{userId}")
    public ResponseEntity<?> deleteUserById(@PathVariable ObjectId userId){
        try{
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            if (!userService.deleteByUserId(userId,username))return new ResponseEntity<>("Forbidden to delete someone else's id",HttpStatus.FORBIDDEN);
            return new ResponseEntity<>(userId,HttpStatus.OK);
        }
        catch (Exception e){
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }



}
