package com.be_paas.modules.user.repository;

import com.be_paas.modules.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%'))")
    Page<User> searchUsers(@Param("term") String term, Pageable pageable);

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
