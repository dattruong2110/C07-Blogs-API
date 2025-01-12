package com.codegym.c07blog.repository;

import com.codegym.c07blog.entity.Fact.FactUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IFactUserRepository extends JpaRepository<FactUser, UUID> {
}
