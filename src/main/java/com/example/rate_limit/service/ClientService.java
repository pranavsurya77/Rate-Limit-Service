package com.example.rate_limit.service;

import java.util.Optional;
import com.example.rate_limit.model.Client;

public interface ClientService {

    Optional<Client> getClientByClientKey(String clientKey);

    Client createClient(Client client);

    void updateClient(Client client);

    void deleteClient(String clientKey);
}