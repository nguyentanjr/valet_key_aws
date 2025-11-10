package com.example.valetkey.service;

import com.example.valetkey.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AWSS3Service {

    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucket-name:valet-demo}")
    private String bucketName;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    /**
     * Generate presigned URL for uploading a file
     */
    public String generatePresignedUploadUrl(String objectKey, int expiryMinutes, User user) {
        if (!user.isCreate() && !user.isWrite()) {
            throw new RuntimeException("User does not have permission to upload files");
        }

        try (S3Presigner presigner = createPresigner()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expiryMinutes))
                    .putObjectRequest(putObjectRequest)
                    .build();

            var presignedRequest = presigner.presignPutObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Error generating presigned upload URL for key: {}", objectKey, e);
            throw new RuntimeException("Failed to generate upload URL: " + e.getMessage(), e);
        }
    }

    /**
     * Generate presigned URL for downloading a file
     */
    public String generatePresignedDownloadUrl(String objectKey, int expiryMinutes, User user) {
        if (!user.isRead()) {
            throw new RuntimeException("User does not have permission to download files");
        }

        // Check if object exists
        if (!objectExists(objectKey)) {
            throw new RuntimeException("File not found: " + objectKey);
        }

        try (S3Presigner presigner = createPresigner()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expiryMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            var presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            log.error("Error generating presigned download URL for key: {}", objectKey, e);
            throw new RuntimeException("Failed to generate download URL: " + e.getMessage(), e);
        }
    }

    /**
     * Delete an object from S3
     */
    public void deleteObject(String objectKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.debug("Deleted object from S3: {}", objectKey);
        } catch (Exception e) {
            log.error("Error deleting object from S3: {}", objectKey, e);
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    /**
     * Check if an object exists in S3
     */
    public boolean objectExists(String objectKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking object existence: {}", objectKey, e);
            return false;
        }
    }

    /**
     * List objects with a given prefix
     */
    public List<String> listObjects(String prefix) {
        List<String> result = new ArrayList<>();
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            for (S3Object s3Object : listResponse.contents()) {
                result.add(s3Object.key());
            }
        } catch (Exception e) {
            log.error("Error listing objects with prefix: {}", prefix, e);
        }
        return result;
    }

    /**
     * Get object input stream (for reading files)
     */
    public InputStream getObjectInputStream(String objectKey) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            return s3Client.getObject(getRequest);
        } catch (Exception e) {
            log.error("Error getting object input stream: {}", objectKey, e);
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }

    /**
     * Create S3Presigner with credentials
     */
    private S3Presigner createPresigner() {
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            software.amazon.awssdk.auth.credentials.AwsBasicCredentials awsCreds =
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey);
            return S3Presigner.builder()
                    .region(software.amazon.awssdk.regions.Region.of(region))
                    .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(awsCreds))
                    .build();
        } else {
            return S3Presigner.builder()
                    .region(software.amazon.awssdk.regions.Region.of(region))
                    .build();
        }
    }
}

