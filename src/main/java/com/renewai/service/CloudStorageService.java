package com.renewai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import jakarta.annotation.PostConstruct;

/**
 * Cloud Storage Service - AWS S3
 *
 * Handles all PDF uploads and deletions.
 * Stores PDFs in S3 and returns a permanent public URL.
 *
 * S3 folder structure:
 *   policies/{clientEmail}/{policyNumber}.pdf
 *
 * The URL is stored directly in policy.pdfFilePath in the database.
 * No files are written to local disk at any point.
 */
// @Service  // PDF storage disabled
public class CloudStorageService {

    private static final Logger logger = LoggerFactory.getLogger(CloudStorageService.class);

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.access-key-id}")
    private String accessKeyId;

    @Value("${aws.secret-access-key}")
    private String secretAccessKey;

    private S3Client s3Client;

    /**
     * Initialize the S3 client after properties are injected.
     */
    @PostConstruct
    public void init() {
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                ))
                .build();
        logger.info("S3 client initialized — bucket: {}, region: {}", bucketName, region);
    }

    /**
     * Upload a policy PDF to S3.
     *
     * S3 key:  policies/{sanitizedEmail}/{policyNumber}.pdf
     * URL:     https://{bucket}.s3.{region}.amazonaws.com/policies/{email}/{policyNumber}.pdf
     *
     * @param file         the uploaded PDF file
     * @param clientEmail  the client's email (used as folder name)
     * @param policyNumber the extracted policy number (used as filename)
     * @return public S3 URL of the uploaded file
     */
    public String uploadPdf(MultipartFile file, String clientEmail, String policyNumber) throws Exception {
        // Sanitize email for safe use in S3 key
        String safeEmail = (clientEmail != null && !clientEmail.isBlank())
                ? clientEmail.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_")
                : "unknown";

        // Sanitize policy number for safe use as filename
        String safePolicy = (policyNumber != null && !policyNumber.isBlank())
                ? policyNumber.trim().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "unknown_" + System.currentTimeMillis();

        String s3Key = "policies/" + safeEmail + "/" + safePolicy + ".pdf";

        logger.info("Uploading PDF to S3: {}", s3Key);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType("application/pdf")
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));

        String url = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + s3Key;
        logger.info("Upload successful. URL: {}", url);
        return url;
    }

    /**
     * Delete a PDF from S3 using its URL.
     * Extracts the S3 key from the URL and deletes the object.
     *
     * Called when a policy is deleted so S3 stays in sync with the DB.
     *
     * @param fileUrl the full S3 URL stored in policy.pdfFilePath
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;

        try {
            // Extract key from URL:
            // https://{bucket}.s3.{region}.amazonaws.com/{key} → {key}
            int idx = fileUrl.indexOf(".amazonaws.com/");
            if (idx == -1) {
                logger.warn("Not a valid S3 URL, skipping delete: {}", fileUrl);
                return;
            }
            String s3Key = fileUrl.substring(idx + ".amazonaws.com/".length());

            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());

            logger.info("Deleted from S3: {}", s3Key);

        } catch (Exception e) {
            // Log but don't throw — S3 deletion failure should not break policy deletion
            logger.warn("Could not delete S3 file ({}): {}", fileUrl, e.getMessage());
        }
    }
}