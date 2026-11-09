package com.peergeek.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
	List<CategoryEntity> findAllByActiveTrueOrderBySortOrderAscNameAsc();
	java.util.Optional<CategoryEntity> findByIdAndActiveTrue(Long id);
}
