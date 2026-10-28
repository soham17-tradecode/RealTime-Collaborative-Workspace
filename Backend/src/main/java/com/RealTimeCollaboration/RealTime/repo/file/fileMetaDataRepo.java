package com.RealTimeCollaboration.RealTime.repo.file;

import com.RealTimeCollaboration.RealTime.model.file.fileMetaData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface fileMetaDataRepo extends JpaRepository<fileMetaData,Long> {

    fileMetaData findByFileId(String fileId);

    List<fileMetaData> findByRoomCode(String roomCode);

    void deleteByRoomCode(String roomCode);
}
