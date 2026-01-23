package com.fine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import com.fine.modle.Contact;
import com.fine.service.ContactService;

import java.util.List;
@PreAuthorize("hasAuthority('admin')")
@RestController
@RequestMapping("/contacts")
public class ContactController {
    @Autowired
    private ContactService contactService;

    @PostMapping("/")
    public boolean addContact(@RequestBody List<Contact> contacts) {
        return contactService.saveBatch(contacts);
    }

    @DeleteMapping("/{id}")
    public boolean deleteContact(@PathVariable Integer id) {
        return contactService.removeById(id);
    }

    @PutMapping("/")
    public boolean updateContact(@RequestBody Contact contact) {
        return contactService.updateById(contact);
    }

    @GetMapping("/{id}")
    public Contact getContact(@PathVariable Integer id) {
        return contactService.getById(id);
    }

    @GetMapping("/")
    public List<Contact> getAllContacts() {
        return contactService.list();
    }
}
