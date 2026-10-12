package com.clouddisk;

import cs.CSSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.CommandLineRunner;

/**
 * 云盘应用主类
 * 集成BS和CS架构
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.clouddisk", "controller", "service", "model", "repository", "utils", "config", "cs"})
@EnableJpaRepositories(basePackages = {"repository"})
@EntityScan(basePackages = {"model"})
public class CloudDiskApplication implements CommandLineRunner {
    
    @Autowired
    private CSSocketServer csSocketServer;
    
    public static void main(String[] args) {
        SpringApplication.run(CloudDiskApplication.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        // 启动CS Socket服务器
        System.out.println("=== 启动EasyCloudDisk云盘系统 ===");
        System.out.println("BS架构: http://localhost:8080");
        System.out.println("CS架构: Socket服务器端口 8889");
        
        // 启动CS服务器
        csSocketServer.start();
        
        System.out.println("=== 系统启动完成 ===");
        System.out.println("BS Web界面: http://localhost:8080");
        System.out.println("CS客户端连接: localhost:8889");
        System.out.println("CS管理接口: http://localhost:8080/api/cs/status");
    }
}

