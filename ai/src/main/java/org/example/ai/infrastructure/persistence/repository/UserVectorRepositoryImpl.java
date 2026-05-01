package org.example.ai.infrastructure.persistence.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.domain.model.UserVector;
import org.example.ai.domain.repository.UserVectorRepository;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserVectorRepositoryImpl implements UserVectorRepository {


    private final ElasticsearchClient elasticsearchClient;
    private final UserVectorESRepository userVectorESRepository;

    @Override
    public Optional<UserVector> findById(String userId) {
        try{
            var response = elasticsearchClient.get(g -> g
                .index("user-index")
                .id(userId)
                .sourceIncludes(
                    "userId",
                    "preferenceVector", "preferenceWeightSum",
                    "recentVector", "recentWeightSum",
                    "cartVector", "cartWeightSum",
                    "negativeVector", "negativeWeightSum",
                    "updatedAt"
                ),
                UserVector.class
            );

            if(!response.found()){
                return Optional.empty();
            }
            return Optional.ofNullable(response.source());
        }
        catch (Exception e){
            log.error("[UserVector] findById 실패 - userId: {}", userId, e);
            return Optional.empty();
        }

    }

    @Override
    public UserVector save(UserVector userVector) {
        return userVectorESRepository.save(userVector);
    }

    @Override
    public List<UserVector> findAll() {
        List<UserVector> result = new ArrayList<>();
        userVectorESRepository.findAll().forEach(result::add);
        return result;
    }
}
