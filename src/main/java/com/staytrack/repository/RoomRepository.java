package com.staytrack.repository;

import com.staytrack.domain.Room;
import com.staytrack.domain.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByBuildingAndRoomNumber(String building, String roomNumber);

    List<Room> findAllByOrderByBuildingAscRoomNumberAsc();

    List<Room> findByStatus(RoomStatus status);

    long countByStatus(RoomStatus status);
}
