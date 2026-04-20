package com.devticket.admin.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Document(indexName = "techstack", createIndex = false)
public class TechStackDocument {

    @Id
    private String id;

    private String name;

    private float[] embedding;

}
