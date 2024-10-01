package com.mmc.bookduck.domain.folder.repository;

import com.mmc.bookduck.domain.folder.entity.FolderBook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolderBookRepository extends JpaRepository<FolderBook, Long> {
}
