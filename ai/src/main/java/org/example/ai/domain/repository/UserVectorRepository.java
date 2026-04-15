package org.example.ai.domain.repository;

import java.util.List;
import java.util.Optional;
import org.example.ai.domain.model.UserVector;

public interface UserVectorRepository {
    Optional<UserVector> findById(String userId);
    UserVector save(UserVector userVector);
    List<UserVector> findAll();
}
