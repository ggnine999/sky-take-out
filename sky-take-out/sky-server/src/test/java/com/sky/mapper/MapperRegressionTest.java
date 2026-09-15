package com.sky.mapper;

import com.sky.context.BaseContext;
import com.sky.dto.DishPageQueryDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.service.OrderService;
import com.sky.service.DeliveryRangeService;
import com.sky.service.impl.OrderServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MapperRegressionTest {
    JdbcTemplate jdbc;
    SqlSessionTemplate session;
    DataSourceTransactionManager transactions;

    @BeforeEach void setup() throws Exception {
        JdbcDataSource data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(data); transactions = new DataSourceTransactionManager(data);
        jdbc.execute("create table shopping_cart(id bigint auto_increment primary key,name varchar(100),image varchar(100),user_id bigint,"
                + "dish_id bigint,setmeal_id bigint,dish_flavor varchar(100),number int default 1,amount decimal(10,2),create_time timestamp)");
        jdbc.execute("create table address_book(id bigint primary key,user_id bigint,consignee varchar(50),phone varchar(50),sex varchar(5),"
                + "province_code varchar(20),province_name varchar(50),city_code varchar(20),city_name varchar(50),district_code varchar(20),"
                + "district_name varchar(50),detail varchar(100),label varchar(10),is_default int)");
        jdbc.execute("create table category(id bigint primary key,name varchar(50))");
        jdbc.execute("create table dish(id bigint primary key,name varchar(50),category_id bigint,price decimal(10,2),status int,create_time timestamp,update_time timestamp)");
        jdbc.execute("create table orders(id bigint auto_increment primary key,number varchar(50),status int,user_id bigint,address_book_id bigint,"
                + "order_time timestamp,checkout_time timestamp,pay_method int,pay_status int,amount decimal(10,2),remark varchar(100),"
                + "phone varchar(50),address varchar(200),consignee varchar(50),estimated_delivery_time timestamp,delivery_status int,pack_amount int,"
                + "tableware_number int,tableware_status int,cancel_reason varchar(100),rejection_reason varchar(100),cancel_time timestamp,delivery_time timestamp)");
        Configuration configuration = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), data));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeAliasRegistry().registerAliases("com.sky.entity");
        for (String name : new String[]{"ShoppingCartMapper", "AddressBookMapper", "DishMapper", "OrderMapper"}) {
            String resource = "mapper/" + name + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
    }

    @AfterEach void close() { BaseContext.removeCurrentId(); jdbc.execute("shutdown"); }

    @Test void cartQuantityPersistsWithoutChangingUnitPrice() {
        ShoppingCartMapper mapper = session.getMapper(ShoppingCartMapper.class);
        ShoppingCart cart = ShoppingCart.builder().name("菜品").userId(1L).dishId(2L).number(3).amount(new BigDecimal("12.50")).build();
        mapper.insert(cart); cart.setNumber(5); mapper.updateNumberById(cart);
        ShoppingCart saved = mapper.list(ShoppingCart.builder().userId(1L).build()).get(0);
        assertEquals(5, saved.getNumber()); assertEquals(new BigDecimal("12.50"), saved.getAmount());
        cart.setNumber(4); mapper.insertBatch(Collections.singletonList(cart));
        assertEquals(9, jdbc.queryForObject("select sum(number) from shopping_cart", Integer.class));
    }

    @Test void sqlEnforcesAddressOwnerForUpdateAndDelete() {
        jdbc.update("insert into address_book(id,user_id,detail) values(1,7,'original')");
        AddressBookMapper mapper = session.getMapper(AddressBookMapper.class);
        mapper.update(AddressBook.builder().id(1L).userId(8L).detail("tampered").build());
        mapper.deleteById(1L,8L);
        assertEquals("original", mapper.getById(1L).getDetail());
    }

    @Test void categoryFilterUsesActualColumn() {
        jdbc.update("insert into category values(1,'类别')");
        jdbc.update("insert into dish(id,name,category_id,status) values(2,'菜品',1,1)");
        DishPageQueryDTO query = new DishPageQueryDTO(); query.setCategoryId(1);
        assertEquals(1, session.getMapper(DishMapper.class).pageQuery(query).size());
        query.setCategoryId(2);
        assertTrue(session.getMapper(DishMapper.class).pageQuery(query).isEmpty());
    }

    @Test void timeoutTaskCannotCancelAlreadyPaidOrder() {
        jdbc.update("insert into orders(number,status,pay_status,order_time) values('paid',2,1,?)", LocalDateTime.now().minusHours(1));
        jdbc.update("insert into orders(number,status,pay_status,order_time) values('unpaid',1,0,?)", LocalDateTime.now().minusHours(1));
        assertEquals(1, session.getMapper(OrderMapper.class).cancelExpired(LocalDateTime.now().minusMinutes(15),LocalDateTime.now()));
        assertEquals(2, jdbc.queryForObject("select status from orders where number='paid'", Integer.class));
    }

    @Test void failedDetailsInsertRollsBackOrderAndPreservesCart() {
        BaseContext.setCurrentId(7L);
        jdbc.update("insert into address_book(id,user_id,city_name,district_name,detail) values(1,7,'城市','区域','地址')");
        jdbc.update("insert into dish(id,name,price,status) values(2,'菜品',20,1)");
        jdbc.update("insert into shopping_cart(user_id,dish_id,number,amount) values(7,2,2,20)");
        OrderServiceImpl target = new OrderServiceImpl();
        ReflectionTestUtils.setField(target,"orderMapper",session.getMapper(OrderMapper.class));
        ReflectionTestUtils.setField(target,"shoppingCartMapper",session.getMapper(ShoppingCartMapper.class));
        ReflectionTestUtils.setField(target,"addressBookMapper",session.getMapper(AddressBookMapper.class));
        ReflectionTestUtils.setField(target,"dishMapper",session.getMapper(DishMapper.class));
        ReflectionTestUtils.setField(target,"deliveryRangeService",mock(DeliveryRangeService.class));
        ReflectionTestUtils.setField(target,"packagingFeePerItem",1);
        ReflectionTestUtils.setField(target,"deliveryFee",new BigDecimal("6"));
        OrderDetailMapper details = mock(OrderDetailMapper.class);
        doThrow(new IllegalStateException("simulated detail failure")).when(details).insertBatch(any());
        ReflectionTestUtils.setField(target,"orderDetailMapper",details);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        OrderService service = (OrderService) proxy.getProxy();
        OrdersSubmitDTO request = new OrdersSubmitDTO(); request.setAddressBookId(1L);
        assertThrows(IllegalStateException.class, () -> service.submitOrder(request));
        verify(details).insertBatch(any());
        assertEquals(0, jdbc.queryForObject("select count(*) from orders", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from shopping_cart", Integer.class));
    }
}
