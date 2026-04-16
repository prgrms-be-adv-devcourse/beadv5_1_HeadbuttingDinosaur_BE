package org.example.ai.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.example.ai.application.service.RecommendationService;
import org.example.ai.presentation.dto.req.RecommendationRequest;
import org.example.ai.presentation.dto.res.RecommendationResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("internal/ai")
public class RecommendationController {
    private final RecommendationService recommendationService;

    @PostMapping("/recommendation")
    public RecommendationResponse recommend(@RequestBody RecommendationRequest request){
        return recommendationService.recommendByUserVector(request);
    }
}
