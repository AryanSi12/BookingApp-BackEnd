package com.example.demo.Service;

import com.example.demo.Models.User;
import com.example.demo.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserDetailServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByusername(username);
        if(user != null){
            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username(user.getUsername())
                    .password(user.getPassword())
                    .authorities(user.getRoles().stream()  // Convert List<Enum> to List<GrantedAuthority>
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))  // Add "ROLE_" prefix
                            .collect(Collectors.toList()))  // Collect as List<GrantedAuthority>
                    .build();

            return userDetails;
        }
        throw new UsernameNotFoundException("User not found of the name"+username);
    }
}