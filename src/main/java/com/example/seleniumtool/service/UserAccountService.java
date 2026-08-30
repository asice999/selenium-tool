package com.example.seleniumtool.service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/** 管理唯一的本地管理员账户，并仅持久化 BCrypt 密码摘要。 */
@Service
public class UserAccountService implements UserDetailsService {

    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final Path accountFile;
    private UserAccount account;

    public UserAccountService(
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder,
            @Value("${automation.user-account-file:user-account.json}") String accountFile
    ) {
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.accountFile = Path.of(accountFile).toAbsolutePath().normalize();
    }

    @PostConstruct
    public synchronized void load() {
        if (!Files.exists(accountFile)) {
            return;
        }
        try {
            account = objectMapper.readValue(Files.readString(accountFile), UserAccount.class);
            if (account == null
                    || !StringUtils.hasText(account.username())
                    || !StringUtils.hasText(account.passwordHash())) {
                throw new IllegalStateException("账户文件缺少用户名或密码摘要");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("无法读取用户账户文件: " + accountFile, ex);
        }
    }

    public synchronized boolean hasUser() {
        return account != null;
    }

    public synchronized void register(String username, String password) {
        if (account != null) {
            throw new IllegalStateException("用户已存在，注册入口已关闭");
        }
        String normalizedUsername = username == null ? "" : username.trim();
        if (!normalizedUsername.matches("[A-Za-z0-9_.@-]{3,64}")) {
            throw new IllegalArgumentException("用户名需为 3-64 位字母、数字或 _ . @ -");
        }
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("密码长度需为 8-128 位");
        }
        UserAccount replacement = new UserAccount(
                normalizedUsername,
                passwordEncoder.encode(password)
        );
        try {
            Path parent = accountFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporaryFile = accountFile.resolveSibling(accountFile.getFileName() + ".tmp");
            Files.writeString(temporaryFile, objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(replacement));
            Files.move(temporaryFile, accountFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            account = replacement;
        } catch (Exception ex) {
            throw new IllegalStateException("无法保存用户账户文件: " + accountFile, ex);
        }
    }

    @Override
    public synchronized UserDetails loadUserByUsername(String username) {
        if (account == null || !account.username().equals(username)) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        return User.withUsername(account.username())
                .password(account.passwordHash())
                .roles("ADMIN")
                .build();
    }

    private record UserAccount(String username, String passwordHash) { }
}
