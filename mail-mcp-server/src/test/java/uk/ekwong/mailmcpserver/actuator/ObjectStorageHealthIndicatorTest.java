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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import uk.ekwong.mailcommon.config.S3Properties;

class ObjectStorageHealthIndicatorTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Properties properties = new S3Properties();
    private final ObjectStorageHealthIndicator indicator =
            new ObjectStorageHealthIndicator(s3Client, properties);

    @Test
    void reportsUpWhenBucketCanBeHeaded() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("bucket", "journal-emails");
    }

    @Test
    void reportsDownWhenBucketDoesNotExist() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(
                        NoSuchBucketException.builder()
                                .message("The specified bucket does not exist")
                                .statusCode(404)
                                .build());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("reason", "bucket does not exist")
                .containsEntry("bucket", "journal-emails");
    }

    @Test
    void reportsDownWithoutLeakingExceptionDetailsWhenEndpointIsUnreachable() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("reason", "cannot reach object storage endpoint");
    }
}
