package com.ironwill.core.service;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final Storage storage;

    @Value("${gcp.storage.bucket}")
    private String bucketName;

    public String uploadProof(UUID userId, UUID goalId, MultipartFile file) throws IOException {
        String hash = sha256Hex(file.getBytes());
        String objectName = String.format("users/%s/goals/%s/%s_%s", userId, goalId, LocalDate.now(), hash);
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType(file.getContentType())
                .build();
        storage.create(blobInfo, file.getBytes());
        return String.format("gs://%s/%s", bucketName, objectName);
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

