package com.bte.service.media;

import com.bte.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageReader;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.media.provider",havingValue="local",matchIfMissing=true)
public class LocalMediaServiceImpl implements MediaService {

	private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
	private static final Pattern FOLDER_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
	private static final Set<String> ALLOWED_CONTENT_TYPES =
			Set.of("image/jpeg", "image/png", "image/gif");

	private final Path storageRoot;
	private final String publicPath;

	public LocalMediaServiceImpl(
			@Value("${app.media.storage-directory:uploads}") String storageDirectory,
			@Value("${app.media.public-path:/media}") String publicPath) {
		this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
		this.publicPath = publicPath.endsWith("/")
				? publicPath.substring(0, publicPath.length() - 1)
				: publicPath;
	}

	@Override
	public String uploadFile(MultipartFile file, String folderName) {
		ImageUploadValidator.validate(file,folderName);
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("An image file is required");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new BadRequestException("Image file must not exceed 5 MB");
		}
		if (folderName == null || !FOLDER_PATTERN.matcher(folderName).matches()) {
			throw new BadRequestException("Invalid media folder name");
		}
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
			throw new BadRequestException("Only JPEG, PNG, and GIF images are supported");
		}

		String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
		String extension = extensionFor(normalizedContentType);
		byte[] content;
		try {
			content = file.getBytes();
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to read uploaded image", exception);
		}
		if (!hasMatchingSignature(content, normalizedContentType)) {
			throw new BadRequestException("Image content does not match its declared type");
		}
		validateImage(content, normalizedContentType);

		String fileName = UUID.randomUUID() + extension;
		Path targetDirectory = storageRoot.resolve(folderName).normalize();
		Path targetFile = targetDirectory.resolve(fileName).normalize();
		if (!targetDirectory.startsWith(storageRoot) || !targetFile.startsWith(targetDirectory)) {
			throw new BadRequestException("Invalid media file path");
		}

		try {
			Files.createDirectories(storageRoot);
			Files.createDirectories(targetDirectory);
			Path realRoot = storageRoot.toRealPath();
			Path realDirectory = targetDirectory.toRealPath();
			if (!realDirectory.startsWith(realRoot)) {
				throw new BadRequestException("Invalid media storage directory");
			}
			Files.write(targetFile, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			return publicPath + "/" + folderName + "/" + fileName;
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to store uploaded image", exception);
		}
	}

	private void validateImage(byte[] content, String contentType) {
		try (ByteArrayInputStream input = new ByteArrayInputStream(content);
			 ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
			if (imageInput == null) {
				throw new BadRequestException("Uploaded file is not a valid image");
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
			if (!readers.hasNext()) {
				throw new BadRequestException("Uploaded file is not a valid image");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(imageInput, true, true);
				String format = reader.getFormatName().toLowerCase(Locale.ROOT);
				String expectedFormat = contentType.equals("image/jpeg")
						? "jpeg"
						: contentType.substring("image/".length());
				if (!format.equals(expectedFormat)
						&& !(expectedFormat.equals("jpeg") && format.equals("jpg"))) {
					throw new BadRequestException("Image content does not match its declared type");
				}
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width < 1 || height < 1 || (long) width * height > 40_000_000L) {
					throw new BadRequestException("Image dimensions exceed the allowed limit");
				}
			} finally {
				reader.dispose();
			}
		} catch (IOException exception) {
			throw new BadRequestException("Uploaded file is not a valid image");
		}
	}

	private boolean hasMatchingSignature(byte[] content, String contentType) {
		return switch (contentType) {
			case "image/jpeg" -> content.length >= 3
					&& (content[0] & 0xFF) == 0xFF
					&& (content[1] & 0xFF) == 0xD8
					&& (content[2] & 0xFF) == 0xFF;
			case "image/png" -> content.length >= 8
					&& (content[0] & 0xFF) == 0x89
					&& content[1] == 0x50
					&& content[2] == 0x4E
					&& content[3] == 0x47
					&& content[4] == 0x0D
					&& content[5] == 0x0A
					&& content[6] == 0x1A
					&& content[7] == 0x0A;
			case "image/gif" -> content.length >= 6
					&& content[0] == 'G'
					&& content[1] == 'I'
					&& content[2] == 'F'
					&& content[3] == '8'
					&& (content[4] == '7' || content[4] == '9')
					&& content[5] == 'a';
			default -> false;
		};
	}

	private String extensionFor(String contentType) {
		return switch (contentType.toLowerCase(Locale.ROOT)) {
			case "image/jpeg" -> ".jpg";
			case "image/png" -> ".png";
			case "image/gif" -> ".gif";
			default -> throw new BadRequestException("Unsupported image type");
		};
	}
}
