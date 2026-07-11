package com.example.rate_limit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.server.ResponseStatusException;

import com.example.rate_limit.model.Client;
import com.example.rate_limit.service.ClientService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/client")
public class ClientController {

    private final ClientService clientService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Client configureClient(@RequestBody Client client) {
        var response = clientService.createClient(client);
        if (response != null) {
            return response;
        } else {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error Configuring Client " + client.getClientKey() + ", Please try again");
        }
    }

    @PutMapping("/update")
    public Client updateClient(@RequestParam String clientId, @RequestBody Client client) {
        Client existingClient = clientService.getClientByClientKey(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client not found with key: " + clientId));

        if (client.getClientKey() != null && !client.getClientKey().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Client key in request body must match path variable");
        }

        existingClient.setMaxTokens(client.getMaxTokens());
        existingClient.setRefillRate(client.getRefillRate());
        existingClient.setAlgorithm(client.getAlgorithm());

        clientService.updateClient(existingClient);
        return existingClient;
    }

    @DeleteMapping("/delete")
    public String deleteClient(@RequestParam String clientId) {
        clientService.deleteClient(clientId);

        if (clientService.getClientByClientKey(clientId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error Deleting Client " + clientId + ", Please try again");
        } else {
            return "Client Deleted Successfully";
        }
    }

    @GetMapping("/details")
    public Client getClient(@RequestParam String clientId) {
        return clientService.getClientByClientKey(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client not found with key: " + clientId));
    }

}
