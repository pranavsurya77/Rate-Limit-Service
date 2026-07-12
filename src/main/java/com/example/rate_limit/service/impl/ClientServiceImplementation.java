package com.example.rate_limit.service.impl;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.rate_limit.model.Client;
import com.example.rate_limit.repository.ClientRepository;
import com.example.rate_limit.service.ClientService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientServiceImplementation implements ClientService {

    private final ClientRepository clientRepository;

    @Override
    public Optional<Client> getClientByClientKey(String clientKey) {
        return clientRepository.findByClientKey(clientKey);
    }

    @Override
    public Client createClient(Client client) {
        Instant now = Instant.now();
        client.setCreatedAt(now);
        client.setUpdatedAt(now);

        if (client.getId() == null || client.getId().isBlank()) {
            client.setId(client.getClientKey());
        }

        return clientRepository.save(client);
    }

    @Override
    public void updateClient(Client client) {
        client.setUpdatedAt(Instant.now());
        clientRepository.save(client);
    }

    @Override
    public void deleteClient(String clientKey) {
        clientRepository.findByClientKey(clientKey)
                .ifPresent(clientRepository::delete);
    }
}


