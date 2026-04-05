package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.ContentUpdate;
import com.cyberlearnix.shared.repository.ContentUpdateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/updates")
public class UpdateController {

    @Autowired
    private ContentUpdateRepository updateRepository;

    @GetMapping
    public ResponseEntity<List<ContentUpdate>> getAllUpdates() {
        return ResponseEntity.ok(updateRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ContentUpdate> createUpdate(@RequestBody ContentUpdate update) {
        return ResponseEntity.ok(updateRepository.save(update));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContentUpdate> updateUpdate(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return updateRepository.findById(id).map(update -> {
            if (updates.containsKey("title"))
                update.setTitle((String) updates.get("title"));
            if (updates.containsKey("description"))
                update.setDescription((String) updates.get("description"));
            if (updates.containsKey("icon"))
                update.setIcon((String) updates.get("icon"));
            if (updates.containsKey("link"))
                update.setLink((String) updates.get("link"));
            if (updates.containsKey("btnText"))
                update.setBtnText((String) updates.get("btnText"));
            if (updates.containsKey("imgUrl"))
                update.setImgUrl((String) updates.get("imgUrl"));
            if (updates.containsKey("details"))
                update.setDetails((String) updates.get("details"));
            return ResponseEntity.ok(updateRepository.save(update));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUpdate(@PathVariable Long id) {
        if (updateRepository.existsById(id)) {
            updateRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
