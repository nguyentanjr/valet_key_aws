package com.example.valetkey.controller;

import com.example.valetkey.model.Resource;
import com.example.valetkey.model.User;
import com.example.valetkey.repository.ResourceRepository;
import com.example.valetkey.repository.UserRepository;
import com.example.valetkey.service.FileService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    /**
     * Generate presigned URL for uploading a file
     */
    @PostMapping("/upload-url")
    public ResponseEntity<?> generateUploadUrl(
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            String fileName = (String) request.get("fileName");
            Long fileSize = request.get("fileSize") != null 
                ? Long.valueOf(request.get("fileSize").toString()) 
                : null;
            Long folderId = request.get("folderId") != null 
                ? Long.valueOf(request.get("folderId").toString()) 
                : null;

            if (fileName == null || fileSize == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "fileName and fileSize are required"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Map<String, Object> uploadInfo = fileService.generateUploadUrl(fileName, fileSize, folderId, user);

            return ResponseEntity.ok(uploadInfo);

        } catch (Exception e) {
            log.error("Error generating upload URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Failed to generate upload URL: " + e.getMessage()));
        }
    }

    /**
     * Confirm file upload completion (after client uploads directly to S3)
     */
    @PostMapping("/upload/confirm")
    public ResponseEntity<?> confirmUpload(
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            Long fileId = request.get("fileId") != null 
                ? Long.valueOf(request.get("fileId").toString()) 
                : null;
            String contentType = (String) request.get("contentType");

            if (fileId == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "fileId is required"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Resource resource = fileService.confirmUpload(fileId, contentType, user);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "File uploaded successfully");
            response.put("file", fileToMap(resource));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error confirming upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Failed to confirm upload: " + e.getMessage()));
        }
    }

    /**
     * Get file metadata
     */
    @GetMapping("/{fileId:\\d+}")
    public ResponseEntity<?> getFile(@PathVariable Long fileId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Map<String, Object> metadata = fileService.getFileMetadata(fileId, user);

            return ResponseEntity.ok(metadata);

        } catch (Exception e) {
            log.error("Error getting file", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Get download URL for a file
     */
    @GetMapping("/{fileId:\\d+}/download")
    public ResponseEntity<?> getDownloadUrl(@PathVariable Long fileId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            String downloadUrl = fileService.getDownloadUrl(fileId, user);

            return ResponseEntity.ok(Map.of(
                "downloadUrl", downloadUrl,
                "expiresInMinutes", 10
            ));

        } catch (Exception e) {
            log.error("Error generating download URL", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Delete a file
     */
    @DeleteMapping("/{fileId:\\d+}")
    public ResponseEntity<?> deleteFile(@PathVariable Long fileId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            fileService.deleteFile(fileId, user);

            return ResponseEntity.ok(Map.of("message", "File deleted successfully"));

        } catch (Exception e) {
            log.error("Error deleting file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Get all file IDs for current user (no pagination, for select all)
     */
    @GetMapping("/all-ids")
    public ResponseEntity<?> getAllFileIds(
            @RequestParam(value = "folderId", required = false) Long folderId,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            List<Resource> allFiles = fileService.getAllFiles(user, folderId);
            List<Long> fileIds = allFiles.stream()
                .map(Resource::getId)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "fileIds", fileIds,
                "count", fileIds.size()
            ));

        } catch (Exception e) {
            log.error("Error getting all file IDs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * List files in a folder (with pagination)
     */
    @GetMapping("/list")
    public ResponseEntity<?> listFiles(
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Page<Resource> filesPage = fileService.listFiles(user, folderId, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("files", filesPage.getContent().stream()
                .map(this::fileToMap)
                .collect(Collectors.toList()));
            response.put("currentPage", filesPage.getNumber());
            response.put("totalPages", filesPage.getTotalPages());
            response.put("totalItems", filesPage.getTotalElements());
            response.put("hasNext", filesPage.hasNext());
            response.put("hasPrevious", filesPage.hasPrevious());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error listing files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Search files by name
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchFiles(
            @RequestParam("query") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Page<Resource> filesPage = fileService.searchFiles(user, query, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("files", filesPage.getContent().stream()
                .map(this::fileToMap)
                .collect(Collectors.toList()));
            response.put("currentPage", filesPage.getNumber());
            response.put("totalPages", filesPage.getTotalPages());
            response.put("totalItems", filesPage.getTotalElements());
            response.put("query", query);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error searching files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Move file to another folder
     */
    @PutMapping("/{fileId:\\d+}/move")
    public ResponseEntity<?> moveFile(
            @PathVariable Long fileId,
            @RequestParam(value = "targetFolderId", required = false) Long targetFolderId,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Resource resource = fileService.moveFile(fileId, targetFolderId, user);

            return ResponseEntity.ok(Map.of(
                "message", "File moved successfully",
                "file", fileToMap(resource)
            ));

        } catch (Exception e) {
            log.error("Error moving file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Rename a file
     */
    @PutMapping("/{fileId:\\d+}/rename")
    public ResponseEntity<?> renameFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, String> request,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            String newName = request.get("newName");
            if (newName == null || newName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "New name is required"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Resource resource = fileService.renameFile(fileId, newName, user);

            return ResponseEntity.ok(Map.of(
                "message", "File renamed successfully",
                "file", fileToMap(resource)
            ));

        } catch (Exception e) {
            log.error("Error renaming file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Generate public sharing link
     */
    @PostMapping("/{fileId:\\d+}/share")
    public ResponseEntity<?> generatePublicLink(@PathVariable Long fileId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            String token = fileService.generatePublicLink(fileId, user);

            return ResponseEntity.ok(Map.of(
                "message", "Public link generated successfully",
                "publicLinkToken", token,
                "publicUrl", "/api/public/files/" + token
            ));

        } catch (Exception e) {
            log.error("Error generating public link", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Revoke public sharing link
     */
    @DeleteMapping("/{fileId:\\d+}/share")
    public ResponseEntity<?> revokePublicLink(@PathVariable Long fileId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            fileService.revokePublicLink(fileId, user);

            return ResponseEntity.ok(Map.of("message", "Public link revoked successfully"));

        } catch (Exception e) {
            log.error("Error revoking public link", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Get user storage info
     */
    @GetMapping("/storage")
    public ResponseEntity<?> getStorageInfo(HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Map<String, Object> storageInfo = fileService.getUserStorageInfo(user);

            return ResponseEntity.ok(storageInfo);

        } catch (Exception e) {
            log.error("Error getting storage info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }


    /**
     * Bulk delete files
     */
    @PostMapping("/bulk-delete")
    public ResponseEntity<?> bulkDeleteFiles(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            @SuppressWarnings("unchecked")
            List<Object> fileIdsObj = (List<Object>) request.get("fileIds");
            if (fileIdsObj == null || fileIdsObj.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "fileIds is required"));
            }

            // Convert to List<Long> handling both Integer and Long
            List<Long> fileIds = fileIdsObj.stream()
                .map(id -> {
                    if (id instanceof Long) {
                        return (Long) id;
                    } else if (id instanceof Integer) {
                        return ((Integer) id).longValue();
                    } else if (id instanceof Number) {
                        return ((Number) id).longValue();
                    } else {
                        return Long.valueOf(id.toString());
                    }
                })
                .collect(java.util.stream.Collectors.toList());

            User user = userRepository.getUserById(sessionUser.getId());
            fileService.bulkDeleteFiles(fileIds, user);

            return ResponseEntity.ok(Map.of(
                "message", "Files deleted successfully",
                "count", fileIds.size()
            ));

        } catch (Exception e) {
            log.error("Error bulk deleting files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Bulk move files
     */
    @PostMapping("/bulk-move")
    public ResponseEntity<?> bulkMoveFiles(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            @SuppressWarnings("unchecked")
            List<Object> fileIdsObj = (List<Object>) request.get("fileIds");
            if (fileIdsObj == null || fileIdsObj.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "fileIds is required"));
            }

            // Convert to List<Long> handling both Integer and Long
            List<Long> fileIds = fileIdsObj.stream()
                .map(id -> {
                    if (id instanceof Long) {
                        return (Long) id;
                    } else if (id instanceof Integer) {
                        return ((Integer) id).longValue();
                    } else if (id instanceof Number) {
                        return ((Number) id).longValue();
                    } else {
                        return Long.valueOf(id.toString());
                    }
                })
                .collect(java.util.stream.Collectors.toList());

            Long targetFolderId = null;
            if (request.get("targetFolderId") != null) {
                Object targetIdObj = request.get("targetFolderId");
                if (targetIdObj instanceof Long) {
                    targetFolderId = (Long) targetIdObj;
                } else if (targetIdObj instanceof Integer) {
                    targetFolderId = ((Integer) targetIdObj).longValue();
                } else if (targetIdObj instanceof Number) {
                    targetFolderId = ((Number) targetIdObj).longValue();
                } else {
                    targetFolderId = Long.valueOf(targetIdObj.toString());
                }
            }

            User user = userRepository.getUserById(sessionUser.getId());
            fileService.bulkMoveFiles(fileIds, targetFolderId, user);

            return ResponseEntity.ok(Map.of(
                "message", "Files moved successfully",
                "count", fileIds.size()
            ));

        } catch (Exception e) {
            log.error("Error bulk moving files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }


    // Helper method
    private Map<String, Object> fileToMap(Resource resource) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", resource.getId());
        map.put("fileName", resource.getFileName());
        map.put("originalName", resource.getOriginalName());
        map.put("fileSize", resource.getFileSize());
        map.put("contentType", resource.getContentType());
        map.put("uploadedAt", resource.getUploadedAt());
        map.put("lastModified", resource.getLastModified());
        map.put("isPublic", resource.isPublic());

        if (resource.getFolder() != null) {
            map.put("folderId", resource.getFolder().getId());
            map.put("folderName", resource.getFolder().getName());
            map.put("folderPath", resource.getFolder().getFullPath());
        }

        return map;
    }
}

