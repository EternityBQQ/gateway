package com.itcast.gateway.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.itcast.gateway.entertities.FilterEntity;
import com.itcast.gateway.entertities.PredicateEntity;
import com.itcast.gateway.entertities.RouteEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 *监听事件推送
 */
@Component
public class DynamicRoutingConfig implements ApplicationEventPublisherAware {
    public static final Logger LOG = LoggerFactory.getLogger(DynamicRoutingConfig.class);

    private ApplicationEventPublisher applicationEventPublisher;

    public static final String DATA_ID = "refresh-dev.json";
    public static final String GROUP = "DEFAULT_GROUP";

    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter;

    @Bean
    public void refreshRouting() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "139.9.205.89:8848");
        properties.setProperty(PropertyKeyConst.NAMESPACE, "9e4b3abd-964c-4ef6-8960-df78b6ab9a58");
        ConfigService configService = NacosFactory.createConfigService(properties);
        configService.addListener(DATA_ID, GROUP, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            /**
             * 在这儿判断路由是否发生变化
             * @param configInfo 配置详情
             */
            @Override
            public void receiveConfigInfo(String configInfo) {
                LOG.info(configInfo);
                Boolean refreshGatewayRoute = JSONObject.parseObject(configInfo).getBoolean("refreshGatewayRoute");
                if (refreshGatewayRoute) {
                    List<RouteEntity> routeList = JSON.parseArray(JSONObject.parseObject(configInfo).getString("routeList")).toJavaList(RouteEntity.class);
                    for (RouteEntity route : routeList) {
                        update(assembleRouteDefinition(route));
                    }
                }
            }
        });
    }

    /**
     * 组装路由定义
     * @param route 路由对象
     * @return 路由定义
     */
    private RouteDefinition assembleRouteDefinition(RouteEntity route) {
        RouteDefinition definition = new RouteDefinition();
        // ID
        definition.setId(route.getId());

        // Predicates
        List<PredicateDefinition> pdList = new ArrayList<>();
        for (PredicateEntity predicateEntity: route.getPredicates()) {
            PredicateDefinition predicateDefinition = new PredicateDefinition();
            predicateDefinition.setName(predicateEntity.getName());
            predicateDefinition.setArgs(predicateEntity.getArgs());
            pdList.add(predicateDefinition);
        }

        definition.setPredicates(pdList);

        // filters
        List<FilterDefinition> fdList = new ArrayList<>();
        for (FilterEntity filterEntity: route.getFilters()) {
            FilterDefinition filterDefinition = new FilterDefinition();
            filterDefinition.setName(filterEntity.getName());
            filterDefinition.setArgs(filterEntity.getArgs());
            fdList.add(filterDefinition);
        }
        definition.setFilters(fdList);

        // URL
        URI uri = UriComponentsBuilder.fromUriString(route.getUri()).build().toUri();
        definition.setUri(uri);

        return definition;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 路由更新
     * @param routeDefinition 路由详情
     */
    public void update(RouteDefinition routeDefinition) {
        try {
            // 更新路由
            this.routeDefinitionWriter.delete(Mono.just(routeDefinition.getId()));
            LOG.info("路由移除成功");
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }

        try {
            this.routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe();
            this.applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
            LOG.info("路由保存成功");
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
