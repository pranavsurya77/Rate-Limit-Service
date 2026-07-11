package com.example.rate_limit.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.rate_limit.model.Client;

@Repository
public interface ClientRepository extends CrudRepository<Client, String> {
    Optional<Client> findByClientKey(String clientKey);
}
