package com.renewai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;

@Service
public class S3Service {
    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    @Value("${aws.s3.bucket:}")
    private String bucket;

    @Value("${aws.s3.region:ap-south-1}")
    private String region;

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    private S3Client s3Client;
    private S3Presigner presigner;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(accessKeyId)
                || !StringUtils.hasText(secretAccessKey)) {
            logger.warn("S3 upload/view is disabled. Configure AWS_S3_BUCKET, AWS_ACCESS_KEY_ID, "
                    + "AWS_SECRET_ACCESS_KEY and AWS_S3_REGION.");
            return;
        }

        Region awsRegion = Region.of(region);
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        this.s3Client = S3Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentials)
                .build();
        this.presigner = S3Presigner.builder()
                .region(awsRegion)
                .credentialsProvider(credentials)
                .build();
        logger.info("S3 client initialized for bucket {} in region {}", bucket, region);
    }

    public String upload(MultipartFile file, String key) throws IOException {
        ensureConfigured();
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key)
                .contentType(file.getContentType()).build(),
            RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );
        return key;
    }

    public String presignedGetUrl(String key) {
        ensureConfigured();
        GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucket).key(key).build();
        GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(10))
            .getObjectRequest(getReq).build();
        return presigner.presignGetObject(presignReq).url().toString();
    }

    public void delete(String keyOrUrl) {
        ensureConfigured();
        if (!StringUtils.hasText(keyOrUrl)) {
            return;
        }

        String key = keyOrUrl;
        int urlPathStart = keyOrUrl.indexOf(".amazonaws.com/");
        if (urlPathStart >= 0) {
            key = keyOrUrl.substring(urlPathStart + ".amazonaws.com/".length());
        }

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        logger.info("Deleted S3 object: {}", key);
    }

    private void ensureConfigured() {
        if (s3Client == null || presigner == null) {
            throw new IllegalStateException(
                    "S3 storage is not configured. Set AWS_S3_BUCKET, AWS_S3_REGION, "
                            + "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY.");
        }
    }
}
