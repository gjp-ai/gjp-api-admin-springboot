package org.ganjp.api.cms.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import org.ganjp.api.common.util.CmsUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FileService {
    private final FileRepository fileRepository;
    private final FileUploadProperties uploadProperties; // file upload config

    public FileResponse createFile(FileCreateRequest request, String userId) {
        FileAsset f = new FileAsset();
        String id = UUID.randomUUID().toString();
        f.setId(id);
        f.setName(request.getName());
        f.setOriginalUrl(request.getOriginalUrl());
        f.setSourceName(request.getSourceName());
        f.setTags(request.getTags());
        if (request.getLang() != null) f.setLang(request.getLang());
        if (request.getDisplayOrder() != null) f.setDisplayOrder(request.getDisplayOrder());

        try {
            String baseDir = uploadProperties.getDirectory();
            Path filesDir = Path.of(baseDir);
            Files.createDirectories(filesDir);

            if (request.getFile() != null && !request.getFile().isEmpty()) {
                MultipartFile mf = request.getFile();
                String orig = mf.getOriginalFilename();
                String stored = (request.getFilename() != null && !request.getFilename().isBlank()) ? request.getFilename() : (orig == null ? System.currentTimeMillis()+"-file" : orig.replaceAll("\\s+", "-"));
                Path target = CmsUtil.resolveSecurePath(baseDir, stored);
                int suffix = 1;
                String base = stored;
                String ext = "";
                int dot = stored.lastIndexOf('.');
                if (dot > 0) { base = stored.substring(0, dot); ext = stored.substring(dot); }
                while (Files.exists(target)) {
                    stored = base + "-" + suffix + ext;
                    target = CmsUtil.resolveSecurePath(baseDir, stored);
                    suffix++;
                }
                Files.copy(mf.getInputStream(), target);
                f.setFilename(stored);
                f.setSizeBytes(mf.getSize());
                if (dot > 0 && dot < stored.length()-1) f.setExtension(stored.substring(dot+1));
                f.setMimeType(mf.getContentType());
            } else if (request.getOriginalUrl() != null && !request.getOriginalUrl().isBlank()) {
                String url = request.getOriginalUrl();
                String stored = request.getFilename();
                if (stored == null || stored.isBlank()) {
                    try { java.net.URL u = new java.net.URL(url); String p = u.getPath(); int last = p.lastIndexOf('/'); String lastSeg = last>=0? p.substring(last+1): p; if (lastSeg==null||lastSeg.isBlank()) lastSeg = System.currentTimeMillis()+"-file"; stored = lastSeg.replaceAll("\\s+","-"); } catch (Exception ex) { stored = System.currentTimeMillis()+"-file"; }
                }
                Path target = CmsUtil.resolveSecurePath(baseDir, stored);
                int dot = stored.lastIndexOf('.');
                // When downloading from an external originalUrl, do not auto-rename if the file already exists.
                // Throw an error to let the caller decide how to handle duplicates.
                try (java.io.InputStream is = new java.net.URL(url).openStream()) {
                    byte[] data = is.readAllBytes();
                    Files.write(target, data, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
                    f.setSizeBytes((long) data.length);
                    if (dot>0 && dot < stored.length()-1) f.setExtension(stored.substring(dot+1));
                } catch (java.nio.file.FileAlreadyExistsException e) {
                    throw new IllegalArgumentException("File already exists: " + stored);
                }
                f.setFilename(stored);
                f.setOriginalUrl(request.getOriginalUrl());
            } else if (request.getFilename() != null) {
                f.setFilename(request.getFilename());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to save file: " + e.getMessage());
        }

        f.setCreatedBy(userId);
        f.setUpdatedBy(userId);

        FileAsset saved = fileRepository.save(f);
        return toResponse(saved);
    }

    public FileResponse updateFile(String id, FileUpdateRequest request, String userId) {
        FileAsset f = fileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File", "id", id));
        if (request.getName() != null) f.setName(request.getName());
        if (request.getOriginalUrl() != null) f.setOriginalUrl(request.getOriginalUrl());
        if (request.getSourceName() != null) f.setSourceName(request.getSourceName());
        if (request.getFilename() != null) f.setFilename(request.getFilename());

        try {
            String baseDir = uploadProperties.getDirectory();
            Path filesDir = Path.of(baseDir);
            Files.createDirectories(filesDir);

            if (request.getFile() != null && !request.getFile().isEmpty()) {
                MultipartFile mf = request.getFile();
                String orig = mf.getOriginalFilename();
                String stored = (request.getFilename() != null && !request.getFilename().isBlank()) ? request.getFilename() : (orig == null ? System.currentTimeMillis()+"-file" : orig.replaceAll("\\s+", "-"));
                Path target = CmsUtil.resolveSecurePath(baseDir, stored);
                int suffix = 1;
                String base = stored;
                String ext = "";
                int dot = stored.lastIndexOf('.');
                if (dot > 0) { base = stored.substring(0, dot); ext = stored.substring(dot); }
                while (Files.exists(target)) {
                    stored = base + "-" + suffix + ext;
                    target = CmsUtil.resolveSecurePath(baseDir, stored);
                    suffix++;
                }
                if (f.getFilename() != null) { try { Path old = CmsUtil.resolveSecurePath(baseDir, f.getFilename()); Files.deleteIfExists(old); } catch (IOException ignored) {} }
                Files.copy(mf.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
                f.setFilename(stored);
                f.setSizeBytes(mf.getSize());
                if (dot > 0 && dot < stored.length()-1) f.setExtension(stored.substring(dot+1));
                f.setMimeType(mf.getContentType());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to save file: " + e.getMessage());
        }

        if (request.getTags() != null) f.setTags(request.getTags());
        if (request.getLang() != null) f.setLang(request.getLang());
        if (request.getDisplayOrder() != null) f.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) f.setIsActive(request.getIsActive());

        f.setUpdatedBy(userId);
        FileAsset saved = fileRepository.save(f);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FileResponse getFileById(String id) {
        FileAsset f = fileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File", "id", id));
        return toResponse(f);
    }

    @Transactional(readOnly = true)
    public java.io.File getFileByFilename(String filename) {
        if (filename == null) throw new IllegalArgumentException("filename is null");
        Path p = CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename);
        if (!Files.exists(p)) throw new ResourceNotFoundException("File", "filename", filename);
        return p.toFile();
    }

    @Transactional(readOnly = true)
    public java.util.List<FileResponse> listFiles() {
        List<FileAsset> all = fileRepository.findAll();
        return all.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<FileResponse> searchFiles(String name, FileAsset.Language lang, String tags, Boolean isActive) {
        List<FileAsset> list = fileRepository.searchFiles(name, lang, tags, isActive);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<FileResponse> searchFiles(String name, FileAsset.Language lang, String tags, Boolean isActive, Pageable pageable) {
        return fileRepository.searchFiles(name, lang, tags, isActive, pageable).map(this::toResponse);
    }

    public void deleteFile(String id, String userId) {
        FileAsset f = fileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File", "id", id));
        f.setIsActive(false);
        f.setUpdatedBy(userId);
        fileRepository.save(f);
        log.info("File soft deleted: {} by user: {}", id, userId);
    }

    public void permanentlyDeleteFile(String id) {
        FileAsset f = fileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File", "id", id));
        String filename = f.getFilename();
        fileRepository.delete(f);
        if (filename != null) {
            try {
                Files.deleteIfExists(CmsUtil.resolveSecurePath(uploadProperties.getDirectory(), filename));
            } catch (IOException e) {
                log.error("Failed to delete file for file asset: {}", id, e);
            }
        }
        log.info("File permanently deleted: {}", id);
    }

    private FileResponse toResponse(FileAsset f) {
        return FileResponse.from(f);
    }
}
