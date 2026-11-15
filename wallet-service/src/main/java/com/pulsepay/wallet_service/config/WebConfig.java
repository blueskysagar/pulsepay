package com.pulsepay.wallet_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration //contains bean definitions and this class contains the configuration
// that spring should apply to the applications.


//WebMvcConfigurer: It is telling spring I want to customize some parts of your MVC web configuration

public class WebConfig implements WebMvcConfigurer {
    /*
     * Customize how Spring converts HTTP pagination parameters
     * into Pageable objects used by our controllers.
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers){
        PageableHandlerMethodArgumentResolver pageableResolver = new PageableHandlerMethodArgumentResolver();
        /*
         * Prevent clients from requesting an unreasonably large
         * number of records in a single API request.
         */
        pageableResolver.setMaxPageSize(100);
        /*
         * Register our customized Pageable resolver with Spring MVC.
         */
        resolvers.add(pageableResolver);
    }
}
