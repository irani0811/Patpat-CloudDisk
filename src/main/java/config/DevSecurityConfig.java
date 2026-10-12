package config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 开发环境安全配置 - 允许API访问但保持基本安全
 */
// @Configuration
// @EnableWebSecurity
public class DevSecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
            .passwordEncoder(passwordEncoder())
            .withUser("admin")
            .password("admin")
            .roles("USER", "ADMIN");
    }
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()  // 禁用CSRF（开发环境）
            .cors().and()      // 启用CORS
            .authorizeRequests()
                .antMatchers("/api/**").permitAll()      // 允许所有API访问
                .antMatchers("/static/**").permitAll()    // 允许静态资源
                .antMatchers("/").permitAll()             // 允许主页
                .antMatchers("/h2-console/**").authenticated() // H2控制台需要认证
                .anyRequest().permitAll()                 // 允许其他请求
            .and()
            .headers().frameOptions().disable()          // 允许H2控制台
            .and()
            .httpBasic()  // 为H2控制台启用HTTP Basic认证
            .and()
            .formLogin().disable()  // 禁用表单登录
            .logout().disable();  // 禁用登出
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance(); // 使用明文密码编码器（开发环境）
    }
}
