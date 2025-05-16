package com.example.demo.Service;

import com.example.demo.Models.User;
import com.example.demo.Models.Venues;
import com.example.demo.Repository.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public void saveUser(User user) {
        userRepository.save(user);
    }


    public User getUserDetails(ObjectId userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        User user = userOptional.get();
        return user;
    }

    public User getCurrUser(String username) {
        return userRepository.findByusername(username);
    }

    public User getUserByName(String username) {
        return userRepository.findByusername(username);
    }


    public boolean deleteByUserId(ObjectId Id, String username) {
        User user = userRepository.findByusername(username);
        if(!user.getUserId().equals(Id)){
            return false;
        }
        userRepository.deleteById(Id);

        return true;
    }

    public User getUserById(ObjectId userId) {
        Optional<User> userOptional =  userRepository.findById(userId);
        return userOptional.get();
    }


}
