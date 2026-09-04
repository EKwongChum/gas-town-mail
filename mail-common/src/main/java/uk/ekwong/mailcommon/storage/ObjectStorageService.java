/*
 * Copyright 2026 ekwongchum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ekwong.mailcommon.storage;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uk.ekwong.mailcommon.config.S3Properties;

/**
 * Stores and reads raw email objects in an S3-compatible bucket using the archive id as the object
 * key. Shared by the archiver (write) and the cleaner (read).
 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final S3Client s3Client;
    private final String bucket;
    private final boolean createBucketIfMissing;

    public ObjectStorageService(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.getBucket();
        this.createBucketIfMissing = properties.isCreateBucketIfMissing();
    }

    @PostConstruct
    public void ensureBucketExists() {
        if (!createBucketIfMissing) {
            return;
        }
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Object storage bucket '{}' already exists", bucket);
        } catch (NoSuchBucketException e) {
            try {
                s3Client.createBucket(b -> b.bucket(bucket));
                log.info("Created object storage bucket '{}'", bucket);
            } catch (S3Exception createException) {
                log.warn("Could not create bucket '{}': {}", bucket, createException.getMessage());
            }
        } catch (S3Exception e) {
            log.warn("Could not verify bucket '{}': {}", bucket, e.getMessage());
        } catch (RuntimeException e) {
            log.warn(
                    "Could not contact object storage at startup ({}); bucket verification/creation skipped",
                    e.getMessage());
        }
    }

    /** Stores the original email with the archive id as the object key. */
    public void store(String objectKey, byte[] content) {
        PutObjectRequest request =
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType("message/rfc822")
                        .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
        log.info("Stored email object s3://{}/{} ({} bytes)", bucket, objectKey, content.length);
    }

    /** Reads the raw email object bytes. */
    public byte[] read(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
        try (ResponseInputStream<GetObjectResponse> in = s3Client.getObject(request)) {
            byte[] content = in.readAllBytes();
            log.info("Read email object s3://{}/{} ({} bytes)", bucket, objectKey, content.length);
            return content;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read object s3://" + bucket + "/" + objectKey, e);
        }
    }

    /**
     * Best-effort removal of an object, used to compensate a partial archive (object written but
     * MongoDB metadata write failed).
     */
    public void deleteIfPresent(String objectKey) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
            log.info("Removed compensating object s3://{}/{}", bucket, objectKey);
        } catch (S3Exception e) {
            log.warn(
                    "Failed to remove compensating object s3://{}/{}: {}",
                    bucket,
                    objectKey,
                    e.getMessage());
        }
    }
}
