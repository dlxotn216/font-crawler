package io.crscube.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Lee Tae Su
 * @version 1.0
 * @project font.crawler
 * @since 2018-06-08
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
public class ApplicationConfig {
	private List<String> metaUrls = new ArrayList<>();
	
}
