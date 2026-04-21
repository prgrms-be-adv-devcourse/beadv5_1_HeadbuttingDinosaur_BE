package com.devticket.admin.infrastructure.config;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchClient elasticsearchClient;

    @PostConstruct
    public void init(){
        createIndexIfNotExists("techstack-index", "elasticsearch/techstack-index-mapping.json");
    }

    private void createIndexIfNotExists(String indexName, String mappingFilePath){

        try{
            // index가 존재하면 -> 스킵
            boolean exists = elasticsearchClient.indices()
                .exists(e -> e.index(indexName))
                .value();

            if(exists){
                log.info("[ES] 인덱스 이미 존재 : {}", indexName);
                return;
            }

            // JSON 파일 -> 인덱스 생성
            InputStream mappingStream = new ClassPathResource(mappingFilePath).getInputStream();

            elasticsearchClient.indices().create(c -> c
                .index(indexName)
                .withJson(mappingStream)
            );

            log.info("[ES] 인덱스 생성 완료: {}", indexName);

        } catch (Exception e){
            log.error("[ES] 인덱스 생성 실패: {}", indexName, e);
        }



    }

}