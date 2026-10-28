package com.RealTimeCollaboration.RealTime.controller.fileController;

import com.RealTimeCollaboration.RealTime.DTOs.chatMessageDTO.charMessageEntity;
import com.RealTimeCollaboration.RealTime.DTOs.fileDTOs.*;
import com.RealTimeCollaboration.RealTime.model.file.fileMetaData;
import com.RealTimeCollaboration.RealTime.repo.chatMessage.chatMessageRepo;
import com.RealTimeCollaboration.RealTime.repo.file.fileMetaDataRepo;
import com.RealTimeCollaboration.RealTime.service.activity.activityService;
import com.RealTimeCollaboration.RealTime.service.file.fileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.DeclareError;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.security.auth.x500.X500Principal;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class fileController {
    private final activityService service;
    private final fileService fileService;
    private final fileMetaDataRepo fileMetaDataRepo;
    private final chatMessageRepo chatMessageRepo;
    private final SimpMessagingTemplate simpMessagingTemplate;


    @PostMapping("/upload/init")
    public ResponseEntity<?> uploadInIt(@RequestBody uploadInItRequest request, Principal principal) {
        String fileId = fileService.initChunk(principal, request);
        return ResponseEntity.ok(Map.of("fileId", fileId));


    }

    @PostMapping("/upload/chunk")
    public ResponseEntity<?> uploadChunk(


            @RequestParam("fileId")
            String fileId,

            @RequestParam("chunkIndex")
            Integer chunkIndex,

            @RequestParam("file")
            MultipartFile file
    ) {
        System.out.println("UPLOAD CHUNK CONTROLLER HIT");

        fileService.uploadChunk(chunkIndex, fileId, file);

        return ResponseEntity.ok("uploadChunk");


    }

    @PostMapping("/upload/complete/{fileId}")
    public ResponseEntity<?> complete(@PathVariable String fileId) throws FileNotFoundException {


        fileService.uploadComplete(fileId);
        fileMetaData fileMetaData = fileMetaDataRepo.findByFileId(fileId);

        charMessageEntity charMessageEntity = new charMessageEntity();

        charMessageEntity.setRoomCode(fileMetaData.getRoomCode());

        charMessageEntity.setSender(fileMetaData.getUploadBy());
        charMessageEntity.setMessageType("FILE");

        charMessageEntity.setFileId(fileMetaData.getFileId());

        charMessageEntity.setFileName(fileMetaData.getFileName());

        charMessageEntity.setSendAt(LocalDateTime.now());

        chatMessageRepo.save(charMessageEntity);

        simpMessagingTemplate.convertAndSend("/topic/room/" + fileMetaData.getRoomCode(), charMessageEntity);


        return ResponseEntity.ok("uploadComplete");
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) throws IOException {

        fileMetaData fileMetaData = fileMetaDataRepo.findByFileId(fileId);


        Resource resource = fileService.download(fileId);
        String contentType =
                Files.probeContentType(
                        Paths.get(
                                fileMetaData.getStoragePath()
                        )
                );

        if (contentType == null) {
            contentType = "application/octet-stream";
        }


        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        contentType
                ))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileMetaData.getFileName() + "\"")
                .body(resource);


    }

    /** Download one source file from the room workspace. */
    @GetMapping("/room/{roomCode}/workspace/{filename}/download")
    public ResponseEntity<byte[]> downloadWorkspaceFile(
            @PathVariable String roomCode,
            @PathVariable String filename
    ) throws IOException {
        String content = fileService.openFile(roomCode, filename);
        String safeName = filename.replace("\r", "").replace("\n", "").replace("\"", "");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .body(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Download every regular file in the room workspace as a ZIP archive. */
    @GetMapping("/room/{roomCode}/workspace/download-zip")
    public ResponseEntity<byte[]> downloadWorkspaceZip(@PathVariable String roomCode) throws IOException {
        List<String> fileNames = fileService.listFiles(roomCode);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(bytes, java.nio.charset.StandardCharsets.UTF_8)) {
            for (String fileName : fileNames) {
                // Workspace files are flat; reject names that could create ZIP paths.
                if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.equals(".") || fileName.equals("..")) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(fileName));
                zip.write(fileService.openFile(roomCode, fileName).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }

        String safeRoomCode = roomCode.replaceAll("[^A-Za-z0-9_-]", "_");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"workspace-" + safeRoomCode + ".zip\"")
                .body(bytes.toByteArray());
    }

    @GetMapping("/room/{roomCode}/files")
    public ResponseEntity<?> getFiles(@PathVariable String roomCode) {
        return ResponseEntity.ok(fileService.getFiles(roomCode));
    }

    @GetMapping("/file/{id}/content")
    public ResponseEntity<String> getFileContent(@PathVariable String id) throws IOException {
        return ResponseEntity.ok(fileService.getFileContent(id));
    }

    //editor file options----->
