package com.devticket.event.presentation.controller;

import com.devticket.event.application.S3ImageUploadService;
import com.devticket.event.common.response.SuccessResponse;
import com.devticket.event.presentation.dto.ImageUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/seller/images")
@RequiredArgsConstructor
public class SellerImageUploadController {

    private final S3ImageUploadService s3ImageUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<ImageUploadResponse> uploadImage(
        @RequestHeader("X-User-Id") UUID sellerId,
        @RequestPart("file") MultipartFile file) {

        String imageUrl = s3ImageUploadService.upload(file);
        return SuccessResponse.success(new ImageUploadResponse(imageUrl));
    }
}
