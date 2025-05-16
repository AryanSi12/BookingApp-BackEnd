package com.example.demo.Repository;

import com.example.demo.Models.User;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User , ObjectId> {
    User findByusername(String username);
    User findByemail(String email);
}