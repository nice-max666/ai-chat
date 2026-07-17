package com.example.ai_chat.repository;

import com.example.ai_chat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    User findByUsername(String username);

    boolean existsByUsername(String username);
}
