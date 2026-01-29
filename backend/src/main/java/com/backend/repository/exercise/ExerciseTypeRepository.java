package com.backend.repository.exercise;

import com.backend.domain.exercise.ExerciseType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExerciseTypeRepository extends JpaRepository<ExerciseType, Long> {
    Optional<ExerciseType> findByName(String name);
}

