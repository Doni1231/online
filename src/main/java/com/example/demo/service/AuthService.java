package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.entity.enums.RoleName;
import com.example.demo.payload.ApiResponse;
import com.example.demo.payload.UserDto;
import com.example.demo.repository.AttachmentRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


import java.util.Optional;
import java.util.UUID;


@Service
public class AuthService implements UserDetailsService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MailSenderService mailSender;

    @Autowired
    private AttachmentRepository attachmentRepository;


    public ApiResponse registerUser(UserDto userDto) {
        try {
            ApiResponse checkPassword = checkPassword(userDto);
            if (!checkPassword.isSuccess())
                return checkPassword;
            ApiResponse checkEmailAndPhoneNumber = checkEmailAndPhoneNumber(userDto, userDto.getId());
            if (!checkEmailAndPhoneNumber.isSuccess())
                return checkEmailAndPhoneNumber;
            UUID emailCode = UUID.randomUUID();
            userRepository.save(makeUser(userDto, false, emailCode));
            mailSender.sendEmailForVerification(emailCode, userDto, false);
            return new ApiResponse("Congratulation you are successfully registered. please check your email address.", true);
        } catch (Exception e) {
            return new ApiResponse("Failed to save user", false);
        }

    }


    public User makeUser(UserDto userDto, Boolean admin, UUID emailCode) {
        return new User(
                userDto.getFirstName(),
                userDto.getLastName(),
                userDto.getPhoneNumber(),
                userDto.getEmail(),
                passwordEncoder.encode(userDto.getPassword()),
                String.valueOf(emailCode),
                roleRepository.findAllByRoleName(admin == null ? RoleName.ROLE_MANAGER :
                        admin ? RoleName.ROLE_ADMIN
                                : RoleName.ROLE_LOADER
                ),
                admin != null && admin,
//                userDto.getPhotoId() == null ? null :attachmentRepository.findById(userDto.getPhotoId())
                null
        );
    }


    public ApiResponse verifyEmail(Boolean check, String emailCode, String email, boolean changing) {
        try {
            Optional<User> optionalUser = changing ? userRepository.findByEmailCodeAndChangingEmail(emailCode, email) :
                    userRepository.findByEmailCodeAndEmail(emailCode, email);
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                if (check) {
                    user.setEnabled(true);
                    user.setEmailCode(null);
                    if (changing) {
                        user.setEmail(user.getChangingEmail());
                        user.setChangingEmail(null);
                    }
                    userRepository.save(user);
                    return new ApiResponse("User enabled!", true);
                } else {
                    if (!changing)
                        userRepository.delete(user);
                    else {
                        user.setChangingEmail(null);
                        user.setEmailCode(null);
                    }
                }
                return new ApiResponse("User deleted!", true);
            }
            return new ApiResponse("User not found!", false);
        } catch (Exception i) {
            return new ApiResponse("Email code exception", false);
        }
    }


    private ApiResponse checkEmailAndPhoneNumber(UserDto userDto, UUID userId) {
        if (userRepository.existsByEmailAndIdNot(userDto.getEmail(), userId))
            return new ApiResponse("Email is already exist", false);
        if (userRepository.existsByPhoneNumberAndIdNot(userDto.getPhoneNumber(), userId))
            return new ApiResponse("This phone number is already exist", false);
        return new ApiResponse("", true);
    }

    public ApiResponse checkPassword(UserDto userDto) {
        if (userDto.getPassword().length() < 6 || userDto.getPassword().length() > 16)
            return new ApiResponse("Password size between 6 and 16 character!", false);
        if (!(userDto.getPassword().equals(userDto.getPrePassword())))
            return new ApiResponse("Password and Pre-password is not equals!", false);
        return new ApiResponse("", true);
    }


    public UserDetails getUserById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("getUser"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmailOrPhoneNumber(username, username).orElseThrow(() -> new UsernameNotFoundException("get user name"));
    }
}