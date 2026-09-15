package com.sky.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.exception.OrderBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.time.Duration;

@Service
public class DeliveryRangeService {
    private final RestTemplate client;

    @Value("${sky.shop.address}")
    private String shopAddress;
    @Value("${sky.baidu.ak}")
    private String ak;

    public DeliveryRangeService(RestTemplateBuilder builder) {
        client = builder.setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5)).build();
    }

    public void check(String address) {
        if (ak == null || ak.trim().isEmpty()) {
            throw new OrderBusinessException("请先配置百度地图密钥 SKY_BAIDU_AK");
        }
        try {
            String origin = coordinate(shopAddress);
            String destination = coordinate(address);
            URI uri = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/directionlite/v1/driving")
                    .queryParam("origin", origin).queryParam("destination", destination)
                    .queryParam("ak", ak).queryParam("steps_info", "0").build().encode().toUri();
            JSONObject response = response(uri);
            JSONArray routes = response.getJSONObject("result").getJSONArray("routes");
            if (routes == null || routes.isEmpty()) {
                throw new OrderBusinessException("配送线路规划失败");
            }
            Integer distance = routes.getJSONObject(0).getInteger("distance");
            if (distance == null || distance < 0) {
                throw new OrderBusinessException("配送距离无效");
            }
            if (distance > 5000) {
                throw new OrderBusinessException("超出配送范围");
            }
        } catch (OrderBusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OrderBusinessException("配送地址校验暂不可用，请稍后重试");
        }
    }

    private String coordinate(String address) {
        URI uri = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/geocoding/v3/")
                .queryParam("address", address).queryParam("output", "json")
                .queryParam("ak", ak).build().encode().toUri();
        JSONObject location = response(uri).getJSONObject("result").getJSONObject("location");
        if (location.getBigDecimal("lat") == null || location.getBigDecimal("lng") == null) {
            throw new OrderBusinessException("地址坐标无效");
        }
        // Baidu route endpoints expect latitude,longitude.
        return location.getString("lat") + "," + location.getString("lng");
    }

    private JSONObject response(URI uri) {
        JSONObject json = JSON.parseObject(client.getForObject(uri, String.class));
        if (json == null || !Integer.valueOf(0).equals(json.getInteger("status"))) {
            throw new OrderBusinessException("地址解析或线路规划失败");
        }
        return json;
    }
}
