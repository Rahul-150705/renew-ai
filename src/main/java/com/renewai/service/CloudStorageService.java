package com.renewai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import jakarta.annotation.PostConstruct;

/**
 * Cloud Storage Service - AWS S3
 *
 * Stores policy PDFs in S3, keyed by the owning agent so files stay
 * partitioned per agent:
 *
 *   policies/agent-{agentId}/{policyNumber}.pdf
 *
 * Only the S3 <b>key</b> is persisted in {@code policy.pdfFilePath}. Files are
 * never made public and are never written to local disk — they are streamed
 * back to the browser by the backend after an ownership check
 * (see {@link PolicyService#getPolicyPdf}).
 *
 * If the AWS properties are not configured the service starts in a disabled
 * state: PDF upload/view return a clear error, while PDF text extraction
 * (Groq) keeps working.
 */
@Service
public class CloudStorageService {

    private static final Logger logger = LoggerFactory.getLogger(CloudStorageService.class);

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    @Value("${aws.s3.region:ap-south-1}")
    private String region;

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    private S3Client s3Client;
    private boolean enabled = false;

    /** Value object returned when downloading a PDF. */
    public record StoredFile(byte[] bytes, String contentType) {
    }

    @PostConstruct
    public void init() {
        if (isBlank(bucketName) || isBlank(accessKeyId) || isBlank(secretAccessKey)) {
            logger.warn("S3 not configured (aws.s3.bucket / aws.access-key-id / aws.secret-access-key missing). "
                    + "Policy PDF storage is DISABLED. PDF extraction is unaffected.");
            return;
        }
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
        this.enabled = true;
        logger.info("S3 client initialized — bucket: {}, region: {}", bucketName, region);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Upload a policy PDF to S3 under the owning agent's prefix.
     *
     * @param file         the uploaded PDF
     * @param agentId      id of the agent that owns the policy
     * @param policyNumber the policy number (used as the file name)
     * @return the S3 object key stored in {@code policy.pdfFilePath}
     */
    public String uploadPolicyPdf(MultipartFile file, Long agentId, String policyNumber) throws Exception {
        ensureEnabled();

        String safePolicy = (policyNumber != null && !policyNumber.isBlank())
                ? policyNumber.trim().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "policy_" + System.currentTimeMillis();

        String key = "policies/agent-" + agentId + "/" + safePolicy + ".pdf";

        logger.info("Uploading policy PDF to S3: {}", key);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType("application/pdf")
                        .build(),
                RequestBody.fromBytes(file.getBytes()));
        logger.info("Upload successful: {}", key);
        return key;
    }

    /** Download a stored PDF by its S3 key. */
    public StoredFile downloadPolicyPdf(String key) {
        ensureEnabled();
        ResponseBytes<GetObjectResponse> obj = s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucketName).key(key).build());
        String contentType = obj.response().contentType();
        return new StoredFile(obj.asByteArray(),
                contentType != null ? contentType : "application/pdf");
    }

    /**
     * Delete a stored PDF. Accepts either a bare S3 key or a legacy full S3 URL.
     * Never throws — a storage cleanup failure must not block policy deletion.
     */
    public void deleteFile(String keyOrUrl) {
        if (!enabled || keyOrUrl == null || keyOrUrl.isBlank()) {
            return;
        }
        try {
            String key = keyOrUrl;
            int idx = keyOrUrl.indexOf(".amazonaws.com/");
            if (idx != -1) {
                key = keyOrUrl.substring(idx + ".amazonaws.com/".length());
            }
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            logger.info("Deleted from S3: {}", key);
        } catch (Exception e) {
            logger.warn("Could not delete S3 file ({}): {}", keyOrUrl, e.getMessage());
        }
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new IllegalStateException(
                    "PDF storage is not configured on the server. Set AWS_S3_BUCKET, "
                            + "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
