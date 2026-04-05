package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.user.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByLocationOrderByDisplayOrderAsc(String location);
    List<MenuItem> findByIsActiveTrueOrderByDisplayOrderAsc();
    List<MenuItem> findByLocationAndIsActiveTrueOrderByDisplayOrderAsc(String location);
    List<MenuItem> findByParentIdOrderByDisplayOrderAsc(Long parentId);
}
