package com.codegym.c07blog.service.impl;

import com.codegym.c07blog.dto.BlogDTO;
import com.codegym.c07blog.dto.FactDTO;
import com.codegym.c07blog.dto.UserDTO;
import com.codegym.c07blog.entity.Blog.Blog;
import com.codegym.c07blog.entity.Fact.Fact;
import com.codegym.c07blog.entity.authentication.Role;
import com.codegym.c07blog.entity.authentication.User;
import com.codegym.c07blog.entity.authentication.UserRole;
import com.codegym.c07blog.jwt.JsonWebTokenProvider;
import com.codegym.c07blog.payload.request.LoginRequest;
import com.codegym.c07blog.payload.request.RegisterRequest;
import com.codegym.c07blog.payload.request.UserRequest;
import com.codegym.c07blog.payload.response.LoginResponse;
import com.codegym.c07blog.payload.response.ResponsePayload;
import com.codegym.c07blog.payload.response.UserResponse;
import com.codegym.c07blog.repository.IBlogRepository;
import com.codegym.c07blog.repository.IFactRepository;
import com.codegym.c07blog.repository.IRoleRepository;
import com.codegym.c07blog.repository.IUserRepository;
import com.codegym.c07blog.repository.IUserRoleRepository;
import com.codegym.c07blog.service.IUserService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class UserService implements IUserService {
    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final IBlogRepository blogRepository;
    private final IFactRepository factRepository;
    private final IUserRoleRepository userRoleRepository;
    private final RoleService roleService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JsonWebTokenProvider jsonWebTokenProvider;

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Override
    @Transactional
    public ResponsePayload delete(UUID id){
        userRepository.deleteUserById(id);
        return ResponsePayload.builder()
                .message("Delete success")
                .data(null)
                .status(HttpStatus.OK)
                .build();
    }

    @Override
    public ResponsePayload update(UserRequest userRequest) {
        User user = userRepository.findUserById(userRequest.getId());
        if (user == null) {
            return ResponsePayload.builder()
                    .message("User not found")
                    .data(null)
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }

        if (userRepository.existsByUsername(userRequest.getUsername())) {
            return ResponsePayload.builder()
                    .message("Username already exists")
                    .data(null)
                    .status(HttpStatus.BAD_REQUEST)
                    .build();
        }

        user.setUsername(userRequest.getUsername());
        user.setEmail(userRequest.getEmail());
        user.setFullName(userRequest.getFullName());
        user.setAvatar(userRequest.getAvatar());
        userRepository.save(user);

        return ResponsePayload.builder()
                .message("Update success")
                .data(user)
                .status(HttpStatus.OK)
                .build();
    }

    @Override
    public ResponsePayload login(LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = jsonWebTokenProvider.generateToken(authentication.getName());
            User user = userRepository.findByUsername(loginRequest.getUsername());

            List<String> roles = user.getUserRole().stream()
                    .map(userRole -> userRole.getRole().getName())
                    .collect(Collectors.toList());

            LoginResponse tokenResponse =  LoginResponse.builder()
                    .id((user.getId()))
                    .fullName(user.getFullName())
                    .username(user.getUsername())
                    .avatar(user.getAvatar())
                    .roles(roles)
                    .token(token)
                    .build();
            return ResponsePayload
                    .builder()
                    .message("Login success")
                    .data(tokenResponse)
                    .status(HttpStatus.OK)
                    .build();
        } catch (Exception e) {
            return ResponsePayload
                    .builder()
                    .message("Login failed")
                    .data(e.getMessage())
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }
    }

    @Override
    public ResponsePayload register(RegisterRequest registerRequest) {
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        String password = passwordEncoder.encode(registerRequest.getPassword());
        user.setPassword(password);
        user.setEmail(registerRequest.getEmail());
        user.setFullName(registerRequest.getFullName());
        user.setAvatar(registerRequest.getAvatar());
        user.setIsDeleted(false);
        userRepository.save(user);
        logger.info("User saved with ID: {}", user.getId());

        Role userRole = roleRepository.findByName("ROLE_USER");
        if (userRole != null) {
            logger.info("User role found: {}", userRole.getName());
            UserRole userRoleMapping = new UserRole();
            userRoleMapping.setUser(user);
            userRoleMapping.setRole(userRole);
            userRoleRepository.save(userRoleMapping);
            logger.info("UserRole saved with user ID: {} and role ID: {}", user.getId(), userRole.getId());
        } else {
            logger.error("USER role not found");
            return ResponsePayload.builder()
                    .message("Register failed: USER role not found")
                    .data(null)
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }

        return ResponsePayload.builder()
                .message("Register success")
                .data(null)
                .status(HttpStatus.OK)
                .build();
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public UserResponse createAdminAccount(UserRequest userRequest, UUID superAdminId) throws Exception {
        if (!roleService.isUserSuperAdmin(superAdminId)) {
            throw new Exception("Only SUPER_ADMIN can create an ADMIN account.");
        }

        Role roleAdmin = roleRepository.findByName("ROLE_ADMIN");
        Role roleUser = roleRepository.findByName("ROLE_USER");

        if (roleAdmin == null) {
            throw new Exception("Admin role not found.");
        } else if (roleUser == null) {
            throw new Exception("User role not found.");
        }

        User user = new User();
        user.setUsername(userRequest.getUsername());
        user.setPassword(passwordEncoder.encode(userRequest.getPassword()));
        user.setEmail(userRequest.getEmail());
        user.setFullName(userRequest.getFullName());
        user.setAvatar(userRequest.getAvatar());
        userRepository.save(user);

        UserRole adminUserRole = new UserRole();
        adminUserRole.setUser(user);
        adminUserRole.setRole(roleAdmin);
        userRoleRepository.save(adminUserRole);

        UserRole userUserRole = new UserRole();
        userUserRole.setUser(user);
        userUserRole.setRole(roleUser);
        userRoleRepository.save(userUserRole);

        user.setUserRole(Set.of(adminUserRole, userUserRole));
        userRepository.save(user);

        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setUsername(user.getUsername());
        userResponse.setEmail(user.getEmail());
        userResponse.setFullName(user.getFullName());
        userResponse.setAvatar(user.getAvatar());
        userResponse.setRoles(user.getUserRole().stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(Collectors.toSet()));

        return userResponse;
    }

    @Override
    public UserDTO getBlogByUserID(UUID id) {
        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            List<Blog> blogs = blogRepository.findAllByUserId(user.getId());

            Set<BlogDTO> blogDTOs = blogs.stream()
                    .map(blog -> new BlogDTO(
                            blog.getId(),
                            blog.getTitle(),
                            blog.getContent(),
                            blog.getPicture(),
                            blog.getCategory().getName().toString(),
                            null
                    )).collect(Collectors.toSet());

            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setUsername(user.getUsername());
            userDTO.setEmail(user.getEmail());
            userDTO.setFullName(user.getFullName());
            userDTO.setAvatar(user.getAvatar());
            userDTO.setIsDeleted(user.getIsDeleted());

            if (!blogDTOs.isEmpty()) {
                userDTO.setBlogs(blogDTOs);
            }

            return userDTO;
        } else {
            System.out.println("User not found");
        }
        return null;
    }

    @Override
    public UserDTO getFactByUserID(UUID id) {
        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            List<Fact> facts = factRepository.findAllByUserId(user.getId());

            Set<FactDTO> factDTOS = facts.stream()
                    .map(fact -> new FactDTO(
                            fact.getId(),
                            fact.getPicture(),
                            fact.getContent(),
                            fact.getLikes(),
                            fact.getComment(),
                            null
                    )).collect(Collectors.toSet());

            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setUsername(user.getUsername());
            userDTO.setEmail(user.getEmail());
            userDTO.setFullName(user.getFullName());
            userDTO.setAvatar(user.getAvatar());
            userDTO.setIsDeleted(user.getIsDeleted());

            if (!factDTOS.isEmpty()) {
                userDTO.setFacts(factDTOS);
            }

            return userDTO;
        } else {
            System.out.println("User not found");
        }
        return null;
    }
}
