package com.shepherdmoney.interviewproject.controller;

import com.shepherdmoney.interviewproject.vo.request.CreateUserPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.shepherdmoney.interviewproject.model.User;
import com.shepherdmoney.interviewproject.repository.UserRepository;

@RestController
public class UserController {

    // wire in the user repository (~ 1 line)
    @Autowired
    private UserRepository userRepository;

    @PutMapping("/user")
    public ResponseEntity<Integer> createUser(@RequestBody CreateUserPayload payload) {
        // Create a user entity with information given in the payload, store it in the database
        //       and return the id of the user in 200 OK response
        User newUser = new User();
        newUser.setName(payload.getName());
        newUser.setEmail(payload.getEmail());
        User savedUser = userRepository.save(newUser);
        return ResponseEntity.ok(savedUser.getId());
    }

    @DeleteMapping("/user")
    public ResponseEntity<String> deleteUser(@RequestParam int userId) {
        // Return 200 OK if a user with the given ID exists, and the deletion is successful
        //       Return 400 Bad Request if a user with the ID does not exist
        //       The response body could be anything you consider appropriate
        return userRepository.findById(userId).map(user -> {
            userRepository.delete(user);
            return ResponseEntity.ok("User deleted successfully.");
        }).orElseGet(() -> ResponseEntity.badRequest().body("No user found with ID: " + userId));
    }
}
