package config;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import javax.servlet.MultipartConfigElement;

/**
 * 文件上传配置类
 */
@Configuration
public class FileUploadConfig {
    
    /**
     * 配置MultipartConfigElement
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        
        // 设置单个文件最大大小
        factory.setMaxFileSize(DataSize.ofGigabytes(1));
        
        // 设置总请求最大大小
        factory.setMaxRequestSize(DataSize.ofGigabytes(1));
        
        // 设置文件写入磁盘的阈值
        factory.setFileSizeThreshold(DataSize.ofMegabytes(10));
        
        return factory.createMultipartConfig();
    }
    
    /**
     * 配置MultipartResolver
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}
