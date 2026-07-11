package com.example.rate_limit.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.rate_limit.model.Client;

@Repository
public interface ClientRepository extends CrudRepository<Client, String> {

}