//creating file in editor------>
    @PostMapping("/room/{roomCode}/files")
    public ResponseEntity<?> createFile(@PathVariable String roomCode, @Valid @RequestBody fileRequestDtos fileRequestDtos, Principal principal) throws IOException {


        fileService.createFile(roomCode, fileRequestDtos.getFileName());

        simpMessagingTemplate.convertAndSend("/topic/files/" + roomCode,

                new fileEventDto(roomCode, "CREATE",
                        fileRequestDtos.getFileName(),
                        principal.getName(),
                        null));
        service.record(roomCode, principal.getName(), "CREATE_FILE",fileRequestDtos.getFileName());


        return ResponseEntity.ok(Map.of("message", "file created successfully",
                "filename", fileRequestDtos.getFileName()));
    }

    @GetMapping("/room/{roomCode}/allFiles")
    public ResponseEntity<List<String>> getWorkspaceFiles(@PathVariable String roomCode) throws IOException {
        return ResponseEntity.ok(fileService.listFiles(roomCode));
    }

    @GetMapping("/room/{roomCode}/workspace/{filename}")
    public ResponseEntity<String> getContent(@PathVariable String roomCode, @PathVariable String filename) throws IOException {
        return ResponseEntity.ok(fileService.openFile(roomCode, filename));

    }

    @PostMapping("/room/{roomCode}/workspace/{filename}")
    public ResponseEntity<?> saveContent(@PathVariable String roomCode, @PathVariable String filename, @RequestBody saveFileDtos save) throws IOException {

        fileService.saveContent(roomCode, filename, save.getContent());

        return ResponseEntity.ok("saved successfully");

    }

    //rename the file----------->
    @PutMapping("/room/{roomCode}/workspace/rename")
    public ResponseEntity<?> rename(@PathVariable String roomCode, @RequestBody renameFileDto renameFileDto, Principal principal) throws IOException {
        System.out.println("RENAME API HIT");

        fileService.renameFile(roomCode, renameFileDto.getOldFileName(), renameFileDto.getNewFileName());
        simpMessagingTemplate.convertAndSend("/topic/files/" +roomCode,new fileEventDto(
                roomCode,
                "RENAME",
                renameFileDto.getNewFileName(),
                principal.getName(),
                renameFileDto.getOldFileName()
        ));
        service.record(
                roomCode,
                principal.getName(),
                "RENAME_FILE",
                renameFileDto.getOldFileName() + " → " + renameFileDto.getNewFileName()
        );

        return ResponseEntity.ok("File rename Successfully");
    }


    //delete file --------->
    @DeleteMapping("/room/{roomCode}/workspace/{fileName}")
    public ResponseEntity<?> deleteFile(@PathVariable String roomCode, @PathVariable String fileName,Principal principal) throws IOException {
        fileService.deleteFile(roomCode, fileName);
        simpMessagingTemplate.convertAndSend("/topic/files/" +roomCode,new fileEventDto(
                roomCode,
                "DELETE",
                fileName,
                principal.getName(),
                null
        ));
        service.record(roomCode, principal.getName(), "DELETE_FILE",fileName);

        return ResponseEntity.ok("File deleted successfully");

    }

    //copy file---------->
    @PostMapping("/room/{roomCode}/workspace/copy")
    public ResponseEntity<?> copyFile(@PathVariable String roomCode, @RequestBody copyFileDDto copyFileDDto,Principal principal) throws IOException {

        String copiedName = fileService.copyFile(roomCode, copyFileDDto.getSourceFile(), copyFileDDto.getTargetFile());
        simpMessagingTemplate.convertAndSend("/topic/files/" +roomCode,new fileEventDto(
                roomCode,
                "COPY",
               copiedName,
                principal.getName(),
                copyFileDDto.getSourceFile()
        ));
        service.record(
                roomCode,
                principal.getName(),
                "COPY_FILE",
                copyFileDDto.getSourceFile() + " → " + copiedName
        );
        return ResponseEntity.ok(
                Map.of("fileName", copiedName));
    }

    //move file --------->

    @PutMapping("/room/{roomCode}/workspace/move")
    public ResponseEntity<?> moveFile(
            @PathVariable String roomCode,
            @RequestBody moveFIleDto dto,Principal principal) throws IOException {

        String fileName = fileService.moveFile(
                roomCode,
                dto.getSourceFile(),
                dto.getTargetFile()
        );
        simpMessagingTemplate.convertAndSend("/topic/files/" +roomCode,new fileEventDto(
                roomCode,
                "MOVE",
                fileName,
                principal.getName(),
                dto.getSourceFile()
        ));
        service.record(
                roomCode,
                principal.getName(),
                "MOVE_FILE",
                dto.getSourceFile() + " → " + fileName
        );


        return ResponseEntity.ok(
                Map.of(
                        "message", "File moved successfully",
                        "fileName", fileName
                )
        );
    }
}
