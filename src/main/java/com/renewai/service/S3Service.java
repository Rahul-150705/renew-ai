package com.renewai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;

@Service
public class S3Service {
    private final S3Client s3Client;
    private final S3Presigner presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public S3Service() {
        this.s3Client = S3Client.builder().region(Region.AP_SOUTH_1).build();
        this.presigner = S3Presigner.builder().region(Region.AP_SOUTH_1).build();
    }

    public String upload(MultipartFile file, String key) throws IOException {
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key)
                .contentType(file.getContentType()).build(),
            RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );
        return key;
    }

    public String presignedGetUrl(String key) {
        GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucket).key(key).build();
        GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(10))
            .getObjectRequest(getReq).build();
        return presigner.presignGetObject(presignReq).url().toString();
    }
}
