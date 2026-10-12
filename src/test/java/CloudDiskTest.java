package com.clouddisk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 云盘系统测试类
 */
@SpringBootTest
@ActiveProfiles("test")
public class CloudDiskTest {
    
    @Test
    public void contextLoads() {
        // 测试Spring上下文是否能正常加载
    }
    
    @Test
    public void testApplicationStartup() {
        // 测试应用程序启动
        System.out.println("云盘系统测试启动成功");
    }
}

