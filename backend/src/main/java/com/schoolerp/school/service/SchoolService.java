package com.schoolerp.school.service;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.NotFoundException;
import com.schoolerp.files.service.FileStorage;
import com.schoolerp.school.dto.SchoolProfileResponse;
import com.schoolerp.school.dto.UpdateSchoolProfileRequest;
import com.schoolerp.school.entity.School;
import com.schoolerp.school.entity.SchoolSettings;
import com.schoolerp.school.repository.SchoolRepository;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SchoolService {

	static final long MAX_LOGO_BYTES = 1024 * 1024;

	/** Stored logo file names: {@code logo-<epochMillis>.<ext>}. */
	static final Pattern LOGO_FILE = Pattern.compile("^logo-\\d+\\.(png|jpg|webp)$");

	private static final String LOGO_URL_PREFIX = "/api/v1/public/schools/";

	private final SchoolRepository schools;

	private final FileStorage storage;

	private final AuditService audit;

	private final Clock clock;

	public record Logo(byte[] content, String contentType) {
	}

	@Transactional(readOnly = true)
	public SchoolProfileResponse current() {
		return SchoolProfileResponse.of(requireCurrent());
	}

	@Transactional
	public SchoolProfileResponse updateProfile(UpdateSchoolProfileRequest request) {
		School school = requireCurrent();
		school.setName(request.name().trim());
		school.setBoard(request.board());
		school.setMedium(request.medium().trim().toUpperCase());
		school.setAddress(blankToNull(request.address()));
		school.setPhone(blankToNull(request.phone()));
		school.setEmail(blankToNull(request.email()));
		school.setPrimaryColor(request.primaryColor().toLowerCase());
		audit.log(AuditAction.UPDATE, "school", school.getId(), Map.of("section", "profile"));
		return SchoolProfileResponse.of(school);
	}

	@Transactional
	public SchoolProfileResponse updateSettings(SchoolSettings settings) {
		School school = requireCurrent();
		if (settings.weekOffDays().size() >= 7) {
			throw new BadRequestException("At least one working day is required");
		}
		school.setSettings(settings);
		audit.log(AuditAction.UPDATE, "school", school.getId(), Map.of("section", "settings"));
		return SchoolProfileResponse.of(school);
	}

	@Transactional
	public SchoolProfileResponse uploadLogo(MultipartFile file) {
		School school = requireCurrent();
		byte[] content = readLogo(file);
		String extension = imageExtension(content)
			.orElseThrow(() -> new BadRequestException("Logo must be a PNG, JPEG or WebP image"));
		String fileName = "logo-" + clock.millis() + "." + extension;
		storage.put(logoKey(school.getId(), fileName), content, contentType(extension));

		String previous = school.getLogoUrl();
		school.setLogoUrl(LOGO_URL_PREFIX + school.getCode() + "/logo/" + fileName);
		storedLogoFile(previous).ifPresent(old -> storage.delete(logoKey(school.getId(), old)));
		audit.log(AuditAction.UPDATE, "school", school.getId(), Map.of("section", "logo"));
		return SchoolProfileResponse.of(school);
	}

	/** Public: serves only the school's current logo, so old or guessed names 404. */
	@Transactional(readOnly = true)
	public Logo publicLogo(String code, String fileName) {
		if (!LOGO_FILE.matcher(fileName).matches()) {
			throw new NotFoundException("Logo");
		}
		School school = schools.findByCodeIgnoreCase(code)
			.filter(School::isActive)
			.filter(s -> storedLogoFile(s.getLogoUrl()).filter(fileName::equals).isPresent())
			.orElseThrow(() -> new NotFoundException("Logo"));
		byte[] content = storage.get(logoKey(school.getId(), fileName))
			.orElseThrow(() -> new NotFoundException("Logo"));
		return new Logo(content, contentType(fileName.substring(fileName.lastIndexOf('.') + 1)));
	}

	public School requireCurrent() {
		return schools.findById(TenantContext.require()).orElseThrow(() -> new NotFoundException("School"));
	}

	private static byte[] readLogo(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("Choose an image to upload");
		}
		if (file.getSize() > MAX_LOGO_BYTES) {
			throw new BadRequestException("Logo must be 1 MB or smaller");
		}
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new BadRequestException("Could not read the uploaded file");
		}
	}

	/** Detects the image type from its first bytes rather than trusting the file name. */
	static Optional<String> imageExtension(byte[] b) {
		if (startsWith(b, 0, 0x89, 'P', 'N', 'G')) {
			return Optional.of("png");
		}
		if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
			return Optional.of("jpg");
		}
		if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) {
			return Optional.of("webp");
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] b, int offset, int... expected) {
		if (b.length < offset + expected.length) {
			return false;
		}
		for (int i = 0; i < expected.length; i++) {
			if ((b[offset + i] & 0xFF) != expected[i]) {
				return false;
			}
		}
		return true;
	}

	private static Optional<String> storedLogoFile(String logoUrl) {
		if (logoUrl == null || !logoUrl.startsWith(LOGO_URL_PREFIX)) {
			return Optional.empty();
		}
		String file = logoUrl.substring(logoUrl.lastIndexOf('/') + 1);
		return LOGO_FILE.matcher(file).matches() ? Optional.of(file) : Optional.empty();
	}

	private static String logoKey(Long schoolId, String fileName) {
		return "schools/" + schoolId + "/" + fileName;
	}

	private static String contentType(String extension) {
		return switch (extension) {
			case "png" -> "image/png";
			case "jpg" -> "image/jpeg";
			default -> "image/webp";
		};
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
