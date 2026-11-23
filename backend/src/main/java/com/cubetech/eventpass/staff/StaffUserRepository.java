package com.cubetech.eventpass.staff;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

    Optional<StaffUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<StaffUser> findAllByOrderByRoleAscNameAsc();
}
