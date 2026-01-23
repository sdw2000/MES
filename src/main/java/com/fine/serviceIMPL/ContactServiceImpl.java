package com.fine.serviceIMPL;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.ContactMapper;
import com.fine.modle.Contact;
import com.fine.service.ContactService;

@Service
public class ContactServiceImpl extends ServiceImpl<ContactMapper, Contact> implements ContactService {
}
