package com.staytrack.service;

import com.staytrack.config.AppProperties;
import com.staytrack.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/** Sinasave sa disk yung upload tapos ibinabalik ang URL. */
@Service
public class StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
        Set.of("image/png", "image/jpeg", "image/jpg", "image/webp");

    private static final Set<String> ALLOWED_DOCUMENT_TYPES =
        Set.of("application/pdf");

    private final AppProperties props;

    public StorageService(AppProperties props) {
        this.props = props;
    }

    /** I-save yung file sa folder tapos ibalik ang public URL. */
    public String store(MultipartFile file, String subdir) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType().toLowerCase())) {
            throw ApiException.badRequest("Only PNG, JPG or WEBP images are allowed.");
        }
        long maxBytes = props.getMaxUploadMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw ApiException.badRequest("The uploaded file is larger than " + props.getMaxUploadMb() + " MB.");
        }

        try {
            Path dir = Paths.get(props.getUploadDir(), subdir).toAbsolutePath().normalize();
            Files.createDirectories(dir);

            String extension = extensionOf(file.getOriginalFilename());
            String filename = UUID.randomUUID() + extension;
            Path target = dir.resolve(filename);
            file.transferTo(target);

            return "/uploads/" + subdir + "/" + filename;
        } catch (IOException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not save the uploaded file.");
        }
    }

    /** I-save yung PDF contract tapos ibalik ang public URL. */
    public String storePdf(MultipartFile file, String subdir) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Please choose a PDF file to upload.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_DOCUMENT_TYPES.contains(type)) {
            throw ApiException.badRequest("Only PDF files are allowed for the contract.");
        }
        long maxBytes = props.getMaxUploadMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw ApiException.badRequest("The uploaded file is larger than " + props.getMaxUploadMb() + " MB.");
        }

        try {
            Path dir = Paths.get(props.getUploadDir(), subdir).toAbsolutePath().normalize();
            Files.createDirectories(dir);

            String filename = UUID.randomUUID() + ".pdf";
            Path target = dir.resolve(filename);
            file.transferTo(target);

            return "/uploads/" + subdir + "/" + filename;
        } catch (IOException ex) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not save the uploaded file.");
        }
    }

    private String extensionOf(String originalName) {
        if (originalName == null) {
            return ".png";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return ".png";
        }
        String ext = originalName.substring(dot).toLowerCase();
        return ext.matches("\\.(png|jpg|jpeg|webp)") ? ext : ".png";
    }
}
