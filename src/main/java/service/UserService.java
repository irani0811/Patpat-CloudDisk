package service;

import model.User;
import repository.UserRepository;
import utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 用户服务类
 */
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    /**
     * 用户注册
     */
    public User registerUser(String username, String password, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("邮箱已存在");
        }
        
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setIsActive(true);
        user.setStorageUsed(0L);
        user.setStorageLimit(1073741824L); // 1GB
        
        return userRepository.save(user);
    }
    
    /**
     * 用户登录
     */
    public String loginUser(String username, String password) {
        Optional<User> userOpt = userRepository.findActiveUserByUsername(username);
        
        if (userOpt.isEmpty()) {
            throw new RuntimeException("用户不存在或已被禁用");
        }
        
        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        
        return jwtUtil.generateToken(username, user.getId());
    }
    
    /**
     * 根据用户名获取用户
     */
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    /**
     * 根据ID获取用户
     */
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }
    
    /**
     * 验证JWT token
     */
    public boolean validateToken(String token, String username) {
        return jwtUtil.validateToken(token, username);
    }
    
    /**
     * 从token获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        return jwtUtil.getUserIdFromToken(token);
    }
    
    /**
     * 更新用户存储使用量
     */
    public void updateStorageUsage(Long userId, Long additionalBytes) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setStorageUsed(user.getStorageUsed() + additionalBytes);
            userRepository.save(user);
        }
    }
    
    /**
     * 检查用户存储空间是否足够
     */
    public boolean hasEnoughStorage(Long userId, Long requiredBytes) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }
        
        User user = userOpt.get();
        return (user.getStorageUsed() + requiredBytes) <= user.getStorageLimit();
    }
    
    /**
     * 更新用户信息
     */
    public User updateUser(Long userId, String email) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }
        
        User user = userOpt.get();
        user.setEmail(email);
        user.setUpdatedAt(LocalDateTime.now());
        
        return userRepository.save(user);
    }
    
    /**
     * 禁用用户
     */
    public void deactivateUser(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setIsActive(false);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }
}

