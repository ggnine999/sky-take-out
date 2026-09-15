package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.exception.AddressBookBusinessException;
import com.sky.mapper.AddressBookMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressBookServiceImplTest {
    @Mock AddressBookMapper mapper;
    @InjectMocks AddressBookServiceImpl service;
    @BeforeEach void setup() { BaseContext.setCurrentId(1L); }
    @AfterEach void clear() { BaseContext.removeCurrentId(); }

    @Test void foreignAddressCannotBeReadChangedDeletedOrMadeDefault() {
        when(mapper.getById(8L)).thenReturn(AddressBook.builder().id(8L).userId(2L).build());
        AddressBook request = AddressBook.builder().id(8L).userId(1L).build();
        assertThrows(AddressBookBusinessException.class, () -> service.getById(8L));
        assertThrows(AddressBookBusinessException.class, () -> service.update(request));
        assertThrows(AddressBookBusinessException.class, () -> service.deleteById(8L));
        assertThrows(AddressBookBusinessException.class, () -> service.setDefault(request));
        verify(mapper, never()).update(any());
        verify(mapper, never()).deleteById(any(), any());
        verify(mapper, never()).updateIsDefaultByUserId(any());
    }

    @Test void updatesAlwaysUseAuthenticatedOwner() {
        when(mapper.getById(8L)).thenReturn(AddressBook.builder().id(8L).userId(1L).build());
        AddressBook request = AddressBook.builder().id(8L).userId(2L).isDefault(1).build();
        service.update(request);
        assertEquals(1L, request.getUserId());
        assertNull(request.getIsDefault());
        verify(mapper).update(request);
    }
}
