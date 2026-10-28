package com.RealTimeCollaboration.RealTime.service.file;

import com.RealTimeCollaboration.RealTime.DTOs.fileDTOs.uploadInItRequest;
import com.RealTimeCollaboration.RealTime.model.file.fileMetaData;
import com.RealTimeCollaboration.RealTime.repo.file.fileMetaDataRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class fileService {

    private final fileMetaDataRepo fileMetaDataRepo;
//    private final String Final_dir = "/app/uploads";
//    private final String Temp_dir = "temp/";
    private final String Temp_dir =
            System.getProperty("user.dir")
                    + File.separator
                    + "temp";

    private final String Final_dir =
            "/app/uploads";
    private final String WORKSPACE = Final_dir + File.separator + "/workspace";


    public String initChunk(Principal principal, uploadInItRequest request) {
        String fileId = UUID.randomUUID().toString();
        String storedFileName = UUID.randomUUID()+"_"+request.getFileName();
        fileMetaData fileMetaData = com.RealTimeCollaboration.RealTime.model.file.fileMetaData.builder()
                .fileId(fileId)
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .uploadComplete(false)
                .totalChunks(request.getTotalChunks())
                .uploadBy(principal.getName())
                .roomCode(request.getRoomCode())
                .storagePath(storedFileName)


                .build();

//        File file = new File(Temp_dir+fileId);
        File file = new File(
                Temp_dir
                        + File.separator
                        + fileId
        );
        if (!file.exists())
        {
            file.mkdirs();
        }
        fileMetaDataRepo.save(fileMetaData);
        return fileId;


    }

    public void uploadChunk(
            Integer chunkIndex,
            String fileId,
            MultipartFile file
    ) {

        try {

            Path chunkPath = Paths.get(
                    Temp_dir,
                    fileId,
                    "chunk_" + chunkIndex
            );

            Files.createDirectories(
                    chunkPath.getParent()
            );

            file.transferTo(
                    chunkPath.toFile()
            );

            System.out.println(
                    "CHUNK SAVED: " +
                            chunkPath
            );

        } catch (Exception e) {

            e.printStackTrace();

            throw new RuntimeException(
                    "upload failed",
                    e
            );
        }
    }

    public void uploadComplete(String fileId) throws FileNotFoundException {
        fileMetaData fileMetaData = fileMetaDataRepo.findByFileId(fileId);
        if (fileMetaData== null)
        {
            throw new RuntimeException("File metadata not found");
        }
        File uploadDir = new File(Final_dir);

        if (!uploadDir.exists())
        {
            uploadDir.mkdirs();
        }

        File finalFinal = new File(Final_dir+File.separator+fileMetaData.getStoragePath());
        try(FileOutputStream fos = new FileOutputStream(finalFinal))
        {

            for (int i=0;i<fileMetaData.getTotalChunks();i++)
            {
//                String basePath = System.getProperty("user.dir");
                File chunk = Paths.get(
                        Temp_dir,
                        fileId,
                        "chunk_" + i
                ).toFile();
                if (!chunk.exists()) {
                    throw new RuntimeException(
                            "Missing chunk: " + i
                    );
                }
                fos.write(Files.readAllBytes(chunk.toPath()));
                chunk.delete();
            }
//            File tempFolder = new File(Temp_dir+fileId);
            File tempFolder = new File(
                    Temp_dir
                            + File.separator
                            + fileId
            );
            tempFolder.delete();
            fileMetaData.setUploadComplete(true);
            fileMetaData.setStoragePath(finalFinal.getAbsolutePath());
            fileMetaDataRepo.save(fileMetaData);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Resource download(String fileId) throws MalformedURLException
    {
        fileMetaData fileMetaData = fileMetaDataRepo.findByFileId(fileId);
        if (fileMetaData == null)
        {
            throw new RuntimeException("File not found");
        }
        Path path = Paths.get(fileMetaData.getStoragePath());
        return new UrlResource(path.toUri());
    }
    
    public List<fileMetaData> getFiles(String roomCode)
    {
        return fileMetaDataRepo.findByRoomCode(roomCode);
    }


    public String getFileContent(String id) throws IOException
    {
        System.out.println("Requested fileId = " + id);
        fileMetaData fileMetaData = fileMetaDataRepo.findByFileId(id);
        Path path = Paths.get(fileMetaData.getStoragePath());
        return Files.readString(path);
    }


    public void createFile(String roomCode, String fileName) throws IOException {

        Path roomPath = Paths.get(WORKSPACE, roomCode);

        Files.createDirectories(roomPath);

        Path filePath = roomPath.resolve(fileName);

        if (Files.exists(filePath)) {
            throw new RuntimeException("File already exists");
        }

        Files.createFile(filePath);
    }

    public List<String> listFiles(String roomCode) throws IOException
    {
        Path roomPath = Paths.get(WORKSPACE,roomCode);

        if (!Files.exists(roomPath))
        {
            return List.of();
        }

        try(var streams = Files.list(roomPath))
        {
            return streams
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    public String openFile(String roomCode,String fileName) throws IOException
    {
        Path filePath = Paths.get(WORKSPACE,roomCode,fileName);

        if (!Files.exists(filePath))
        {
            throw new RuntimeException("File not Found");
        }

        return Files.readString(filePath);
    }

    public void saveContent(String roomCode,String fileName,String content) throws IOException
    {
        Path save = Paths.get(WORKSPACE,roomCode,fileName);
        if (!Files.exists(save))
        {
            throw new RuntimeException("File not Found");
        }

         Files.writeString(save,content);

    }


    public  void  renameFile(String roomCode,String oldFilename,String newIFileName)throws IOException
    {
        Path room = Paths.get(WORKSPACE,roomCode);
        Path source = room.resolve(oldFilename);
        Path target = room.resolve(newIFileName);

        if (!Files.exists(source)) {
            throw new RuntimeException("File not found");
        }

        if (Files.exists(target)) {
            throw new RuntimeException("File already exists");
        }

        Files.move(source,target);

    }


    public void deleteFile(String roomCode, String fileName) throws IOException {

        Path file = Paths
                .get(WORKSPACE,roomCode)
                .resolve(fileName);

        if (!Files.exists(file)) {
            throw new RuntimeException("File not found");
        }

        Files.delete(file);


    }


    public String copyFile(String roomCode, String sourceFile, String targetFile) throws IOException {
        Path copy = Paths.get(WORKSPACE,roomCode);
        Path source = copy.resolve(sourceFile);


        if (!Files.exists(source)) {
            throw new RuntimeException("File not found");
        }

        if (targetFile == null || targetFile.isBlank()) {
            targetFile = generateCopyName(copy, sourceFile);
        }

        Path target = copy.resolve(targetFile);

        if (Files.exists(target)) {
            throw new RuntimeException("Target file already exists");
        }
        Files.copy(source,target);
        return targetFile;
    }

    public String moveFile(String roomCode,
                           String sourceFile,
                           String targetFile) throws IOException {

        Path workspace = Paths.get(WORKSPACE, roomCode);

        Path source = workspace.resolve(sourceFile);

        if (!Files.exists(source)) {
            throw new RuntimeException("File not found");
        }

        if (targetFile == null || targetFile.isBlank()) {
            targetFile = generateCopyName(workspace, sourceFile);
        }

        Path target = workspace.resolve(targetFile);

        Files.move(source, target);

        return targetFile;
    }

    /** Permanently removes this room's workspace directory and uploaded-file artifacts. */
    public void deleteRoomFiles(String roomCode, List<fileMetaData> metadata) throws IOException {
        Path workspaceRoot = Paths.get(WORKSPACE).toAbsolutePath().normalize();
        Path roomDirectory = workspaceRoot.resolve(roomCode).normalize();
        if (!roomDirectory.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Invalid room code");
        }
        if (Files.exists(roomDirectory)) {
            try (var paths = Files.walk(roomDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }

        Path uploadsRoot = Paths.get(Final_dir).toAbsolutePath().normalize();
        for (fileMetaData item : metadata) {
            if (item.getStoragePath() != null && !item.getStoragePath().isBlank()) {
                Path stored = Paths.get(item.getStoragePath()).toAbsolutePath().normalize();
                if (stored.startsWith(uploadsRoot)) {
                    Files.deleteIfExists(stored);
                }
            }
            if (item.getFileId() != null && !item.getFileId().isBlank()) {
                Path tempDirectory = Paths.get(Temp_dir).toAbsolutePath().normalize()
                        .resolve(item.getFileId()).normalize();
                Path tempRoot = Paths.get(Temp_dir).toAbsolutePath().normalize();
                if (tempDirectory.startsWith(tempRoot) && Files.exists(tempDirectory)) {
                    try (var paths = Files.walk(tempDirectory)) {
                        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(path);
                        }
                    }
                }
            }
        }
    }

    private String generateCopyName(Path room, String fileName) {

        int dot = fileName.lastIndexOf('.');

        String name;
        String extension;

        if (dot == -1) {
            name = fileName;
            extension = "";
        } else {
            name = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }

        int index = 1;

        while (true) {

            String candidate =
                    name + " (" + index + ")" + extension;

            if (!Files.exists(room.resolve(candidate))) {
                return candidate;
            }

            index++;
        }
    }
}
