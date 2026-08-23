package com.wangning.storage.aliyun;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.wangning.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OssConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OssConfiguration.class);

    @Test
    void shouldRemainDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(OSSClient.class);
            assertThat(context).doesNotHaveBean(CredentialsProvider.class);
            assertThat(context).doesNotHaveBean(ObjectStorageService.class);
        });
    }

    @Test
    void shouldCreateEnvironmentCredentialProviderAndReusableClient() {
        contextRunner
                .withPropertyValues(
                        "storage.oss.enabled=true",
                        "storage.oss.region=cn-beijing",
                        "storage.oss.endpoint=https://oss-cn-beijing.aliyuncs.com",
                        "storage.oss.bucket=zhiguang-test",
                        "storage.oss.public-base-url=https://static.example.com",
                        "storage.oss.avatar-max-size=8MB",
                        "storage.oss.presign-ttl=PT5M"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(OSSClient.class);
                    assertThat(context).hasSingleBean(CredentialsProvider.class);
                    assertThat(context).hasSingleBean(ObjectStorageService.class);
                    assertThat(context.getBean(CredentialsProvider.class))
                            .isInstanceOf(EnvironmentVariableCredentialsProvider.class);

                    OssProperties properties = context.getBean(OssProperties.class);
                    assertThat(properties.getRegion()).isEqualTo("cn-beijing");
                    assertThat(properties.getBucket()).isEqualTo("zhiguang-test");
                    assertThat(properties.getPublicBaseUrl()).isEqualTo("https://static.example.com");
                    assertThat(properties.getAvatarMaxSize()).isEqualTo(DataSize.ofMegabytes(8));
                    assertThat(properties.getPresignTtl()).isEqualTo(Duration.ofMinutes(5));

                    String destroyMethod = context.getBeanFactory()
                            .getBeanDefinition("ossClient")
                            .getDestroyMethodName();
                    assertThat(destroyMethod).isEqualTo("close");
                });
    }

    @Test
    void shouldRejectMissingRequiredConfigurationWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "storage.oss.enabled=true",
                        "storage.oss.region=cn-beijing"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessage("storage.oss.bucket 不能为空");
                });
    }
}
