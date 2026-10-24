package com.bte.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class MediaResourceConfig implements WebMvcConfigurer {

	private final Path storageRoot;
	private final String publicPath;

	public MediaResourceConfig(
			@Value("${app.media.storage-directory:uploads}") String storageDirectory,
			@Value("${app.media.public-path:/media}") String publicPath) {
		this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
		this.publicPath = publicPath.endsWith("/")
				? publicPath.substring(0, publicPath.length() - 1)
				: publicPath;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = storageRoot.toUri().toString();
		if (!location.endsWith("/")) {
			location += "/";
		}
		registry.addResourceHandler(publicPath + "/**")
				.addResourceLocations(location);
	}
}
