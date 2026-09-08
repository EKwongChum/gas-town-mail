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

package uk.ekwong.mailmcpserver.actuator;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import uk.ekwong.mailcommon.config.S3Properties;

/**
 * Reports whether the configured S3/MinIO bucket can be reached. The original-email download
 * endpoint reads staged {@code .eml} files from object storage, so a broken S3 endpoint should
 * surface as {@code DOWN} in the health check even when Elasticsearch is healthy.
 */
@Component
public class ObjectStorageHealthIndicator implements HealthIndicator {

    private final S3Client s3Client;
    private final String bucket;

    public ObjectStorageHealthIndicator(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.getBucket();
    }

    @Override
    public Health health() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return Health.up().withDetail("bucket", bucket).build();
        } catch (NoSuchBucketException e) {
            return Health.down()
                    .withDetail("reason", "bucket does not exist")
                    .withDetail("bucket", bucket)
                    .build();
        } catch (S3Exception e) {
            return Health.down()
                    .withDetail("reason", errorMessage(e))
                    .withDetail("bucket", bucket)
                    .build();
        } catch (RuntimeException e) {
            return Health.down()
                    .withDetail("reason", "cannot reach object storage endpoint")
                    .withDetail("bucket", bucket)
                    .build();
        }
    }

    private String errorMessage(S3Exception e) {
        if (e.awsErrorDetails() != null && e.awsErrorDetails().errorMessage() != null) {
            return e.awsErrorDetails().errorMessage();
        }
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        return "S3 request failed";
    }
}
