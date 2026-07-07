package com.be_paas.modules.project.repository;

import com.be_paas.modules.project.entity.EnvironmentVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentVariableRepository extends JpaRepository<EnvironmentVariable, Integer> {
    List<EnvironmentVariable> findByProjectId(Integer projectId);
    void deleteByProjectId(Integer projectId);

    Optional<EnvironmentVariable> findByIdAndProjectId(Integer id, Integer projectId);
}
