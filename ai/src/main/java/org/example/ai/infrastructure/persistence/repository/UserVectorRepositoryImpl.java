package org.example.ai.infrastructure.persistence.repository;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.ai.domain.model.UserVector;
import org.example.ai.domain.repository.UserVectorRepository;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserVectorRepositoryImpl implements UserVectorRepository {


    private final UserVectorESRepository userVectorESRepository;

    @Override
    public Optional<UserVector> findById(String userId) {
        return userVectorESRepository.findById(userId);
    }

    @Override
    public UserVector save(UserVector userVector) {
        return userVectorESRepository.save(userVector);
    }
}
