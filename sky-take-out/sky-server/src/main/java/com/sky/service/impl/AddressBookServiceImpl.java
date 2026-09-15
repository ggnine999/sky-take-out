package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import com.sky.exception.AddressBookBusinessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AddressBookServiceImpl implements AddressBookService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询
     * @param addressBook
     * @return
     */
    @Override
    public List<AddressBook> list(AddressBook addressBook) {
        addressBook.setUserId(BaseContext.getCurrentId());
        return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     * @param addressBook
     */
    @Override
    public void save(AddressBook addressBook) {
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询
     * @param id
     * @return
     */
    @Override
    public AddressBook getById(Long id) {
        AddressBook address = addressBookMapper.getById(id);
        if (address == null || BaseContext.getCurrentId() == null
                || !BaseContext.getCurrentId().equals(address.getUserId())) {
            throw new AddressBookBusinessException("地址不存在");
        }
        return address;
    }

    /**
     * 根据id修改地址
     * @param addressBook
     */
    @Override
    public void update(AddressBook addressBook) {
        getById(addressBook.getId());
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(null);
        addressBookMapper.update(addressBook);
    }

    /**
     * 设置默认地址
     * @param addressBook
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(AddressBook addressBook) {
        getById(addressBook.getId());
        // Only the ID is accepted here; do not overwrite address fields supplied by a client.
        addressBook = AddressBook.builder().id(addressBook.getId()).build();
        //1、将当前用户的所有地址修改为非默认地址 update address_book set is_default = ? where user_id = ?
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        //2、将当前地址改为默认地址 update address_book set is_default = ? where id = ?
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }

    /**
     * 根据id删除地址
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        getById(id);
        addressBookMapper.deleteById(id, BaseContext.getCurrentId());
    }
}
